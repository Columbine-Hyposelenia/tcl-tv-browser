#!/usr/bin/env python3
# Generate an ID-correct R.java directly from a built APK's resources.arsc.
#
# The engine APK merges app resources with GeckoView resources, so the IDs
# differ from an R.java generated against app resources alone. Reading the
# real IDs from the APK makes the recompiled R class reference exactly the
# resources that are packaged, regardless of merge order.
import sys, re, collections

PKG = "com.tclbrowser.gecko"
dump_path, out_path = sys.argv[1], sys.argv[2]

pat = re.compile(
    r"spec resource (0x[0-9a-fA-F]+) "
    + re.escape(PKG) + r":([a-zA-Z0-9_]+)/([A-Za-z0-9_]+)")

groups = collections.defaultdict(dict)
order = []
with open(dump_path, encoding="utf-8", errors="replace") as f:
    for line in f:
        m = pat.search(line)
        if not m:
            continue
        rid, typ, name = m.group(1), m.group(2), m.group(3)
        if typ == "styleable":
            continue
        if typ not in groups:
            order.append(typ)
        groups[typ][name] = rid

lines = []
lines.append("package %s;" % PKG)
lines.append("")
lines.append("public final class R {")
for typ in order:
    lines.append("    public static final class %s {" % typ)
    for name in sorted(groups[typ]):
        lines.append("        public static final int %s=%s;"
                     % (name, groups[typ][name]))
    lines.append("    }")
lines.append("}")
lines.append("")

with open(out_path, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print("wrote", out_path, "types:", order)
