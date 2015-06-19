import hashlib
import base64

class CompactHash(object):
    def __init__(self, proto):
        self._proto = proto

    def b64digest(self):
        return base64.b64encode(self.digest(), "-_")[:-2]

    def b32digest(self):
        res = base64.b32encode(self.digest())
        return res[:res.index('=')]

    def update(self, data):
        if type(data) == unicode:
            data=data.encode("utf-8")
        return self._proto.update(data)

    def __getattr__(self, name):
        return getattr(self._proto, name)

def sha224(*args, **kwargs):
    return CompactHash(hashlib.sha224(*args,**kwargs))

def sha512(*args, **kwargs):
    return CompactHash(hashlib.sha512(*args,**kwargs))

def md5(*args, **kwargs):
    return CompactHash(hashlib.md5(*args,**kwargs))
