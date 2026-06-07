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
HOLD=""
SCREEN_W="1280"
SCREEN_H="720"
DISPLAY_NUM="99"
# 追加の撮影プロパティ（-x key=val を繰り返すと -Dphantom.screenshot.key=val を渡す）。
# 近接が必要な被弾スクショ等で p1x / p2x の初期位置オーバーライドに使う。
EXTRA_PROPS=()

while getopts "o:f:k:W:H:d:x:" opt; do
  case "$opt" in
    o) OUT="$OPTARG" ;;
    f) FRAME="$OPTARG" ;;
    k) HOLD="$OPTARG" ;;
    W) SCREEN_W="$OPTARG" ;;
    H) SCREEN_H="$OPTARG" ;;
    d) DISPLAY_NUM="$OPTARG" ;;
    x) EXTRA_PROPS+=("-Dphantom.screenshot.${OPTARG%%=*}=${OPTARG#*=}") ;;
    *) echo "usage: $0 [-o out.png] [-f frame] [-k hold] [-W width] [-H height] [-d display] [-x key=val]" >&2; exit 2 ;;
  esac
done

# リポジトリルートへ移動（このスクリプトは scripts/ 配下）。
cd "$(dirname "$0")/.."
mkdir -p "$(dirname "$OUT")"
OUT_ABS="$(cd "$(dirname "$OUT")" && pwd)/$(basename "$OUT")"

# 依存チェック（apt 追加は不要。Ubuntu 標準で Xvfb / Mesa swrast を想定）。
command -v Xvfb >/dev/null 2>&1 || { echo "Xvfb が見つかりません（apt-get install -y xvfb）" >&2; exit 1; }

# 当該 display で Xvfb が「実際に」動作中かを検証する（ロックファイルの有無だけで判定しない）。
# xdpyinfo があれば接続可否で判定し、無ければ X ソケットの実在＋生プロセスの両面で確認する。
# kill / 中断で stale な /tmp/.X${N}-lock だけが残ったケースを再利用扱いしないため。
xvfb_running() {
  if command -v xdpyinfo >/dev/null 2>&1; then
    xdpyinfo -display ":${DISPLAY_NUM}" >/dev/null 2>&1
    return $?
  fi
  [ -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ] && pgrep -f "Xvfb[[:space:]]*:${DISPLAY_NUM}\b" >/dev/null 2>&1
}

if ! xvfb_running; then
  # stale な lock / ソケットが残っていると Xvfb が起動を拒否するため掃除してから起動する。
  rm -f "/tmp/.X${DISPLAY_NUM}-lock" "/tmp/.X11-unix/X${DISPLAY_NUM}" 2>/dev/null || true
  echo "[capture] Xvfb :${DISPLAY_NUM} を起動 (${SCREEN_W}x${SCREEN_H}x24)"
  Xvfb ":${DISPLAY_NUM}" -screen 0 "${SCREEN_W}x${SCREEN_H}x24" >/tmp/xvfb-${DISPLAY_NUM}.log 2>&1 &
  XVFB_PID=$!
  trap '[ -n "${XVFB_PID:-}" ] && kill "${XVFB_PID}" 2>/dev/null || true' EXIT
  # 実際に接続可能になるまで待つ（ロックの出現ではなく接続性で判定）。
  for _ in $(seq 1 40); do
    sleep 0.25
    xvfb_running && break
  done
  xvfb_running || { echo "[capture] Xvfb :${DISPLAY_NUM} を起動できませんでした（/tmp/xvfb-${DISPLAY_NUM}.log）" >&2; exit 1; }
fi

export DISPLAY=":${DISPLAY_NUM}"
# Mesa ソフトウェアレンダリング（GPU 無しでも OpenGL を出す）。
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
# LWJGL3/GLFW が要求する GL バージョンをソフトウェアで満たすため明示上書き。
export MESA_GL_VERSION_OVERRIDE="3.3"
export MESA_GLSL_VERSION_OVERRIDE="330"

echo "[capture] アプリを起動して frame=${FRAME}${HOLD:+ hold=${HOLD}} で撮影 -> ${OUT_ABS}"
rm -f "$OUT_ABS"
# 撮影モードのシステムプロパティを渡して起動。撮影後にアプリが自動終了する。
HOLD_PROP=()
[ -n "$HOLD" ] && HOLD_PROP=(-Dphantom.screenshot.hold="${HOLD}")
./gradlew run --console=plain \
  -Dphantom.screenshot.path="${OUT_ABS}" \
  -Dphantom.screenshot.frame="${FRAME}" \
  "${HOLD_PROP[@]}" \
  "${EXTRA_PROPS[@]}"

if [ ! -s "$OUT_ABS" ]; then
  echo "[capture] 失敗: PNG が生成されませんでした (${OUT_ABS})" >&2
  exit 1
fi
echo "[capture] 完了: ${OUT_ABS}"
ls -l "$OUT_ABS"
