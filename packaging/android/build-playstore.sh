#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME="/workspace/tools/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

ANDROID_DIR="/workspace/solidcraft/packaging/android"
SDK_DIR="/workspace/tools/android-sdk"
BUILD_TOOLS="$SDK_DIR/build-tools/35.0.1"
PLATFORM_JAR="$SDK_DIR/platforms/android-35/android.jar"
KEYSTORE="$ANDROID_DIR/solidcraft-release-upload.jks"
KEY_ALIAS="solidcraft_upload"
KEY_PASS="SolidCraft2026!"
BUNDLETOOL="/workspace/tools/bundletool.jar"
WORK_DIR="$ANDROID_DIR/build_playstore_tmp"
PLAY_DIR="/workspace/solidcraft/build/playstore"
FINAL_AAB="$PLAY_DIR/solidcraft-s26ultra-v0.21.0-r3-release.aab"
FINAL_APK="$PLAY_DIR/solidcraft-s26ultra-v0.21.0-r3-release.apk"

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/gen" "$WORK_DIR/classes" "$WORK_DIR/dex" "$WORK_DIR/aab_base" "$PLAY_DIR"

echo "[1/8] Compiling Android resources with aapt2..."
"$BUILD_TOOLS/aapt2" compile --dir "$ANDROID_DIR/res" -o "$WORK_DIR/compiled_res.zip"

echo "[2/8] Linking proto-format base APK for AAB..."
"$BUILD_TOOLS/aapt2" link \
  --proto-format \
  -o "$WORK_DIR/base_proto.apk" \
  -I "$PLATFORM_JAR" \
  --manifest "$ANDROID_DIR/AndroidManifest.xml" \
  --min-sdk-version 21 \
  --target-sdk-version 35 \
  --version-code 2103 \
  --version-name "0.21.0-s26ultra-r3" \
  --java "$WORK_DIR/gen" \
  --auto-add-overlay \
  "$WORK_DIR/compiled_res.zip"

echo "[3/8] Compiling MainActivity.java & R.java..."
javac -source 11 -target 11 \
  -cp "$PLATFORM_JAR" \
  -d "$WORK_DIR/classes" \
  "$WORK_DIR/gen/com/tangencylabs/solidcraft/R.java" \
  "$ANDROID_DIR/src/com/tangencylabs/solidcraft/MainActivity.java"

echo "[4/8] Converting JVM bytecode to Dalvik Executable (classes.dex) via d8..."
CLASS_FILES=$(find "$WORK_DIR/classes" -name "*.class")
"$BUILD_TOOLS/d8" \
  --release \
  --lib "$PLATFORM_JAR" \
  --min-api 26 \
  --output "$WORK_DIR/dex" \
  $CLASS_FILES

echo "[5/8] Assembling AAB base module zip..."
python3 - "$WORK_DIR/base_proto.apk" "$WORK_DIR/dex/classes.dex" "$ANDROID_DIR/assets" "$WORK_DIR/base.zip" << 'PY'
import os, sys, zipfile

proto_apk, dex_path, assets_dir, out_zip = sys.argv[1:5]

with zipfile.ZipFile(out_zip, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
    with zipfile.ZipFile(proto_apk, 'r') as zin:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename == 'AndroidManifest.xml':
                zout.writestr('manifest/AndroidManifest.xml', data)
            elif item.filename == 'resources.pb':
                zout.writestr('resources.pb', data)
            elif item.filename.startswith('res/'):
                zout.writestr(item.filename, data)
    zout.write(dex_path, 'dex/classes.dex')
    for root, _, files in os.walk(assets_dir):
        for f in sorted(files):
            full_path = os.path.join(root, f)
            rel_path = os.path.relpath(full_path, assets_dir)
            zout.write(full_path, os.path.join('assets', rel_path))
PY

echo "[6/8] Building and signing Android App Bundle (.aab) with bundletool + jarsigner..."
rm -f "$FINAL_AAB"
java -jar "$BUNDLETOOL" build-bundle \
  --modules="$WORK_DIR/base.zip" \
  --config="$ANDROID_DIR/BundleConfig.json" \
  --output="$FINAL_AAB"

jarsigner -verbose \
  -sigalg SHA256withRSA \
  -digestalg SHA-256 \
  -keystore "$KEYSTORE" \
  -storepass "$KEY_PASS" \
  -keypass "$KEY_PASS" \
  "$FINAL_AAB" \
  "$KEY_ALIAS" > "$WORK_DIR/jarsigner.log"

echo "[7/8] Validating .aab with bundletool validate & testing build-apks..."
java -jar "$BUNDLETOOL" validate --bundle="$FINAL_AAB"
rm -f "$WORK_DIR/test.apks"
java -jar "$BUNDLETOOL" build-apks \
  --bundle="$FINAL_AAB" \
  --output="$WORK_DIR/test.apks" \
  --ks="$KEYSTORE" \
  --ks-pass="pass:$KEY_PASS" \
  --ks-key-alias="$KEY_ALIAS" \
  --key-pass="pass:$KEY_PASS"
java -jar "$BUNDLETOOL" get-size total --apks="$WORK_DIR/test.apks"

echo "[8/8] Building and signing matching Production Release APK (.apk)..."
"$BUILD_TOOLS/aapt2" link \
  -o "$WORK_DIR/unaligned.apk" \
  -I "$PLATFORM_JAR" \
  --manifest "$ANDROID_DIR/AndroidManifest.xml" \
  --min-sdk-version 21 \
  --target-sdk-version 35 \
  --version-code 2103 \
  --version-name "0.21.0-s26ultra-r3" \
  -A "$ANDROID_DIR/assets" \
  -0 .wasm -0 .data -0 .js -0 .html -0 .css -0 .svg -0 .ttf -0 .png -0 .webmanifest \
  "$WORK_DIR/compiled_res.zip"

python3 - "$WORK_DIR/unaligned.apk" "$WORK_DIR/dex/classes.dex" << 'PY'
import sys, zipfile
apk_path, dex_path = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk_path, 'a', compression=zipfile.ZIP_STORED) as zf:
    zf.write(dex_path, 'classes.dex', compress_type=zipfile.ZIP_STORED)
PY

"$BUILD_TOOLS/zipalign" -f -P 16 4 "$WORK_DIR/unaligned.apk" "$WORK_DIR/aligned.apk"

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEY_PASS" \
  --key-pass "pass:$KEY_PASS" \
  --min-sdk-version 21 \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$FINAL_APK" \
  "$WORK_DIR/aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$FINAL_APK"
"$BUILD_TOOLS/aapt2" dump badging "$FINAL_APK" | head -30
ls -lh "$FINAL_AAB" "$FINAL_APK"
