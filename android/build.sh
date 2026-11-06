#!/usr/bin/env bash
# Builds LicenseGenerator.apk without Gradle (aapt2 + javac + d8 + apksigner).
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="${BUILD_TOOLS:-$SDK/build-tools/35.0.0}"
JAR="$SDK/platforms/android-34/android.jar"
OUT=build
KS="$HOME/.android/debug.keystore"

rm -rf "$OUT" && mkdir -p "$OUT/classes"

"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 24 --target-sdk-version 34
javac -encoding UTF-8 --release 8 -Xlint:-options -cp "$JAR" -d "$OUT/classes" src/com/licensegen/*.java
"$BT/d8" --lib "$JAR" --min-api 24 --output "$OUT" $(find "$OUT/classes" -name '*.class')
(cd "$OUT" && zip -q base.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"

if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out LicenseGenerator.apk "$OUT/aligned.apk"
echo "Built $(pwd)/LicenseGenerator.apk"
