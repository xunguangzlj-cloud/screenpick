#!/usr/bin/env bash
# 手工构建 ScreenPick APK —— 不依赖 Gradle / Android Studio。
# 只用 SDK 自带的 aapt2 + javac + d8 + zipalign + apksigner。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-/d/DSH/buildenv/sdk}"
BT="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
JH="${JAVA_HOME:-/d/DSH/buildenv/jdk-17.0.20.1+1}"
export PATH="$JH/bin:$PATH"

OUT="$ROOT/build"
GEN="$OUT/gen"
OBJ="$OUT/obj"
APK_UNSIGNED="$OUT/base-unsigned.apk"
APK_ALIGNED="$OUT/base-aligned.apk"
APK_FINAL="$ROOT/dist/screenpick-v1.0.apk"

MIN_SDK=26
TARGET_SDK=34
VERSION_CODE=1
VERSION_NAME="1.0"

rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$GEN" "$OBJ" "$ROOT/dist"

echo "==> 1/6 aapt2 compile"
"$BT/aapt2.exe" compile --dir "$ROOT/res" -o "$OUT/compiled/res.zip"

echo "==> 2/6 aapt2 link"
"$BT/aapt2.exe" link \
  -o "$APK_UNSIGNED" \
  -I "$PLATFORM" \
  --manifest "$ROOT/AndroidManifest.xml" \
  -A "$ROOT/assets" \
  --java "$GEN" \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  --auto-add-overlay \
  "$OUT/compiled/res.zip"

echo "==> 3/6 javac"
find "$ROOT/src" "$GEN" -name '*.java' | while IFS= read -r f; do cygpath -w "$f"; done > "$OUT/sources.txt"
echo "   源文件 $(wc -l < "$OUT/sources.txt") 个"

# 注意：这里绝不能用 "javac ... | grep ... || true"，管道会把退出码吞掉，
# 编译失败也照样打包，产出的 APK 一启动就 NoClassDefFoundError。
SRC_LIST="$(cygpath -w "$OUT/sources.txt")"
if ! "$JH/bin/javac.exe" -nowarn -Xlint:-options   -source 8 -target 8 -encoding UTF-8   -classpath "$PLATFORM"   -d "$OBJ"   "@$SRC_LIST" > "$OUT/javac.log" 2>&1; then
  echo "!! javac 失败："
  grep -v 'bootstrap class path' "$OUT/javac.log" | head -40
  exit 1
fi
grep -v 'bootstrap class path' "$OUT/javac.log" | grep -vE '^注:|^Note:' | head -5 || true

# 产物校验：每个顶层源文件都必须生成同名 .class，缺一个就中止。
echo "   校验 class 产物"
while IFS= read -r src; do
  base="$(basename "$src" .java)"
  if [ ! -f "$OBJ/com/screenpick/app/$base.class" ]; then
    echo "!! 缺少 $base.class（源文件 $src）—— 编译没走到这一步，禁止打包"
    exit 1
  fi
done < <(find "$ROOT/src" -name '*.java')
echo "   class 产物完整：$(find "$OBJ" -name '*.class' | wc -l) 个"

echo "==> 4/6 d8"
mkdir -p "$OUT/dex"
find "$OBJ" -name '*.class' | while IFS= read -r f; do cygpath -w "$f"; done > "$OUT/classes.txt"
echo "   class 文件 $(wc -l < "$OUT/classes.txt") 个"
# --thread-count 1：d8 8.2.2 并行打包会偶发 NullPointerException
# （"Cannot invoke String.length() because null"，报错落在哪个 class 上每次都不一样），
# 单线程稳定。输出落日志，失败时原样打印，不再用 "> /dev/null" 把现场吞掉。
if ! "$BT/d8.bat" --min-api "$MIN_SDK" --lib "$PLATFORM" --thread-count 1 \
  --output "$OUT/dex" @"$OUT/classes.txt" > "$OUT/d8.log" 2>&1; then
  echo "!! d8 失败："
  grep -vE '^[[:space:]]+at ' "$OUT/d8.log" | head -12
  exit 1
fi

echo "==> 5/6 打包 dex + assets"
"$JH/bin/java.exe" -version 2>/dev/null || true
python - "$APK_UNSIGNED" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile, shutil, os
apk, dex = sys.argv[1], sys.argv[2]
tmp = apk + ".tmp"
with zipfile.ZipFile(apk, "r") as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.write(dex, "classes.dex")
shutil.move(tmp, apk)
print("   dex 已写入，条目数:", len(zipfile.ZipFile(apk).namelist()))
PY

echo "==> 6/6 zipalign + apksigner"
"$BT/zipalign.exe" -f -p 4 "$APK_UNSIGNED" "$APK_ALIGNED"

KS="$ROOT/keystore/screenpick.keystore"
if [ ! -f "$KS" ]; then
  echo "   生成签名证书"
  mkdir -p "$ROOT/keystore"
  "$JH/bin/keytool.exe" -genkeypair -v \
    -keystore "$KS" -alias screenpick -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass screenpick -keypass screenpick \
    -dname "CN=ScreenPick, OU=Dev, O=ScreenPick, L=Beijing, ST=Beijing, C=CN" 2>/dev/null
fi

"$BT/apksigner.bat" sign \
  --ks "$KS" --ks-pass pass:screenpick --key-pass pass:screenpick \
  --ks-key-alias screenpick \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$APK_FINAL" "$APK_ALIGNED"

"$BT/apksigner.bat" verify --print-certs "$APK_FINAL" | head -5

echo ""
echo "✅ 构建完成: $APK_FINAL"
ls -lh "$APK_FINAL"
