#!/usr/bin/env bash
# Копирует нативные исходники tgcalls и Java-слой WebRTC из Telegram-Android в проект Yougram.
# Запуск из корня Yougram:  bash tools/setup_tgvoip.sh [каталог для клона Telegram, по умолчанию ./_tg]
set -euo pipefail

Y="$(cd "$(dirname "$0")/.." && pwd)"
TG="${1:-$Y/_tg}"
D="$Y/app/src/main/cpp/tgvoip"

if [ ! -d "$TG/.git" ]; then
  git clone --depth 1 https://github.com/DrKLO/Telegram "$TG"
fi
cd "$TG"
git submodule update --init --depth 1 \
  TMessagesProj/jni/third_party/absl TMessagesProj/jni/third_party/libyuv \
  TMessagesProj/jni/third_party/boringssl TMessagesProj/jni/third_party/dav1d \
  TMessagesProj/jni/third_party/libvpx TMessagesProj/jni/third_party/xiph/opus \
  TMessagesProj/jni/td

J="$TG/TMessagesProj/jni"
mkdir -p "$D/prebuild/lib/arm64-v8a"

cp -r "$J/voip" "$J/openssl" "$D/"
rsync -a --exclude .git --exclude breakpad --exclude ffmpeg --exclude openh264 --exclude wamr \
  "$J/third_party/" "$D/third_party/"
rsync -a --exclude .git "$J/td/" "$D/td/"
cp -r "$J/prebuild/include" "$D/prebuild/"
for l in crypto ssl vpx dav1d opus openh264 iwasm tde2e tdutils; do
  cp "$J/prebuild/lib/arm64-v8a/lib$l.a" "$D/prebuild/lib/arm64-v8a/"
done
sed -i 's/${CMAKE_HOME_DIRECTORY}/${CMAKE_CURRENT_SOURCE_DIR}/g' "$D/voip/CMakeLists.txt"

# Java-слой WebRTC (нужен звуку во время выполнения).
mkdir -p "$Y/app/src/main/java/org"
rm -rf "$Y/app/src/main/java/org/webrtc"
cp -r "$TG/TMessagesProj/src/main/java/org/webrtc" "$Y/app/src/main/java/org/webrtc"
rm -f "$Y/app/src/main/java/org/webrtc/TextureViewRenderer.java"

# Старый движок на NTgCalls больше не нужен.
rm -f "$Y/app/src/main/java/app/yougram/feature/calls/data/NTgCallsEngine.kt"

echo "Готово. Клон Telegram можно удалить: $TG"
