#!/usr/bin/env bash
# テンプレートの placeholder（{{OWNER}} 等）を実プロジェクトの値に一括置換する。
#
# 使い方:
#   # 対話モード
#   bash scripts/apply-template.sh
#
#   # 非対話モード（CI 等から）
#   bash scripts/apply-template.sh \
#     --owner hang-up33 \
#     --repo my-new-project \
#     --project-name "My New Project" \
#     --default-branch main \
#     --branch-prefix task \
#     --build-cmd "npm run build" \
#     --test-cmd "npm test" \
#     --screenshot-dir docs/screenshots \
#     --app-binary-hint "./build/app" \
#     --codex-bot-login "chatgpt-codex-connector[bot]" \
#     --review-lang "日本語"
#
# 動作:
#   - リポジトリ直下の *.md / *.json / *.sh / *.ps1 を対象に sed で in-place 置換
#   - .git/ 以下と本スクリプト自身は除外
#   - 置換後に残存 placeholder（'{{...}}'）が無いか検査して報告
#   - 終了後、CLAUDE.md.template → CLAUDE.md のリネームを促す
#
# 注意:
#   - macOS の BSD sed と GNU sed の差異を吸収するため、`sed -i.bak` 方式で書いて
#     最後に *.bak を一括削除する
set -euo pipefail

# ---- 引数パース -------------------------------------------------------------

OWNER=""
REPO=""
PROJECT_NAME=""
DEFAULT_BRANCH=""
BRANCH_PREFIX=""
BUILD_CMD=""
TEST_CMD=""
SCREENSHOT_DIR=""
APP_BINARY_HINT=""
CODEX_BOT_LOGIN=""
REVIEW_LANG=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --owner)            OWNER="$2"; shift 2 ;;
        --repo)             REPO="$2"; shift 2 ;;
        --project-name)     PROJECT_NAME="$2"; shift 2 ;;
        --default-branch)   DEFAULT_BRANCH="$2"; shift 2 ;;
        --branch-prefix)    BRANCH_PREFIX="$2"; shift 2 ;;
        --build-cmd)        BUILD_CMD="$2"; shift 2 ;;
        --test-cmd)         TEST_CMD="$2"; shift 2 ;;
        --screenshot-dir)   SCREENSHOT_DIR="$2"; shift 2 ;;
        --app-binary-hint)  APP_BINARY_HINT="$2"; shift 2 ;;
        --codex-bot-login)  CODEX_BOT_LOGIN="$2"; shift 2 ;;
        --review-lang)      REVIEW_LANG="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,30p' "$0"
            exit 0
            ;;
        *)
            echo "unknown arg: $1" >&2
            exit 2
            ;;
    esac
done

prompt_if_empty() {
    local var_name="$1"
    local question="$2"
    local default="${3:-}"
    if [[ -z "${!var_name}" ]]; then
        if [[ -n "$default" ]]; then
            read -r -p "$question [$default]: " val
            val="${val:-$default}"
        else
            read -r -p "$question: " val
        fi
        printf -v "$var_name" '%s' "$val"
    fi
}

prompt_if_empty OWNER             "GitHub owner (user/org)"
prompt_if_empty REPO              "Repository name"
prompt_if_empty PROJECT_NAME      "Human-readable project name"
prompt_if_empty DEFAULT_BRANCH    "Default branch"        "main"
prompt_if_empty BRANCH_PREFIX     "Task branch prefix"    "task"
prompt_if_empty BUILD_CMD         "Build / verify command"
prompt_if_empty TEST_CMD          "Test / lint command (empty で空のまま可)"
prompt_if_empty SCREENSHOT_DIR    "Screenshot directory"  "docs/screenshots"
prompt_if_empty APP_BINARY_HINT   "App binary hint (起動コマンド例)"
prompt_if_empty CODEX_BOT_LOGIN   "Codex bot login"       "chatgpt-codex-connector[bot]"
prompt_if_empty REVIEW_LANG       "Review language"       "日本語"

# ---- 置換対象ファイルを収集 -------------------------------------------------

SELF="$(realpath "$0")"

mapfile -t FILES < <(
    find . \
        \( -path ./.git -o -path ./.git/'*' \) -prune -o \
        -type f \
        \( -name '*.md' -o -name '*.json' -o -name '*.sh' -o -name '*.ps1' \) \
        -print
)

# ---- 置換実行 ---------------------------------------------------------------

replace_in_file() {
    local f="$1"
    # スクリプト自身は触らない
    if [[ "$(realpath "$f")" == "$SELF" ]]; then
        return 0
    fi
    sed -i.bak \
        -e "s|{{OWNER}}|${OWNER}|g" \
        -e "s|{{REPO}}|${REPO}|g" \
        -e "s|{{PROJECT_NAME}}|${PROJECT_NAME}|g" \
        -e "s|{{DEFAULT_BRANCH}}|${DEFAULT_BRANCH}|g" \
        -e "s|{{BRANCH_PREFIX}}|${BRANCH_PREFIX}|g" \
        -e "s|{{BUILD_CMD}}|${BUILD_CMD}|g" \
        -e "s|{{TEST_CMD}}|${TEST_CMD}|g" \
        -e "s|{{SCREENSHOT_DIR}}|${SCREENSHOT_DIR}|g" \
        -e "s|{{APP_BINARY_HINT}}|${APP_BINARY_HINT}|g" \
        -e "s|{{CODEX_BOT_LOGIN}}|${CODEX_BOT_LOGIN}|g" \
        -e "s|{{REVIEW_LANG}}|${REVIEW_LANG}|g" \
        "$f"
}

for f in "${FILES[@]}"; do
    replace_in_file "$f"
done

# *.bak の掃除
find . -name '*.bak' -not -path './.git/*' -delete

# ---- 残存 placeholder の検査 ------------------------------------------------

echo ""
echo "--- placeholder 残存検査 ---"
LEFTOVER=$(grep -rln '{{[A-Z_]\+}}' \
    --include='*.md' --include='*.json' --include='*.sh' --include='*.ps1' \
    . 2>/dev/null | grep -v "^${SELF}$" || true)

if [[ -n "$LEFTOVER" ]]; then
    echo "⚠️  以下のファイルに置換されていない placeholder が残っています："
    echo "$LEFTOVER"
    echo ""
    echo "（{{TASK_LIST_PLACEHOLDER}} のように利用者が手で埋める前提の placeholder は"
    echo "  そのままで OK。コメントになっているはずです。docs/customize.md を参照。）"
else
    echo "✅ 全 placeholder が置換されました。"
fi

# ---- 仕上げ -----------------------------------------------------------------

echo ""
echo "--- 仕上げ ---"
if [[ -f CLAUDE.md.template && ! -f CLAUDE.md ]]; then
    read -r -p "CLAUDE.md.template を CLAUDE.md にリネームしますか？ [Y/n]: " ans
    if [[ "${ans:-Y}" =~ ^[Yy]$ ]]; then
        mv CLAUDE.md.template CLAUDE.md
        echo "  → mv CLAUDE.md.template CLAUDE.md"
    fi
fi

echo ""
echo "完了。次のステップ："
echo "  1. CLAUDE.md / README.md にプロジェクト固有の概要・アーキテクチャを追記"
echo "  2. docs/customize.md を読んで、未置換 placeholder（{{TASK_LIST_PLACEHOLDER}} 等）を手で埋める"
echo "  3. git add -A && git commit -m 'Apply template values'"
echo "  4. ChatGPT Codex（Web）で GitHub OAuth → 本リポジトリを連携"
