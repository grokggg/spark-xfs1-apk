#!/bin/bash
set -eu
JAVA_HOME=/tmp/tools/dl/jdk-17.0.21+9
SDK=/tmp/android/sdk
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
SRC=/workspace/build/apk/src
OUT=/workspace/build/apk/out
KS=/workspace/build/apk/keystore/xfs1.jks
export JAVA_HOME
export PATH=$JAVA_HOME/bin:$PATH

mkdir -p "$OUT/classes" "$OUT/dex" "$(dirname "$KS")"
rm -rf "$OUT/classes" "$OUT/dex"; mkdir -p "$OUT/classes" "$OUT/dex"

if [ ! -f "$KS" ]; then
  echo "[1/8] 生成自签名 keystore"
  keytool -genkeypair -v -keystore "$KS" -storepass xfs1demo -keypass xfs1demo \
    -alias xfs1 -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Spark XFS-1, OU=Self-Signed, O=Xinghuo Demo, C=CN" >/dev/null
else
  echo "[1/8] keystore 已存在，复用"
fi

echo "[2/8] javac 编译"
javac -source 8 -target 8 -bootclasspath "$PLATFORM" -d "$OUT/classes" $(find "$SRC" -name '*.java') 2>&1 | grep -v "bootstrap class path" || true
ls "$OUT/classes/com/xinghuo/sparkdemo/" | head

echo "[3/8] d8 转 dex"
"$BT/d8" --min-api 26 --lib "$PLATFORM" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "[4/8] aapt2 link（无资源）"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLATFORM" \
  --manifest /workspace/build/apk/AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 34

echo "[5/8] 注入 classes.dex"
python3 - "$OUT/base.apk" "$OUT/dex/classes.dex" "$OUT/unsigned.apk" <<'PY'
import sys, zipfile, shutil
base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
shutil.copyfile(base, out)
with zipfile.ZipFile(out, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
print('injected', dex)
PY

echo "[6/8] zipalign"
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[7/8] apksigner 签名（v1+v2+v3）"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:xfs1demo --key-pass pass:xfs1demo \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out /workspace/build/apk/Spark-XFS1-1.0.0.apk "$OUT/aligned.apk"

echo "[8/8] 验证签名"
"$BT/apksigner" verify --print-certs -v /workspace/build/apk/Spark-XFS1-1.0.0.apk

APK=/workspace/build/apk/Spark-XFS1-1.0.0.apk
ls -l "$APK"
sha256sum "$APK"
