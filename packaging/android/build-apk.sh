#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME="/workspace/tools/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

ANDROID_DIR="/workspace/solidcraft/packaging/android"
SDK_DIR="/workspace/tools/android-sdk"
BUILD_TOOLS="$SDK_DIR/build-tools/35.0.1"
PLATFORM_JAR="$SDK_DIR/platforms/android-35/android.jar"
KEYSTORE="/workspace/tools/godsim-debug.keystore"
WORK_DIR="$ANDROID_DIR/build_tmp"
OUT_DIR="/workspace/solidcraft/build"
FINAL_APK="$OUT_DIR/solidcraft-s26ultra-v0.21.0-r2.apk"

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/gen" "$WORK_DIR/classes" "$WORK_DIR/dex" "$OUT_DIR"

echo "[1/6] Compiling Android resources with aapt2..."
"$BUILD_TOOLS/aapt2" compile --dir "$ANDROID_DIR/res" -o "$WORK_DIR/compiled_res.zip"

echo "[2/6] Linking resources, AndroidManifest.xml, and SolidCraft WASM/Web assets..."
"$BUILD_TOOLS/aapt2" link \
  -o "$WORK_DIR/unaligned.apk" \
  -I "$PLATFORM_JAR" \
  --manifest "$ANDROID_DIR/AndroidManifest.xml" \
  --java "$WORK_DIR/gen" \
  -A "$ANDROID_DIR/assets" \
  -0 .wasm -0 .js -0 .html -0 .css -0 .svg -0 .ttf -0 .png -0 .webmanifest \
  "$WORK_DIR/compiled_res.zip"

echo "[3/6] Compiling MainActivity.java & R.java..."
javac -source 11 -target 11 \
  -cp "$PLATFORM_JAR" \
  -d "$WORK_DIR/classes" \
  "$WORK_DIR/gen/com/tangencylabs/solidcraft/R.java" \
  "$ANDROID_DIR/src/com/tangencylabs/solidcraft/MainActivity.java"

echo "[4/6] Converting JVM bytecode to Dalvik Executable (classes.dex) via d8..."
CLASS_FILES=$(find "$WORK_DIR/classes" -name "*.class")
"$BUILD_TOOLS/d8" \
  --lib "$PLATFORM_JAR" \
  --min-api 28 \
  --output "$WORK_DIR/dex" \
  $CLASS_FILES

echo "[5/6] Packaging classes.dex into APK..."
python3 - "$WORK_DIR/unaligned.apk" "$WORK_DIR/dex/classes.dex" << 'PY'
import sys, zipfile
apk_path, dex_path = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk_path, 'a', compression=zipfile.ZIP_STORED) as zf:
    zf.write(dex_path, 'classes.dex', compress_type=zipfile.ZIP_STORED)
PY

echo "[6/6] Page-aligning and signing APK (v1+v2+v3)..."
"$BUILD_TOOLS/zipalign" -f -P 16 4 "$WORK_DIR/unaligned.apk" "$WORK_DIR/aligned.apk"

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --min-sdk-version 21 \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$FINAL_APK" \
  "$WORK_DIR/aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose "$FINAL_APK"
"$BUILD_TOOLS/aapt2" dump badging "$FINAL_APK" | head -30
ls -lh "$FINAL_APK"
