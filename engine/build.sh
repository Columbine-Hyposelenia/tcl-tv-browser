#!/bin/bash
set -e

SDK=/home/user/.doubao/agent_mode/workspace/.sessions/38444332457187330/agents/m_0cwps1nIzyG/android-sdk
JDK=/home/user/.doubao/agent_mode/workspace/.sessions/38444332457187330/agents/m_0cwps1nIzyG/jdk-17.0.20.1+1
export PATH="$JDK/bin:$PATH"
BT=$SDK/build-tools/34.0.0
R8_JAR=/home/user/.doubao/agent_mode/workspace/.sessions/38444332457187330/agents/m_0cwps1nIzyG/r8.jar
ANDROID_JAR=$SDK/platforms/android-30/android.jar

PROJECT=/home/user/Doubao/chats/38444332457187330/engine
cd "$PROJECT"

# Prepare libraries first
python3 ../prepare_libs.py

BUILD="$PROJECT/build"
MERGED_RES="$BUILD/merged_res"
CP_DIR="$BUILD/cp"
LIB_DIR="$BUILD/lib"
ASSET_DIR="$BUILD/assets"

rm -rf gen obj bin dex staging
mkdir -p gen obj bin dex staging

EXTRA=$(cat "$BUILD/extra_packages.txt")
CP=$(cat "$BUILD/cp_list.txt" | tr '\n' ':')

# 1a. Build the base apk with compiled resources (no R.java output here;
# this path succeeds and packs every resource).
"$BT/aapt" package -f \
  -M AndroidManifest.xml \
  -S "$MERGED_RES" \
  -I "$ANDROID_JAR" \
  -F bin/app.unaligned.apk

# 1b. Generate the main R.java. The legacy aapt reports a benign error for
# the framework-only android:lStar styleable while still emitting a complete
# R.java, so ignore its exit status here.
set +e
"$BT/aapt" package -f \
  -M AndroidManifest.xml \
  -S "$MERGED_RES" \
  -I "$ANDROID_JAR" \
  -J gen
set -e

# 1c. Expand the main R.java into an R.java for every library package.
python3 ../gen_library_R.py

# 2. Compile app Java sources against all libraries
find src gen -name '*.java' -type f > sources.txt
javac -source 1.8 -target 1.8 -encoding UTF-8 \
  -classpath "${CP}${ANDROID_JAR}" \
  -d obj @sources.txt

# 3. Convert app classes + every library jar to dex (auto multidex)
java -cp "$R8_JAR" com.android.tools.r8.D8 --release --min-api 21 \
  --lib "$ANDROID_JAR" \
  --output dex \
  $(find obj -name '*.class' | tr '\n' ' ') \
  $(cat "$BUILD/cp_list.txt" | tr '\n' ' ')

# 4. Stage dex, native libs and assets with correct apk paths
cp dex/*.dex staging/
mkdir -p staging/lib/armeabi-v7a
cp "$LIB_DIR"/*.so staging/lib/armeabi-v7a/
mkdir -p staging/assets
(cd "$ASSET_DIR" && find . -type f -exec cp --parents {} "$PROJECT/staging/assets/" \;)

# 5. Add everything into the apk. '%P' strips the leading "./" that
# "find ." otherwise emits, so entries are named classes.dex,
# lib/<abi>/x.so and assets/x (a leading "./" breaks the 5.1 installer).
cd staging
"$BT/aapt" add ../bin/app.unaligned.apk $(find . -type f -printf '%P\n' | sort)
cd ..

# 6. Align
"$BT/zipalign" -f 4 bin/app.unaligned.apk bin/app.aligned.apk

# 7. Keystore
if [ ! -f engine.keystore ]; then
  keytool -genkeypair \
    -keystore engine.keystore \
    -storepass android -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias ge -dname "CN=dev"
fi

# 8. Sign and verify
"$BT/apksigner" sign \
  --ks engine.keystore \
  --ks-pass pass:android --key-pass pass:android \
  --out bin/engine.apk \
  bin/app.aligned.apk

"$BT/apksigner" verify --print-certs bin/engine.apk | head -3
echo "----------------------------------------"
ls -la bin/engine.apk
SIZE=$(stat -c%s bin/engine.apk)
echo "size_bytes=$SIZE"
echo "size_mb=$((SIZE/1048576))"
echo "BUILD_OK"
