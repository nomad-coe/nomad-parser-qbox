import json
"""Various functions to simplify and standardize dumping objects to json"""

class ExtraIndenter(object):
    """Helper class to add extra indent at the beginning of every line"""
    def __init__(self, fStream, extraIndent):
        self.fStream = fStream
        self.indent = " " * extraIndent if extraIndent else  ""
    def write(self, val):
        i = 0
        while True:
            j = val.find("\n", i)
            if j == -1:
                fStream.write(val[i:])
                return
            j += 1
            fStream.write(val[i:j])
            fStream.write(self.indent)
            i = j

def jsonCompactF(obj, fOut, check_circular = False):
    """Dumps the object obj with a compact json representation using the utf_8 encoding 
    to the file stream fOut"""
    json.dump(obj, fOut, sort_keys = True, indent = None, separators = (',', ':'),
            ensure_ascii = False, check_circular = check_circular)

def jsonIndentF(obj, fOut, check_circular = False, extraIndent = None):
    """Dumps the object obj with an indented json representation using the utf_8 encoding 
    to the file stream fOut"""
    fStream = fOut
    if extraIndent:
        fStream = ExtraIndenter(fOut, extraIndent = extraIndent)
    json.dump(obj, fStream, sort_keys = True, indent = 2, separators = (',', ': '),
            ensure_ascii = False, check_circular = check_circular)

class DumpToStream(object):
    """transform a dump function in a stream"""
    def __init__(self, dumpF, extraIndent = None):
        self.baseDumpF = dumpF
        self.extraIndent = extraIndent
        self.indent = " " * extraIndent if extraIndent else  ""
        self.dumpF = self.dumpIndented if extraIndent else dumpF
    def dumpIndented(self, val):
        if type(val) == unicode:
            val = val.encode("utf_8")
        i = 0
        while True:
            j = val.find("\n", i)
            if j == -1:
                self.baseDumpF(val[i:])
                return
            j += 1
            self.baseDumpF(val[i:j])
            self.baseDumpF(self.indent)
            i = j
    def write(self, val):
        self.dumpF(val)

def jsonCompactD(obj, dumpF, check_circular = False):
    """Dumps the object obj with a compact json representation using the utf_8 encoding 
    to the file stream fOut"""
    json.dump(obj, DumpToStream(dumpF), sort_keys = True, indent = None, separators = (', ', ': '),
            ensure_ascii = False, check_circular = check_circular)

def jsonIndentD(obj, dumpF, check_circular = False, extraIndent = None):
    """Dumps the object obj with an indented json representation using the utf_8 encoding 
    to the function dumpF"""
    json.dump(obj, DumpToStream(dumpF, extraIndent = extraIndent), sort_keys = True, indent = 2, separators = (',', ': '),
            ensure_ascii = False, check_circular = check_circular, encoding="utf_8")

def jsonCompactS(obj, check_circular = False):
    """returns a compact json representation of the object obj as a string"""
    return json.dumps(obj, sort_keys = True, indent = None, separators = (', ', ': '),
            ensure_ascii = False, check_circular = check_circular, encoding="utf_8")

def jsonIndentS(obj, check_circular = False, extraIndent = None):
    """retuns an indented json representation if the object obj as a string"""
    res = json.dumps(obj, sort_keys = True, indent = 2, separators = (',', ': '),
            ensure_ascii = False, check_circular = check_circular, encoding="utf_8")
    if extraIndent:
        indent = " " * extraIndent
        res = res.replace("\n", "\n" + indent)
    return res

def jsonDump(obj, path):
    """Dumps the object obj to an newly created utf_8 file at path"""
    kwds = dict()
    if sys.version_info.major > 2:
        kwds["encoding"] = "utf_8"
    with open(path, "w", **kwds) as f:
        jsonIndentF(obj, f)

class ShaStreamer(object):
    """a file like object that calculates one or more shas"""
    def __init__(self, shas = None):
        self.shas = shas
        if shas is None:
            self.shas = (CompactSha.sha224(),)
    def write(self, val):
        for sha in self.shas:
            sha.update(val)
    def b64digests(self):
        return [sha.b32digest() for sha in self.shas]

def addShasOfJson(obj, shas = None):
    """adds the jsonDump of obj to the shas"""
    streamer = ShaStreamer(shas)
    jsonCompactF(obj, streamer)
    return streamer

def normalizedJsonGid(obj, shas = None):
    """returns the gid of the standard formatted jsonDump of obj"""
    return map(lambda x: 'j' + x, addShasOfJson(shas).b64digests())
