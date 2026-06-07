#!/bin/bash
# Claude Code on the web 用 SessionStart フック。
# web セッション開始時に Gradle のビルド依存（foojay プラグイン・JDK17 toolchain）を
# ウォームアップし、`./gradlew build` が緑になることを検証する。
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

echo "[session-start] 完了: ./gradlew build が緑です。"
