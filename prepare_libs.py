#!/usr/bin/env python3
import os, re, shutil, zipfile, subprocess

ROOT = "/home/user/Doubao/chats/38444332457187330"
RESOLVED = os.path.join(ROOT, "kernel_research/resolved.txt")
CACHE = os.path.join(ROOT, "kernel_research/maven_cache")
BUILD = os.path.join(ROOT, "engine/build")
MERGED_RES = os.path.join(BUILD, "merged_res")
CP_DIR = os.path.join(BUILD, "cp")
LIB_DIR = os.path.join(BUILD, "lib")
ASSET_DIR = os.path.join(BUILD, "assets")

for d in (BUILD, MERGED_RES, CP_DIR, LIB_DIR, ASSET_DIR):
    if os.path.abspath(d) == os.path.abspath(BUILD):
        continue
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d, exist_ok=True)

# start merged_res with the app's own resources
app_res = os.path.join(ROOT, "engine/res")
shutil.copytree(app_res, MERGED_RES, dirs_exist_ok=True)

extra_packages = set()
cp_entries = []
value_index = 0

def safe_copy(src, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if os.path.exists(dst):
        return
    shutil.copy2(src, dst)

CONFIG_TOKENS = {
    "night", "land", "port", "round", "notround", "long", "notlong",
    "television", "watch", "car", "appliance", "vrheadset", "widecg",
    "normal", "small", "large", "xlarge", "mcc", "mnc",
}

def is_locale_values(folder):
    # folder like "values", "values-v21", "values-night", "values-fr-rFR"
    if folder == "values":
        return False
    qualifier = folder[len("values-"):]
    tokens = qualifier.split("-")
    first = tokens[0]
    is_lang = (re.match(r"^[a-z]{2,3}$", first) is not None
               or first.startswith("b+")
               or re.match(r"^r[A-Z]{2}$", first) is not None)
    if not is_lang:
        return False
    for t in tokens:
        if re.match(r"^(v|w|h|sw|w\d|h\d)\d", t) or t in CONFIG_TOKENS:
            return False
    return True

entries = []
with open(RESOLVED) as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        g, a, v, ext, path = line.split("|")
        if not os.path.isabs(path):
            path = os.path.join(ROOT, "kernel_research", path)
        entries.append((g, a, v, ext, path))

for (g, a, v, ext, path) in entries:
    libname = (g + "." + a).replace(".", "_")
    if ext == "aar":
        extra_packages.add(g)
        with zipfile.ZipFile(path) as z:
            names = z.namelist()
            # classes.jar
            if "classes.jar" in names:
                target = os.path.join(CP_DIR, libname + ".jar")
                with z.open("classes.jar") as src, open(target, "wb") as out:
                    out.write(src.read())
                cp_entries.append(target)
            # resources
            for n in names:
                if n.startswith("res/") and not n.endswith("/"):
                    rel = n[4:]
                    parts = rel.split("/")
                    if parts[0].startswith("values") and is_locale_values(parts[0]):
                        continue
                    if parts[0].startswith("values"):
                        # rename to avoid values.xml clash
                        value_index += 1
                        suffix = parts[0]
                        fn = parts[-1]
                        dest = os.path.join(MERGED_RES, suffix,
                                "v%03d_%s" % (value_index, fn))
                    else:
                        dest = os.path.join(MERGED_RES, rel)
                    if not os.path.exists(dest):
                        os.makedirs(os.path.dirname(dest), exist_ok=True)
                        with z.open(n) as src, open(dest, "wb") as out:
                            out.write(src.read())
            # native libs: only armeabi-v7a
            for n in names:
                if n.startswith("jni/armeabi-v7a/") and n.endswith(".so"):
                    fn = os.path.basename(n)
                    dest = os.path.join(LIB_DIR, fn)
                    if not os.path.exists(dest):
                        with z.open(n) as src, open(dest, "wb") as out:
                            out.write(src.read())
            # assets
            for n in names:
                if n.startswith("assets/") and not n.endswith("/"):
                    rel = n[len("assets/"):]
                    dest = os.path.join(ASSET_DIR, rel)
                    if not os.path.exists(dest):
                        os.makedirs(os.path.dirname(dest), exist_ok=True)
                        with z.open(n) as src, open(dest, "wb"):
                            pass
                        with z.open(n) as src, open(dest, "wb") as out:
                            out.write(src.read())
    elif ext == "jar":
        target = os.path.join(CP_DIR, libname + ".jar")
        shutil.copy2(path, target)
        cp_entries.append(target)

# Normalize values: rewrite <id name="x"/> to <item type="id" name="x"/>
# which the legacy aapt accepts, and de-duplicate identical public attrs.
id_re = re.compile(r'<id\s+name="([^"]+)"\s*/>')
values_dir = os.path.join(MERGED_RES, "values")
for fn in os.listdir(values_dir):
    p = os.path.join(values_dir, fn)
    text = open(p, encoding="utf-8").read()
    new = id_re.sub(r'<item type="id" name="\1"/>', text)
    if new != text:
        open(p, "w", encoding="utf-8").write(new)

# write classpath file and extra packages
with open(os.path.join(BUILD, "cp_list.txt"), "w") as f:
    for p in sorted(cp_entries):
        f.write(p + "\n")

# include the app's android jar separately at build; extra packages only
with open(os.path.join(BUILD, "extra_packages.txt"), "w") as f:
    f.write(",".join(sorted(extra_packages)))

print("classpath entries: %d" % len(cp_entries))
print("extra packages: %s" % ",".join(sorted(extra_packages)))
print("native libs: %d" % len(os.listdir(LIB_DIR)))
for n in sorted(os.listdir(LIB_DIR)):
    p = os.path.join(LIB_DIR, n)
    print("  %-24s %d bytes" % (n, os.path.getsize(p)))
print("assets: %d" % sum(len(fs) for _, _, fs in os.walk(ASSET_DIR)))
