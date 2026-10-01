#!/usr/bin/env python3
import sys

COUNT = 40

def svc(name, process, isolated=False, stop_task=False):
    extra = ' android:stopWithTask="true"' if stop_task else ''
    return (
        '        <service\n'
        '                android:name="%s"\n'
        '                android:enabled="true"\n'
        '                android:exported="false"\n'
        '                android:isolatedProcess="%s"\n'
        '                android:process="%s"%s>\n'
        '        </service>\n' % (name,
            "true" if isolated else "false", process, extra)
    )

parts = []
parts.append('<?xml version="1.0" encoding="utf-8"?>\n')
parts.append(
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    xmlns:tools="http://schemas.android.com/tools"\n'
    '    package="com.tclbrowser.gecko"\n'
    '    android:versionCode="1"\n'
    '    android:versionName="144.0">\n\n')

parts.append(
    '    <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="22"/>\n')
for p in [
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.WAKE_LOCK",
    "android.permission.MODIFY_AUDIO_SETTINGS",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.VIBRATE",
]:
    parts.append('    <uses-permission android:name="%s"/>\n' % p)

for feat in [
    "android.hardware.touchscreen",
    "android.hardware.faketouch",
    "android.hardware.camera",
    "android.hardware.microphone",
    "android.hardware.location",
    "android.hardware.gamepad",
    "android.hardware.usb.host",
]:
    parts.append(
        '    <uses-feature android:name="%s" android:required="false"/>\n' % feat)
parts.append(
    '    <uses-feature android:glEsVersion="0x00020000" '
    'android:required="true"/>\n\n')

parts.append(
    '    <application\n'
    '        android:label="@string/app_name"\n'
    '        android:icon="@drawable/ic_launcher"\n'
    '        android:theme="@style/AppTheme"\n'
    '        android:hardwareAccelerated="true"\n'
    '        android:allowBackup="true"\n'
    '        android:largeHeap="true"\n'
    '        android:isGame="true">\n\n')

parts.append(
    '        <activity\n'
    '            android:name=".GeckoEngineActivity"\n'
    '            android:exported="true"\n'
    '            android:launchMode="singleTask"\n'
    '            android:screenOrientation="landscape"\n'
    '            android:configChanges="orientation|screenSize|screenLayout|'
    'keyboardHidden|keyboard|navigation|uiMode|smallestScreenSize">\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.MAIN"/>\n'
    '                <category android:name="android.intent.category.LAUNCHER"/>\n'
    '            </intent-filter>\n'
    '            <intent-filter>\n'
    '                <action android:name="android.intent.action.VIEW"/>\n'
    '                <category android:name="android.intent.category.DEFAULT"/>\n'
    '                <category android:name="android.intent.category.BROWSABLE"/>\n'
    '                <data android:scheme="http"/>\n'
    '                <data android:scheme="https"/>\n'
    '            </intent-filter>\n'
    '        </activity>\n\n')

parts.append(svc("org.mozilla.gecko.media.MediaManager", ":media"))
parts.append(svc("org.mozilla.gecko.process.GeckoChildProcessServices$gmplugin",
                 ":gmplugin"))
parts.append(svc("org.mozilla.gecko.process.GeckoChildProcessServices$socket",
                 ":socket"))
parts.append(svc("org.mozilla.gecko.process.GeckoChildProcessServices$gpu",
                 ":gpu"))
parts.append(svc("org.mozilla.gecko.process.GeckoChildProcessServices$rdd",
                 ":rdd"))
parts.append(svc("org.mozilla.gecko.process.GeckoChildProcessServices$utility",
                 ":utility"))
parts.append(svc("org.mozilla.gecko.process.GeckoChildProcessServices$ipdlunittest",
                 ":ipdlunittest"))
parts.append(svc("org.mozilla.gecko.crashhelper.CrashHelper", ":crashhelper",
                 stop_task=True))

for i in range(COUNT):
    parts.append(svc(
        "org.mozilla.gecko.process.GeckoChildProcessServices$tab%d" % i,
        ":tab%d" % i, isolated=False))
    parts.append(svc(
        "org.mozilla.gecko.process.GeckoChildProcessServices$isolatedTab%d" % i,
        ":isolatedTab%d" % i, isolated=True))

parts.append('    </application>\n</manifest>\n')

out = "".join(parts)
with open("engine/AndroidManifest.xml", "w") as f:
    f.write(out)
print("wrote engine/AndroidManifest.xml (%d bytes)" % len(out))
