import os, sys, re, urllib.request, urllib.error, hashlib

REPOS = [
    "https://dl.google.com/dl/android/maven2",
    "https://maven.mozilla.org/maven2",
    "https://repo1.maven.org/maven2",
]
CACHE = "maven_cache"
os.makedirs(CACHE, exist_ok=True)

def coord_path(g, a, v, ext):
    return "%s/%s/%s/%s-%s.%s" % (g.replace('.', '/'), a, v, a, v, ext)

def fetch(url, binary=True):
    key = hashlib.sha1(url.encode()).hexdigest()
    p = os.path.join(CACHE, key)
    if os.path.exists(p):
        return open(p, 'rb').read()
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            data = r.read()
        open(p, 'wb').write(data)
        return data
    except Exception:
        return None

def get_pom(g, a, v):
    rel = coord_path(g, a, v, 'pom')
    for repo in REPOS:
        data = fetch(repo + '/' + rel)
        if data and b'<project' in data[:200]:
            return data.decode('utf-8', 'replace')
    return None

def get_artifact(g, a, v):
    # returns (path, kind); try aar then jar
    for ext in ('aar', 'jar'):
        rel = coord_path(g, a, v, ext)
        for repo in REPOS:
            data = fetch(repo + '/' + rel)
            if data and data[:2] == b'PK':
                p = os.path.join(CACHE, "%s-%s.%s" % (a, v, ext))
                open(p, 'wb').write(data)
                return (p, ext)
    return (None, None)

def strip_ns(xml):
    return re.sub(r'xmlns(:\w+)?="[^"]+"', '', xml)

def parse_deps(pom):
    # deps in dependencyManagement set versions; ignore for simplicity
    deps = []
    # find all <dependency> blocks outside dependencyManagement
    dm = re.search(r'<dependencyManagement>(.*?)</dependencyManagement>', pom, re.S)
    body = re.sub(r'<dependencyManagement>.*?</dependencyManagement>', '', pom, flags=re.S)
    for m in re.finditer(r'<dependency>(.*?)</dependency>', body, re.S):
        block = m.group(1)
        g = re.search(r'<groupId>(.*?)</groupId>', block)
        a = re.search(r'<artifactId>(.*?)</artifactId>', block)
        v = re.search(r'<version>(.*?)</version>', block)
        scope = re.search(r'<scope>(.*?)</scope>', block)
        optional = re.search(r'<optional>(.*?)</optional>', block)
        if not g or not a: continue
        sc = scope.group(1).strip() if scope else 'compile'
        if sc in ('test', 'provided', 'system'): continue
        if optional and optional.group(1).strip() == 'true': continue
        deps.append((g.group(1).strip(), a.group(1).strip(),
                     v.group(1).strip() if v else None, sc))
    return deps

EXCLUDE_GROUPS = {
    "com.google.android.gms",
}

def resolve(root):
    resolved = {}  # g:a -> (g,a,v,scope,path,kind)
    queue = [(root, 0)]
    while queue:
        ((g, a, v), depth) = queue.pop(0)
        key = g + ':' + a
        if key in resolved:
            continue
        if g in EXCLUDE_GROUPS:
            continue
        path, kind = get_artifact(g, a, v)
        if not path:
            print("MISSING", key, v)
            continue
        resolved[key] = (g, a, v, path, kind)
        pom = get_pom(g, a, v)
        if pom:
            for (dg, da, dv, ds) in parse_deps(pom):
                if dg in EXCLUDE_GROUPS:
                    continue
                if dv is None or dv.startswith('${'):
                    # version from parent; skip unresolved for now
                    if dv and 'project' in dv:
                        dv = v
                    else:
                        continue
                dk = dg + ':' + da
                if dk not in resolved:
                    queue.append(((dg, da, dv), depth+1))
    return resolved

if __name__ == '__main__':
    roots = [
        ("org.mozilla.geckoview", "geckoview", "144.0.20251027123126"),
        ("org.mozilla.geckoview", "geckoview-exoplayer2", "144.0.20251027123126"),
    ]
    all_res = {}
    for r in roots:
        res = resolve(r)
        all_res.update(res)
    print("=== resolved %d artifacts ===" % len(all_res))
    for k, v in sorted(all_res.items()):
        print("%-55s %-10s %s" % (k, v[4], v[2]))
    # write manifest
    with open("resolved.txt", "w") as f:
        for k, v in sorted(all_res.items()):
            f.write("%s|%s|%s|%s|%s\n" % (v[0], v[1], v[2], v[4], v[3]))
