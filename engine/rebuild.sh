#!/usr/bin/env bash
# Reproducible engine rebuild.
#
# The engine APK bundles GeckoView plus a large transitive dependency tree
# (androidx, kotlin, coroutines, snakeyaml, relocated ExoPlayer, ...). Rather
# than re-resolving that tree, we keep every library class from the existing
# engine APK and replace ONLY our own com.tclbrowser classes, which we compile
# fresh against the GeckoView 144 API jar.
#
# Requirements (auto-provisioned when missing):
#   * Android SDK build-tools 35.0.0 + platform android-37
#   * JDK 17 (GeckoView classes.jar is Java 17 bytecode) and JDK 11 (smali)
#   * Network access to maven.mozilla.org and repo1.maven.org
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$HERE/.." && pwd)
RESEARCH=$(cd "$REPO/../research" && pwd)

GV_VERSION="144.0.20251027123126"
SMALI_VERSION="2.5.2"
SDK="${ANDROID_HOME:-/home/user/android-sdk}"
BT="$SDK/build-tools/35.0.0"
AJ="$SDK/platforms/android-37.0/android.jar"
J17="${JDK17:-/usr/lib/jvm/java-17-openjdk-amd64}"
J11="${JDK11:-/usr/lib/jvm/java-11-openjdk-amd64}"

GV_DIR="$RESEARCH/gv"
TOOLS_DIR="$RESEARCH/tools"
WORK="$RESEARCH/ebuild"
GJ="$GV_DIR/classes.jar"
KEYSTORE="$HERE/engine.keystore"
OUT_APK="$HERE/bin/engine.apk"

log() { echo "[engine-build] $*"; }

ensure_jdk() {
    [ -x "$J17/bin/javac" ] || { log "installing JDK17"; sudo apt-get update -qq; sudo apt-get install -y openjdk-17-jdk-headless -qq; }
    [ -x "$J11/bin/java" ] || { log "installing JDK11"; sudo apt-get install -y openjdk-11-jdk-headless -qq; }
}

ensure_geckoview() {
    if [ -f "$GJ" ]; then return; fi
    mkdir -p "$GV_DIR"
    local aar="$GV_DIR/gv.aar"
    log "downloading GeckoView $GV_VERSION AAR"
    curl -sL -o "$aar" \
        "https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/$GV_VERSION/geckoview-$GV_VERSION.aar" \
        --max-time 300
    (cd "$GV_DIR" && unzip -o -q "$aar" classes.jar)
}

ensure_smali() {
    local need=""
    for j in baksmali smali dexlib2 util; do
        [ -f "$TOOLS_DIR/$j-$SMALI_VERSION.jar" ] || need=1
    done
    [ -z "$need" ] && return
    mkdir -p "$TOOLS_DIR"
    local base="https://repo1.maven.org/maven2"
    log "downloading baksmali/smali $SMALI_VERSION"
    local jars=(
        "org/smali/baksmali/$SMALI_VERSION/baksmali-$SMALI_VERSION.jar"
        "org/smali/smali/$SMALI_VERSION/smali-$SMALI_VERSION.jar"
        "org/smali/dexlib2/$SMALI_VERSION/dexlib2-$SMALI_VERSION.jar"
        "org/smali/util/$SMALI_VERSION/util-$SMALI_VERSION.jar"
        "org/antlr/antlr/3.5.2/antlr-3.5.2.jar"
        "org/antlr/antlr-runtime/3.5.2/antlr-runtime-3.5.2.jar"
        "com/beust/jcommander/1.64/jcommander-1.64.jar"
        "org/antlr/stringtemplate/3.2.1/stringtemplate-3.2.1.jar"
        "com/google/guava/guava/27.1-android/guava-27.1-android.jar"
    )
    for u in "${jars[@]}"; do
        local n=$(basename "$u")
        [ -f "$TOOLS_DIR/$n" ] || curl -sL -o "$TOOLS_DIR/$n" "$base/$u" --max-time 90
    done
}

smali_cp() {
    local cp=""
    for j in "$TOOLS_DIR"/*.jar; do cp="$cp:$j"; done
    echo "${cp#:}"
}

log "provisioning toolchain"
ensure_jdk
ensure_geckoview
ensure_smali

rm -rf "$WORK"
mkdir -p "$WORK/gen" "$WORK/obj" "$WORK/tmp" "$WORK/smali_old" "$WORK/smali_new"

log "generating R.java"
"$BT/aapt" package -f -M "$HERE/AndroidManifest.xml" -S "$HERE/res" \
    -I "$AJ" -J "$WORK/gen" 2>/dev/null || true

log "compiling app sources"
find "$HERE/src" -name '*.java' > "$WORK/sources.txt"
find "$WORK/gen" -name '*.java' >> "$WORK/sources.txt"
"$J17/bin/javac" -encoding UTF-8 -classpath "$GJ:$AJ" \
    -d "$WORK/obj" @"$WORK/sources.txt"

log "dexing app classes"
"$BT/d8" --release --min-api 21 --lib "$AJ" --classpath "$GJ" \
    --output "$WORK/tmp" $(find "$WORK/obj/com/tclbrowser" -name '*.class') 2>/dev/null

local_cp=$(smali_cp)

log "disassembling current engine (libraries)"
"$J11/bin/java" -cp "$local_cp" org.jf.baksmali.Main disassemble \
    "$OUT_APK" -o "$WORK/smali_old"
rm -rf "$WORK/smali_old/com/tclbrowser"

log "disassembling fresh app classes and merging"
"$J11/bin/java" -cp "$local_cp" org.jf.baksmali.Main disassemble \
    "$WORK/tmp/classes.dex" -o "$WORK/smali_new"
cp -r "$WORK/smali_new/com/tclbrowser" "$WORK/smali_old/com/tclbrowser"

log "assembling merged dex"
"$J11/bin/java" -cp "$local_cp" org.jf.smali.Main assemble -a 22 \
    "$WORK/smali_old" -o "$WORK/classes.dex"

log "repacking APK"
python3 - "$OUT_APK" "$WORK/classes.dex" "$WORK/engine.unsigned.apk" <<'PYEOF'
import sys, zipfile
src, new_dex_path, out = sys.argv[1], sys.argv[2], sys.argv[3]
new_dex = open(new_dex_path, 'rb').read()
zin = zipfile.ZipFile(src, 'r')
zout = zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED)
for item in zin.infolist():
    name = item.filename
    if name.startswith('META-INF'):
        continue
    if name == 'classes.dex':
        zi = zipfile.ZipInfo('classes.dex', date_time=item.date_time)
        zi.compress_type = zipfile.ZIP_DEFLATED
        zi.external_attr = item.external_attr
        zout.writestr(zi, new_dex, compresslevel=9)
        continue
    zi = zipfile.ZipInfo(name, date_time=item.date_time)
    zi.compress_type = item.compress_type
    zi.external_attr = item.external_attr
    zi.internal_attr = item.internal_attr
    zi.create_system = item.create_system
    zout.writestr(zi, zin.read(name))
zout.close()
PYEOF

log "aligning and signing"
"$BT/zipalign" -p -f 4 "$WORK/engine.unsigned.apk" "$WORK/engine.aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android \
    --ks-key-alias ge --key-pass pass:android \
    --out "$OUT_APK" "$WORK/engine.aligned.apk"

"$BT/apksigner" verify "$OUT_APK"
log "done -> $OUT_APK ($(stat -c%s "$OUT_APK") bytes)"
