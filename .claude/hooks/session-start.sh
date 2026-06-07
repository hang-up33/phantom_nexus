#!/bin/bash
# Claude Code on the web 用 SessionStart フック（settings.json の matcher で startup 限定）。
# 新規セッション起動時に Gradle のビルド依存（foojay プラグイン・JDK17 toolchain）を
# ウォームアップし、`./gradlew build` が緑になることを検証する。
# resume / clear / compact では発火させない（毎回の同期ビルドで作業がブロックされるのを防ぐ）。
# あわせてヘッドレス GUI スクショ（scripts/capture-app-screenshot-linux.sh）に必要な
# Xvfb の存在を保証する（Mesa ソフトウェア GL は基盤イメージに同梱済み）。
# コンテナ状態はフック完了後にキャッシュされるため、以降のセッションは温まった状態で始まる。
set -euo pipefail

# リモート（Claude Code on the web）以外では何もしない。ローカル開発の邪魔をしない。
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"

echo "[session-start] Gradle 依存と JDK17 toolchain をウォームアップします..."
# build = compile + test。これ一発で toolchain provision・プラグイン取得・依存解決が温まる。
# 冪等（再実行安全）・非対話。--console=plain で対話/装飾出力を抑制。
./gradlew build --console=plain --no-daemon

# ヘッドレス GUI スクショ用の Xvfb を保証（既に在れば何もしない）。ベストエフォート：
# ネットワークポリシー次第で apt が失敗してもビルド検証を妨げないよう非致命にする。
if ! command -v Xvfb >/dev/null 2>&1; then
  echo "[session-start] Xvfb が無いため導入を試みます（ヘッドレススクショ用・ベストエフォート）..."
  (apt-get update -qq && apt-get install -y -qq xvfb) >/tmp/session-start-xvfb.log 2>&1 \
    && echo "[session-start] Xvfb 導入完了" \
    || echo "[session-start] Xvfb 導入をスキップ（GUI スクショ不要なら無視可）"
fi

echo "[session-start] 完了: ./gradlew build が緑です。"
