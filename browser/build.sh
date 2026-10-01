#!/bin/bash
set -e

SDK=/home/user/.doubao/agent_mode/workspace/.sessions/38444332457187330/agents/m_0cwps1nIzyG/android-sdk
JDK=/home/user/.doubao/agent_mode/workspace/.sessions/38444332457187330/agents/m_0cwps1nIzyG/jdk-11.0.32.1+1
export PATH="$JDK/bin:$PATH"
BT=$SDK/build-tools/30.0.3
ANDROID_JAR=$SDK/platforms/android-22/android.jar

PROJECT=/home/user/Doubao/chats/38444332457187330/browser
cd "$PROJECT"

rm -rf gen obj bin sources.txt
mkdir -p gen obj bin

# Compile resources, generate R.java and the base apk
"$BT/aapt" package -f \
  -M AndroidManifest.xml \
  -S res \
  -I "$ANDROID_JAR" \
  -J gen \
  -F bin/app.unaligned.apk

# Compile Java sources
find src gen -name '*.java' > sources.txt
javac -source 1.8 -target 1.8 -encoding UTF-8 \
  -classpath "$ANDROID_JAR" \
  -d obj @sources.txt

# Convert to dex
"$BT/d8" --release --min-api 21 \
  --lib "$ANDROID_JAR" \
  --output bin \
  $(find obj -name '*.class')

# Add dex into apk (must run from the dex directory)
cd bin
"$BT/aapt" add app.unaligned.apk classes.dex
cd ..

# Align
"$BT/zipalign" -f 4 bin/app.unaligned.apk bin/app.aligned.apk

# Keystore
if [ ! -f debug.keystore ]; then
  keytool -genkeypair \
    -keystore debug.keystore \
    -storepass android -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias db -dname "CN=dev"
fi

# Sign and verify
"$BT/apksigner" sign \
  --ks debug.keystore \
  --ks-pass pass:android --key-pass pass:android \
  --out bin/browser.apk \
  bin/app.aligned.apk

"$BT/apksigner" verify --print-certs bin/browser.apk | head -3
echo "----------------------------------------"
ls -la bin/browser.apk
SIZE=$(stat -c%s bin/browser.apk)
echo "size_bytes=$SIZE"
if [ "$SIZE" -le 10485760 ]; then
  echo "SIZE_OK (<=10MB)"
else
  echo "SIZE_FAIL (>10MB)"
fi
echo "BUILD_OK"
