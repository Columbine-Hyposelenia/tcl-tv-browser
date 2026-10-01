#!/usr/bin/env python3
import os, re

ROOT = "/home/user/Doubao/chats/38444332457187330"
GEN = os.path.join(ROOT, "engine/gen")
EXTRA = os.path.join(ROOT, "engine/build/extra_packages.txt")
MAIN_PKG = "com.tclbrowser.gecko"

main_r = os.path.join(GEN, "R.java")
text = open(main_r, encoding="utf-8").read()

# strip the package declaration, keep the rest as a reusable template
body = re.sub(r"package\s+[\w.]+\s*;\n", "", text, count=1)

# aapt aborts on the framework-only android:lStar styleable and leaves the
# outer R class without its closing brace. Close any unbalanced braces by
# counting only real code (ignoring comments and string literals).
def strip_noise(s):
    s = re.sub(r"/\*.*?\*/", "", s, flags=re.DOTALL)
    s = re.sub(r"//[^\n]*", "", s)
    s = re.sub(r'"(\\.|[^"\\])*"', '""', s)
    s = re.sub(r"'(\\.|[^'\\])*'", "''", s)
    return s

missing = strip_noise(body).count("{") - strip_noise(body).count("}")
body = body + ("}" * max(0, missing))

packages = [MAIN_PKG]
with open(EXTRA) as f:
    packages += [p for p in f.read().strip().split(",") if p]

for pkg in packages:
    out_dir = os.path.join(GEN, pkg.replace(".", "/"))
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, "R.java")
    open(out, "w", encoding="utf-8").write("package %s;\n%s" % (pkg, body))

os.remove(main_r)
print("generated R.java for %d packages:" % len(packages))
for p in packages:
    print("  " + p)
