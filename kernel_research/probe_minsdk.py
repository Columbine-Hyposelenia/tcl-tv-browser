#!/usr/bin/env python3
import sys, struct, zlib, urllib.request, re

BASE = "https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview-armeabi-v7a"

def head_bytes(url, n=160000):
    req = urllib.request.Request(url, headers={"Range": f"bytes=0-{n-1}"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()

def parse_first_file(data, target):
    off = 0
    while off + 30 <= len(data):
        sig = data[off:off+4]
        if sig != b'PK\x03\x04':
            break
        (_, ver, flags, method, mtime, mdate, crc, csize, usize, nlen, elen) = struct.unpack('<IHHHHHIIIHH', data[off:off+30])
        name_start = off + 30
        name = data[name_start:name_start+nlen].decode('utf-8', 'replace')
        data_start = name_start + nlen + elen
        if name == target:
            cdata = data[data_start:data_start+csize]
            return zlib.decompress(cdata, -15) if method == 8 else cdata
        off = data_start + csize
    return None

def probe(version):
    url = f"{BASE}/{version}/geckoview-armeabi-v7a-{version}.aar"
    raw = parse_first_file(head_bytes(url), 'AndroidManifest.xml')
    if raw is None:
        return None
    txt = raw.decode('utf-8', 'ignore')
    m = re.search(r'minSdkVersion\s*=\s*"(\d+)"', txt)
    return int(m.group(1)) if m else -1

if __name__ == '__main__':
    for v in sys.argv[1:]:
        print(v, "minSdk =", probe(v))
