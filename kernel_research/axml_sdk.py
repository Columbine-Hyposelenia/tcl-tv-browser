import struct, sys

def parse_min_sdk(path):
    data = open(path, 'rb').read()
    # find string pool and start tags; minimal AXML parser
    pos = 0
    strings = []
    def u16(p): return struct.unpack_from('<H', data, p)[0]
    def u32(p): return struct.unpack_from('<I', data, p)[0]
    # walk chunks
    p = 0
    n = len(data)
    while p + 8 <= n:
        ctype = u16(p)
        hsize = u16(p+2)
        csize = u32(p+4)
        if csize <= 0: break
        if ctype == 0x0001:  # string pool
            scount = u32(p+8)
            sstart = u32(p+8+16)
            flags = u32(p+8+20)
            utf8 = (flags & 0x100) != 0
            offsets = []
            for i in range(scount):
                offsets.append(u32(p+hsize + i*4))
            base = p + sstart
            for off in offsets:
                q = base + off
                if utf8:
                    # u8 len, then u8 len (may be 2-byte)
                    l = data[q]
                    if l & 0x80:
                        l = ((l & 0x7f) << 8) | data[q+1]
                        q += 2
                    else:
                        q += 1
                    l2 = data[q]
                    if l2 & 0x80:
                        l2 = ((l2 & 0x7f) << 8) | data[q+1]
                        q += 2
                    else:
                        q += 1
                    strings.append(data[q:q+l2].decode('utf-8','replace'))
                else:
                    l = u16(q)
                    if l & 0x8000:
                        l = ((l & 0x7fff) << 16) | u16(q+2)
                        q += 4
                    else:
                        q += 2
                    strings.append(data[q:q+l*2].decode('utf-16-le','replace'))
        elif ctype == 0x0102:  # start tag
            ext = p + hsize
            name_idx = u32(ext+4)
            attr_count = u32(ext+4+16) >> 16  # attribute count high word? layout: comment(4), ns(4), name(4), attrStart(2), attrSize(2), attrCount(2), ...
            attr_start = u16(ext+4+8)
            attr_size = u16(ext+4+10)
            attr_count = u16(ext+4+12)
            ename = strings[name_idx] if name_idx < len(strings) else ''
            if ename == 'uses-sdk':
                res = {}
                ap = ext + attr_start
                for i in range(attr_count):
                    a = ap + i*attr_size
                    aname = u32(a+4)
                    dtype = data[a+8+3]
                    dval = u32(a+8+4)
                    aname_s = strings[aname] if aname < len(strings) else hex(aname)
                    res[aname_s] = (dtype, dval)
                return res
        p += csize
    return None

for path in sys.argv[1:]:
    try:
        r = parse_min_sdk(path)
        print(path, '->', r)
    except Exception as e:
        print(path, 'ERR', e)
