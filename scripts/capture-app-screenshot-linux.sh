#!/bin/bash
# ヘッドレス Linux（Claude Code on the web / CI）でゲーム画面のスクリーンショットを撮る。
#
# Windows は scripts/capture-app-window.ps1 で外部からウィンドウを撮るが、ウィンドウシステムの
# 無いリモート Linux では撮れない。本スクリプトは Xvfb（仮想ディスプレイ）＋ Mesa ソフトウェア GL
# 上でアプリを起動し、アプリ自身が指定フレームでフレームバッファを PNG に書き出して自動終了する
# （アプリ側の実装は GameRuntime/Debug/.../ScreenshotController.java）。
#
# 使い方:
#   scripts/capture-app-screenshot-linux.sh [-o 出力PNG] [-f フレーム番号] [-W 幅] [-H 高さ]
# 例:
#   scripts/capture-app-screenshot-linux.sh -o docs/screenshots/4-window-display.png
set -euo pipefail

OUT="docs/screenshots/linux-capture.png"
FRAME="90"
SCREEN_W="1280"
SCREEN_H="720"
DISPLAY_NUM="99"

while getopts "o:f:W:H:d:" opt; do
  case "$opt" in
    o) OUT="$OPTARG" ;;
    f) FRAME="$OPTARG" ;;
    W) SCREEN_W="$OPTARG" ;;
    H) SCREEN_H="$OPTARG" ;;
    d) DISPLAY_NUM="$OPTARG" ;;
    *) echo "usage: $0 [-o out.png] [-f frame] [-W width] [-H height] [-d display]" >&2; exit 2 ;;
  esac
done

# リポジトリルートへ移動（このスクリプトは scripts/ 配下）。
cd "$(dirname "$0")/.."
mkdir -p "$(dirname "$OUT")"
OUT_ABS="$(cd "$(dirname "$OUT")" && pwd)/$(basename "$OUT")"

# 依存チェック（apt 追加は不要。Ubuntu 標準で Xvfb / Mesa swrast を想定）。
command -v Xvfb >/dev/null 2>&1 || { echo "Xvfb が見つかりません（apt-get install -y xvfb）" >&2; exit 1; }

# Xvfb を起動（既存のロックがあれば再利用）。
if [ ! -e "/tmp/.X${DISPLAY_NUM}-lock" ]; then
  echo "[capture] Xvfb :${DISPLAY_NUM} を起動 (${SCREEN_W}x${SCREEN_H}x24)"
  Xvfb ":${DISPLAY_NUM}" -screen 0 "${SCREEN_W}x${SCREEN_H}x24" >/tmp/xvfb-${DISPLAY_NUM}.log 2>&1 &
  XVFB_PID=$!
  trap '[ -n "${XVFB_PID:-}" ] && kill "${XVFB_PID}" 2>/dev/null || true' EXIT
  # 起動待ち。
  for _ in $(seq 1 20); do
    sleep 0.3
    [ -e "/tmp/.X${DISPLAY_NUM}-lock" ] && break
  done
fi

export DISPLAY=":${DISPLAY_NUM}"
# Mesa ソフトウェアレンダリング（GPU 無しでも OpenGL を出す）。
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
# LWJGL3/GLFW が要求する GL バージョンをソフトウェアで満たすため明示上書き。
export MESA_GL_VERSION_OVERRIDE="3.3"
export MESA_GLSL_VERSION_OVERRIDE="330"

echo "[capture] アプリを起動して frame=${FRAME} で撮影 -> ${OUT_ABS}"
rm -f "$OUT_ABS"
# 撮影モードのシステムプロパティを渡して起動。撮影後にアプリが自動終了する。
./gradlew run --console=plain \
  -Dphantom.screenshot.path="${OUT_ABS}" \
  -Dphantom.screenshot.frame="${FRAME}"

if [ ! -s "$OUT_ABS" ]; then
  echo "[capture] 失敗: PNG が生成されませんでした (${OUT_ABS})" >&2
  exit 1
fi
echo "[capture] 完了: ${OUT_ABS}"
ls -l "$OUT_ABS"
