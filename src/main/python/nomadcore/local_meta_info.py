import logging
from nomadcore import compact_sha
import json
import os
from nomadcore.json_support import jsonCompactS, jsonCompactD, jsonIndentD
"""objects to handle a local InfoKinds with unique name (think self written json)"""

class InfoKindEl(object):
    """Info kind (tipically from a file, without shas but with locally unique names)"""
    __slots__ = ["name","description","kindStr","units","super_names","dtypeStr", "repeats", "shape", "extra_args"]
    IGNORE_EXTRA_ARGS = 1
    ADD_EXTRA_ARGS = 2
    RAISE_IF_EXTRA_ARGS = 3

    def __init__(self, name, description, kindStr = "DocumentContentType", units = None, super_names = None,
            dtypeStr = None, shape = None, extraArgsHandling = ADD_EXTRA_ARGS, repeats = None, **extra_args):
        if super_names is None:
            super_names = []
        self.name = name
        self.description = description
        self.kindStr = kindStr
        self.super_names = super_names
        self.units = units
        self.dtypeStr = dtypeStr
        if dtypeStr in ["None", "null"]:
            self.dtypeStr = None
        self.shape = shape
        self.repeats = repeats
        if extraArgsHandling == self.ADD_EXTRA_ARGS:
            self.extra_args = extra_args
        elif extraArgsHandling == self.IGNORE_EXTRA_ARGS:
            self.extra_args = {}
        else:
            raise Exception("extra arguments to InfoKindEl:" + str(extra_args))

    def __eq__(o1, o2):
        try:
            if not (o1.name == o2.name and o1.description == o2.description and o1.kindStr == o2.kindStr and
                o1.units == o2.units and o1.shape == o2.shape):
                return False
            if o1.dtypeStr != o2.dtypeStr:
                return False
            if o1.repeats != o2.repeats:
                return False
            if o1.extra_args != o2.extra_args:
                return False
            if o1.super_names == o2.super_names:
                return True
            if len(o1.super_names) != len(o2.super_names):
                return False
            if o1.super_names[0] != o2.super_names[0]:
                return False
            a1 = o1.super_names[1:]
            a2 = o2.super_names[1:]
            a1.sort()
            a2.sort()
            for i in range(len(a1)):
                if a1[i] != a2[i]:
                    return False
            return True
        except:
            raise
            return False

    def __cmp__(k1, k2):
        c = cmp(k1.name, k2.name)
        if c != 0: return c
        c = cmp(k1.kindStr, k2.kindStr)
        if c != 0: return c
        c = cmp(k1.description, k2.description)
        if c != 0: return c
        if len(k1.super_names) > 0:
            if len(k2.super_names) > 0:
                c = cmp(k1.super_names[0], k2.super_names[0])
                if c != 0: return c
                s1 = k1.super_names[1:]
                s2 = k2.super_names[1:]
                c = cmp(s1, s2)
                if c != 0: return c
            else:
                return 1
        elif len(k2.super_names) > 0:
            return -1
        if c != 0: return c
        c = cmp(k1.units, k2.units)
        if c != 0: return c
        c = cmp(k1.dtypeStr, k2.dtypeStr)
        if c != 0: return c
        c = cmp(k1.repeats, k2.repeats)
        if c != 0: return c
        c = cmp(k1.shape, k2.shape)
        if c != 0: return c
        if k1.extra_args == k2.extra_args:
            return 0
        if k1.extra_args is None:
            return 1
        if k2.extra_args is None:
            return -1
        extraK1 = k1.extra_args.keys()
        extraK1.sort()
        extraK2 = k2.extra_args.keys()
        extraK2.sort()
        i = 0
        while (i < len(extraK1) and i < len(extraK2)):
            kk1 = extraK1[i]
            kk2 = extraK2[i]
            c = cmp(kk1, kk2)
            if c != 0: return c # use -c ?
            c = cmp(k1.extra_args[kk1], k2.extra_args[kk2])
            if c != 0: return c
        c = cmp(len(extraK1), len(extraK2))
        return c

    def __ne__(o1, o2):
        return not o1.__eq__(o2)

    def prepare(self, env):
        if len(self.super_names) > 1:
            a = self.super_names[1:]
            a.sort(lambda x, y: cmp(env.gidOf(x, precalculatedGid = True), env.gidOf(y, precalculatedGid = True)))
            self.super_names[1:] = a

    def evalGid(self, env):
        self.prepare(env)
        sha = env.newSha()
        self.serialize(env,sha.update, precalculatedGid = True, selfGid = False)
        return 'p' + sha.b64digest()

    def serialize(self, env, writeOut, subGids = True, addExtraArgs = True, precalculatedGid = False, selfGid = True):
        d = self.toDict(env, subGids = subGids, addExtraArgs = addExtraArgs, precalculatedGid = precalculatedGid, selfGid = selfGid)
        jsonCompactD(d, writeOut)

    def toDict(self, env = None, addExtraArgs = True, inlineExtraArgs = True , selfGid = False, subGids = False, precalculatedGid = False):
        res = {
            "description": self.description,
            "name": self.name,
            "super_names": self.super_names,
        }
        try:
            if self.kindStr != "DocumentContentType":
                if self.kindStr is None or self.kindStr == "":
                    res["kindStr"] = "MetaType"
                else:
                    res["kindStr"] = self.kindStr
            if env:
                if selfGid:
                    res["gid"] = env.gidOf(self.name, precalculatedGid = precalculatedGid)
                if subGids:
                    res["super_gids"] = [ env.gidOf(sName, precalculatedGid = precalculatedGid) for sName in self.super_names ]
            elif subGids or selfGid:
                raise Exception("env required in toDict for subGids or selfGid")
            if self.units is not None:
                res["units"] = self.units
            if self.dtypeStr is not None:
                res["dtypeStr"] = self.dtypeStr
            if self.repeats is not None:
                res["repeats"] = self.repeats
            if self.shape is not None:
                res["shape"] = self.shape
            if addExtraArgs:
                if inlineExtraArgs:
                    res.update(self.extra_args)
                else:
                    res["extra_args"] = self.extra_args
        except:
            logging.exception("error in InfoKindEl.toDict, partial dict is %s", res)
        return res

    def __unicode__(self):
        s = StringIO.StringIO()
        self.serialize(s)
        return s.string

