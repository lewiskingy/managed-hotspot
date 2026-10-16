#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk=${1:?Pass absolute Android SDK path}
bt="$sdk/build-tools/36.0.0"
android="$sdk/platforms/android-36/android.jar"
rm -rf build/classes build/dex build/generated
mkdir -p build/classes build/dex build/generated dist
python3 scripts/fetch-deps.py
sed 's/<manifest xmlns:/<manifest package="uk.me.theflat.hotspot" xmlns:/' app/src/main/AndroidManifest.xml > build/AndroidManifest.xml
"$bt/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$bt/aapt2" link -I "$android" --manifest build/AndroidManifest.xml -A app/src/main/assets --min-sdk-version 31 --target-sdk-version 36 --version-code 9 --version-name 0.2.7 --java build/generated build/resources.zip -o build/base.apk
javac --release 17 -classpath "$android:app/libs/*" -d build/classes $(find app/src/main/java build/generated -name '*.java')
jar cf build/classes.jar -C build/classes .
"$bt/d8" --lib "$android" --min-api 31 --output build/dex build/classes.jar app/libs/*.jar
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$bt/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [ ! -f debug.keystore ]; then keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias androiddebugkey -dname 'CN=Hotspot Keeper Development' -keyalg RSA -keysize 2048 -validity 3650; fi
"$bt/apksigner" sign --ks debug.keystore --ks-pass pass:android --out dist/HotspotKeeper-debug.apk build/aligned.apk
"$bt/apksigner" verify dist/HotspotKeeper-debug.apk
javac -d build/policy app/src/main/java/uk/me/theflat/hotspot/SessionPolicy.java app/src/main/java/uk/me/theflat/hotspot/SessionRules.java tests/SessionPolicyTest.java tests/SessionRulesTest.java
java -cp build/policy SessionPolicyTest
java -cp build/policy SessionRulesTest
# Separate JVM fixture: reproduce ContextImpl attribution inheritance without packaging test stubs.
javac -d build/identity $(find tests/fixtures -name '*.java') app/src/main/java/uk/me/theflat/hotspot/ShellIdentity.java tests/ShellIdentityTest.java
java -cp build/identity uk.me.theflat.hotspot.ShellIdentityTest
