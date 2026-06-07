#!/bin/bash
# Claude Code on the web 用 SessionStart フック（settings.json の matcher で startup 限定）。
# 新規セッション起動時に Gradle のビルド依存（foojay プラグイン・JDK17 toolchain）を
# ウォームアップし、`./gradlew build` が緑になることを検証する。
# resume / clear / compact では発火させない（毎回の同期ビルドで作業がブロックされるのを防ぐ）。
# あわせてヘッドレス GUI スクショ（scripts/capture-app-screenshot-linux.sh）に必要な
# Xvfb の存在を保証する（Mesa ソフトウェア GL は基盤イメージに同梱済み）。
# コンテナ状態はフック完了後にキャッシュされるため、以降のセッションは温まった状態で始まる。
#
# 重要：SessionStart の stdout は Claude のコンテキストへ追加される。冗長な Gradle/apt 出力で
# コンテキストを汚さないよう、詳細はログファイルへ逃がし、stdout は要約 1 行のみに抑える。
set -euo pipefail

# リモート（Claude Code on the web）以外では何もしない。ローカル開発の邪魔をしない。
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"

BUILD_LOG="/tmp/session-start-build.log"
XVFB_LOG="/tmp/session-start-xvfb.log"

# build = compile + test。これ一発で toolchain provision・プラグイン取得・依存解決が温まる。
# 冪等（再実行安全）・非対話。冗長出力は BUILD_LOG へ逃がす（stdout を汚さない）。
# 失敗してもセッション開始はブロックせず、要約 1 行で知らせる（詳細はログ）。
if ./gradlew build --console=plain --no-daemon >"$BUILD_LOG" 2>&1; then
  build_status="ビルド緑"
else
  build_status="ビルド失敗（要確認: ${BUILD_LOG}）"
fi

# ヘッドレス GUI スクショ用の Xvfb を保証（既に在れば何もしない）。ベストエフォート：
# ネットワークポリシー次第で apt が失敗してもセッション開始を妨げないよう非致命にする。
if ! command -v Xvfb >/dev/null 2>&1; then
  (apt-get update -qq && apt-get install -y -qq xvfb) >"$XVFB_LOG" 2>&1 || true
fi
if command -v Xvfb >/dev/null 2>&1; then
  gui_status="GUI スクショ可"
else
  gui_status="GUI スクショ不可（Xvfb 無し）"
fi

# Claude のコンテキストへ渡す stdout は要約 1 行のみ（冗長ログは上記ファイル参照）。
echo "[session-start] ${build_status} / ${gui_status}（詳細ログ: ${BUILD_LOG}）"