class RelativeDependencySolver:
    def __init__(self):
        self.deps = {}

    def __call__(self, infoKindEnv, source, dep):
        if not dep.has_key("relativePath"):
            raise Exception('Invalid dependency for relativeDependencySolver there must be a relativePath')
        basePath = source.get('path')
        if basePath:
            baseDir = os.path.dirname(os.path.abspath(basePath))
        else:
            baseDir = os.getcwd()
        dPath = os.path.realpath(os.path.join(baseDir, dep['relativePath']))
        if dPath in self.deps:
            return self.deps[dPath]
        depInfo = None
        depIKEnv = InfoKindEnv(path = dPath, dependencyLoader=infoKindEnv.dependencyLoader)
        self.deps[dPath] = depIKEnv
        with open(dPath) as f:
            depInfo = json.load(f)
        if depInfo:
            depIKEnv.fromJsonList(depInfo, source = { 'path': dPath }, dependencyLoad = True)
        return depIKEnv


class InfoKindEnv(object):
    """An environment keeping locally unique InfoKinds and their gids"""
    def __init__(self, infoKinds = None, newSha = compact_sha.sha224, gids = None,
            dependencyLoader = None, path = None, uri = None, deps = None):
        self.newSha = newSha
        self.clear()
        self.dependencyLoader = dependencyLoader
        if dependencyLoader is None:
            self.dependencyLoader = RelativeDependencySolver()
        self.path = path
        self.uri  = uri
        if not infoKinds is None:
            for ik in infoKinds:
                self.addInfoKindEl(ik)
        if not gids is None:
            self.gids = gids
        if deps:
            self.deps = deps

    def __str__(self):
        if self.path:
            return "InfoKindEnv loaded from {path}".format(self.path)
    def clear(self):
        self.gids = {}
        self.infoKinds = {}
        self.deps = []

    def depNames(self):
        res = set()
        for dep in self.deps:
            for name in dep.infoKinds.keys():
                res.add(name)
        return res

    def noDepNames(self):
        return set(self.infoKinds.keys()).difference(self.depNames())

    def embedDeps(self):
        hidden = []
        duplicate = set()
        for dep in self.deps:
            for name, ikEl in dep.infoKinds.items():
                oldVal=self.infoKinds.get(name, None)
                if oldVal is None:
                    self.infoKinds[name] = ikEl
                elif ikEl != oldVal:
                    hidden.append(ikEl)
                else:
                    duplicate.add(name)
        return { "hidden": hidden, "duplicate": duplicate }

    def addInfoKindEl(self, infoKind):
        if infoKind.name in self.infoKinds and infoKind != self.infoKinds[infoKind.name]:
            raise Exception('InfoKindEnv has collision for name {0}: {1} vs {2}'
                    .format(infoKind.name, infoKind, self.infoKinds[infoKind.name]))
        self.infoKinds[infoKind.name] = infoKind

    def addDependenciesFrom(self, infoKindEnv):
        toAdd = set(self.infoKinds.keys())
        missing = set()
        while len(toAdd):
            ikName = toAdd.pop()
            ik = self.infoKinds.get(ikName,None)
            if ik is None:
                depInfoKindEl = infoKindEnv.infoKinds.get(ikName, None)
                if depInfoKindEl:
                    self.infoKinds[ikName] = depInfoKindEl
                    toAdd.add(ikName)
                else:
                    missing.add(ikName)
            else:
                for dep in ik.super_names:
                    if not dep in self.infoKinds:
                        toAdd.add(dep)
        return missing

    def gidOf(self, name, precalculatedGid=False):
        res = self.gids.get(name,None)
        if res is None:
            if precalculatedGid:
                raise Exception("non precalculated gid for %s" % name)
            res = self.calcGid(name)
        return res

    def calcGid(self, name):
        inProgress = []
        toDo = [name]
        hasPending = False
        for i in range(2):
            while len(toDo) > 0:
                if not hasPending and inProgress:
                    now = inProgress.pop()
                else:
                    now = toDo.pop()
                if now in self.gids and now in inProgress:
                    inProgress.remove(now)
                hasPending = False
                nowVal = self.infoKinds.get(now, None)
                if nowVal is None:
                    raise Exception("while calculating gid of %r found unknown key %r" % (name, now))
                for subName in nowVal.super_names:
                    if subName in self.gids:
                        continue
                    hasPending = True
                    if subName in toDo:
                        toDo.remove(subName)
                    if subName in inProgress:
                        raise Exception('found loop to %s evaluating %s, currently in progress: %s' % (subName, now, inProgress))
                    toDo.append(subName)
                if not hasPending:
                    self.gids[now] = nowVal.evalGid(self)
                    if now in inProgress:
                        inProgress.remove(now)
                else:
                    if now in inProgress:
                        raise Exception('found loop to %s, currently in progress: %s' % (now, inProgress))
                    inProgress.append(now)
            toDo = list(inProgress)
        return self.gids[name]

    def keyDependsOnKey(self, k1Name, k2Name):
        """partial ordering given by the dependencies
        1: k1Name depends on k2Name
        0: k1Name == k2Name
        -1: k2Name depends on k1Name
        None: no dependency"""
        if k1Name == k2Name: return 0
        k1 = self.infoKinds[k1Name]
        k2 = self.infoKinds[k2Name]
        if k1.super_names != k2.super_names:
            allSuperK1 = set()
            toDoK1 = list(k1.super_names)
            allSuperK2 = set()
            toDoK2 = list(k2.super_names)
            while (len(toDoK1) > 0 or len(toDoK2) > 0):
                if len(toDoK1) > 0:
                    el1Name = toDoK1.pop()
                    if k2Name == el1Name:
                        return 1
                    el1 = self.infoKinds[el1Name]
                    if el1.kindStr in self and not el1.kindStr in allSuperK1:
                        toDoK1.append(el1.kindStr)
                    for subEl in el1.super_names:
                        if not subEl in allSuperK1:
                            toDoK1.append(subEl)
                    allSuperK1.update(el1.super_names)
                if len(toDoK2) > 0:
                    el2Name = toDoK2.pop()
                    if k1Name == el2Name:
                        return -1
                    el2 = self.infoKinds[el2Name]
                    if el2.kindStr in self and not el2.kindStr in allSuperK2:
                        toDoK2.append(el2.kindStr)
                    for subEl in el2.super_names:
                        if not subEl in allSuperK2:
                            toDoK2.append(subEl)
                    allSuperK2.update(el2.super_names)
        return None

    def __contains__(self, name):
        "if an item with the given name is contained in this environment"
        return name in self.infoKinds

    def __len__(self):
        """returns the number of InfoKindEl stored in this environment"""
        return len(self.infoKinds)

    def __getitem__(self, name):
        """returns a dictionary representing the entry with the given name, or None if it does not exist"""
        ikEl = self.infoKinds.get(name, None)
        if ikEl:
            return ikEl.toDict(self)
        return None

    def infoKindEls(self):
        return self.infoKinds.values()

    def infoKindEl(self, name):
        """returns the InfoKindEl with the given name, or None if it does not exist"""
        return self.infoKinds.get(name, None)

    def calcGids(self):
        for k in self.infoKinds.keys():
            if not k in self.gids:
                self.gids[k]=self.calcGid(k)

    def serialize(self, writeOut, subGids = True, selfGid = True):
        infoKinds = self.sortedIKs()
        writeOut("[ ")
        addColon = False
        if not self.path:
            baseDir = os.getcwd()
        else:
            baseDir = os.path.normpath(os.path.dirname(os.path.abspath(self.path)))
        depKeys = set()
        if self.deps:
            writeOut("{\n")
            writeOut('    "dependencies": [ ')
            depColon = False
            for d in self.deps:
                path = d.path
                uri = d.uri
                depKeys.update(d.infoKinds.keys())
                if path:
                    path = os.path.normpath(os.path.abspath(path))
                    if path.startswith(baseDir) or not uri:
                        if depColon:
                            writeOut(", ")
                        else:
                            depColon = True
                        jsonIndentD({"relativePath": os.path.relpath(path, baseDir)}, writeOut, extraIndent = 6)
                        continue
                if uri:
                    if depColon:
                        writeOut(", ")
                    else:
                        depColon = True
                    jsonIndentD({"uri": uri}, writeOut, extraIndent = 6)
                    continue
                raise Exception("Dependency on serializable %s" % d)
            writeOut(']\n  }')
            addColon = True
        for ik in infoKinds:
            if ik.name in depKeys:
                continue
            if addColon:
                writeOut(", ")
            else:
                addColon = True
            jsonIndentD(ik.toDict(env = self, subGids = subGids, selfGid = selfGid), writeOut, extraIndent = 2, check_circular = True)
        writeOut("]")

    def sortedIKs(self):
        infoKinds = list(self.infoKinds.values())
        infoKinds.sort()
        return self.sortAndComplete(infoKinds, ignoreMissing = True)


    def toJsonList(self, withGids):
        infoKinds = list(self.infoKinds.keys())
        infoKinds.sort(lambda x, y: self.compareKeys(x.name, y.name))
        return map(lambda x: self.infoKinds[x].toDict(self,
                self if withGids else None), infoKinds)

    def verifyGids(self, preserveAbsent=False):
        changes = {}
        oldGids = self.gids
        self.gids = {}
        self.calcGids()
        for k,v in oldGids.items():
            newVal = self.gids.get(k, None)
            if newVal is None:
                if preserveAbsent:
                    self.gids[k] = v
                else:
                    changes[k] = (v, None)
            elif v != newVal:
                changes[k] = (v, newVal)
        return changes

    def fromJsonList(self, jsonList, source, extraArgsHandling = InfoKindEl.ADD_EXTRA_ARGS, dependencyLoad=False):
        overwritten = []
        gidToCheck = {}
        index = -1
        for ii in jsonList:
            index += 1
            if "dependencies" in ii:
                for d in ii["dependencies"]:
                    if self.dependencyLoader is None:
                        raise Exception("no dependencyLoader while loading local_in")
                    dep = self.dependencyLoader(self, source, d)
                    if dep:
                        self.deps.append(dep)
                continue
            val = dict(ii)
            if not "name" in ii:
                if "dependencies" in ii:
                    continue
                raise Exception("InfoKind at %d is without name: %s" % (index, ii) )
            oldVal=self.infoKinds.get(ii['name'],None)
            gid=None
            if "gid" in ii:
                gid = ii['gid']
                del val['gid']
            if "super_gids" in ii:
                if not "super_names" in ii:
                    raise Exception("super_gids without super_names in fromJsonList")
                super_names = ii["super_names"]
                super_gids = ii["super_gids"]
                if len(super_names) != len(super_gids):
                    raise Exception("super_gids incompatible with super_names in fromJsonList: %s vs %s" % (ii["super_gids"], ii["super_names"]))
                toCheck = {}
                for i in range(len(super_names)):
                    assert not super_names[i] in toCheck.keys(), "duplicate super_name %r in %r" % (super_names[i], ii["name"])
                    toCheck[super_names[i]] = super_gids[i]
                gidToCheck[ii["name"]] = toCheck
                del val['super_gids']
            val['extraArgsHandling'] = extraArgsHandling
            ikEl = InfoKindEl(**val)
            if not oldVal is None and ikEl != oldVal:
                overwritten.append(oldVal, ikEl)
            if gid:
                self.gids[ii['name']] = gid
            self.infoKinds[ikEl.name] = ikEl
        res = { "overwritten": overwritten }
        if not dependencyLoad:
            res.update(self.embedDeps())
            for childName, gids in gidToCheck.items():
                for name, gid in gids.items():
                    if self.gidOf(name) != gid:
                        raise Exception("incompatible super_gid for super_name %s of %s (%s vs %s)" % (name, ii["name"], gid, self.gidOf(name)))
        if res.get("overwritten", False) or res.get("duplicate", False) or res.get("hidden", False):
            res["hasWarnings"] = True
        else:
            res["hasWarnings"] = res.get("hasWarnings", False)
        return res

    def sortAndComplete(self, propsToSort, ignoreMissing = False):
        """builds a list of properties in propsToSort, so that all the dependecies of each
        property are present before them"""
        toDo = list(propsToSort)
        done = set()
        deps = []
        res = []
        while len(toDo)>0:
            pAtt = toDo.pop()
            nameAtt = pAtt.name
            if nameAtt in done:
                continue
            deps = [nameAtt]
            while len(deps)>0:
                nameAtt = deps[-1]
                pAtt = self.infoKinds.get(nameAtt, None)
                if pAtt is None:
                    if ignoreMissing:
                        deps.pop()
                        done.add(nameAtt)
                        continue
                    else:
                        raise Exception("missing dependent InfoKindEl {0} following chain {1}".format(nameAtt, pAtt))
                hasDepsToDo = False
                kindStr = pAtt.kindStr
                kindType = self.infoKindEl(kindStr)
                for superName in pAtt.super_names:
                    if not superName in done:
                        if superName in deps:
                            raise Exception("circular dependency {0}, {1}".format(deps,superName))
                        deps.append(superName)
                        hasDepsToDo = True
                if kindType and not kindStr in done:
                    if kindStr in deps:
                        raise Exception("circular dependency in kindStr {0}, {1}".format(deps,kindStr))
                    deps.append(kindStr)
                    hasDepsToDo = True
                if not hasDepsToDo:
                    deps.pop()
                    res.append(pAtt)
                    done.add(nameAtt)
        return res


def loadJsonFile(filePath, dependencyLoader = None, extraArgsHandling = InfoKindEl.ADD_EXTRA_ARGS, uri = None):
    env = InfoKindEnv(dependencyLoader = dependencyLoader, path = filePath, uri = uri)
    try:
        with open(filePath) as f:
            o = json.load(f)
            warnings = env.fromJsonList(o, source = {'path': filePath}, extraArgsHandling = extraArgsHandling)
    except:
        logging.exception("Error while loading file %s" % filePath)
        raise
    return env, warnings

def loadJsonStream(fileStream, dependencyLoader = None, extraArgsHandling = InfoKindEl.ADD_EXTRA_ARGS, filePath = None, uri = None):
    if filePath is None:
        try:
            filePath = fileStream.name
        except:
            filePath = None
    env = InfoKindEnv(dependencyLoader = dependencyLoader, path = filePath, uri = uri)
    try:
        o = json.load(fileStream)
        warnings = env.fromJsonList(o, source = {'path': filePath}, extraArgsHandling = extraArgsHandling)
    except:
        logging.exception("Error while loading file %s" % filePath)
        raise
    return env, warnings
