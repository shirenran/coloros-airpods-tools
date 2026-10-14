#!/bin/bash
# 编译并签名 LSPosed 模块：./build.sh            产物在 release/AirPodsOff.apk
#                          ./build.sh install    编译后用 adb 安装到手机
# 依赖：JDK 11+、curl、zip；首次运行会从 Maven 下载编译工具，并从已连接的手机拉 framework-res.apk
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
T="$ROOT/build-tools"
M="$ROOT/lsposed-module"
B="$ROOT/build"
KEY="${KEY_FILE:-$M/key.p12}"
KEY_PASS="${KEY_PASS:-airpods}"
MAVEN="${MAVEN_MIRROR:-https://maven.aliyun.com/repository}"
AAPT2_VER=8.5.2-11315950

fetch() { [ -f "$2" ] || { echo "下载 $(basename "$2")"; curl -fsSL --retry 2 -o "$2" "$1"; }; }

mkdir -p "$T"
fetch "$MAVEN/google/com/android/tools/r8/8.5.35/r8-8.5.35.jar" "$T/r8.jar"
fetch "$MAVEN/google/com/android/tools/build/apksig/8.5.2/apksig-8.5.2.jar" "$T/apksig.jar"
fetch "$MAVEN/public/de/robv/android/xposed/api/82/api-82.jar" "$T/xposed-api.jar"
if [ ! -x "$T/aapt2/aapt2" ]; then
  case "$(uname -s)" in Darwin) os=osx ;; Linux) os=linux ;; *) echo "不支持的系统" >&2; exit 1 ;; esac
  fetch "$MAVEN/google/com/android/tools/build/aapt2/$AAPT2_VER/aapt2-$AAPT2_VER-$os.jar" "$T/aapt2.jar"
  mkdir -p "$T/aapt2" && unzip -o -q "$T/aapt2.jar" aapt2 -d "$T/aapt2" && chmod +x "$T/aapt2/aapt2"
  xattr -d com.apple.quarantine "$T/aapt2/aapt2" 2>/dev/null || true
fi
# aapt2 链接资源需要一个 Android 框架资源包，直接取自目标手机
[ -f "$T/framework-res.apk" ] || adb pull /system/framework/framework-res.apk "$T/framework-res.apk"
# 首次编译生成自己的签名密钥；覆盖升级必须保持同一个密钥
[ -f "$KEY" ] || keytool -genkeypair -keystore "$KEY" -storetype PKCS12 -storepass "$KEY_PASS" \
  -alias key -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=airpodsoff"

rm -rf "$B" && mkdir -p "$B/cls" "$B/dex" "$B/flat" "$B/signer" "$ROOT/release"
javac --release 8 -nowarn -cp "$T/xposed-api.jar" -d "$B/cls" $(find "$M/src" -name '*.java')
java -cp "$T/r8.jar" com.android.tools.r8.D8 --min-api 30 --classpath "$T/xposed-api.jar" \
  --output "$B/dex" $(find "$B/cls" -name '*.class')
"$T/aapt2/aapt2" compile --dir "$M/res" -o "$B/flat/"
"$T/aapt2/aapt2" link -I "$T/framework-res.apk" --manifest "$M/AndroidManifest.xml" \
  -A "$M/assets" -o "$B/unsigned.apk" "$B"/flat/*.flat
(cd "$B/dex" && zip -q ../unsigned.apk classes.dex)

javac -nowarn -cp "$T/apksig.jar" -d "$B/signer" "$ROOT/tools/Sign.java"
java -cp "$T/apksig.jar:$B/signer" Sign "$KEY" "$KEY_PASS" "$B/unsigned.apk" "$ROOT/release/AirPodsOff.apk"

if [ "${1:-}" = "install" ]; then
  adb install -r "$ROOT/release/AirPodsOff.apk"
  echo "已安装：在 LSPosed 中启用模块、作用域勾选「蓝牙」，然后重启手机"
fi
