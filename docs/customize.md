# Customize — placeholder 一覧と書き換え指針

`scripts/apply-template.sh` で機械的に置換される placeholder と、利用者が手で埋める placeholder の一覧。

## 機械置換される placeholder（apply-template.sh が処理）

| Placeholder | 意味 | 例 |
|---|---|---|
| `{{OWNER}}` | GitHub user / org | `hang-up33` |
| `{{REPO}}` | リポジトリ名 | `my-new-project` |
| `{{PROJECT_NAME}}` | 人間可読プロジェクト名 | `My New Project` |
| `{{DEFAULT_BRANCH}}` | 既定ブランチ | `main` |
| `{{BRANCH_PREFIX}}` | タスクブランチ接頭辞 | `task` / `feature` |
| `{{BUILD_CMD}}` | ビルド検証コマンド | `npm run build` / `cmake --build build` |
| `{{TEST_CMD}}` | テスト / lint コマンド | `npm test` |
| `{{SCREENSHOT_DIR}}` | スクリーンショット置き場 | `docs/screenshots` |
| `{{APP_BINARY_HINT}}` | アプリ起動コマンド例 | `./build/app` |
| `{{CODEX_BOT_LOGIN}}` | Codex bot のログイン名 | `chatgpt-codex-connector[bot]` |
| `{{REVIEW_LANG}}` | Codex のレビュー言語 | `日本語` / `English` |

これらは `apply-template.sh` を一度走らせれば全ファイルで `sed` 置換される。対象拡張子：`*.md` / `*.json` / `*.sh` / `*.ps1`。

## 手で埋める placeholder（apply-template.sh では置換されない）

### `{{TASK_LIST_PLACEHOLDER}}`

**場所**：`.claude/skills/next-task/SKILL.md`

HTML / Markdown コメント内に置かれている。プロジェクト固有のタスク順序や完了基準があれば、コメントごと書き換える。

```md
<!-- {{TASK_LIST_PLACEHOLDER}}
  ここを実プロジェクトのタスク順に書き換える。例：
    1. プロジェクト初期化 ✅
    2. データモデル定義
    3. ログイン画面
    ...
-->
```

タスク順序が動的（バックログから随時引く運用）なら、このコメントごと削除して「README の進捗表を参照」と書き換えてもよい。

### CLAUDE.md.template 内の HTML コメント

`CLAUDE.md.template` → `CLAUDE.md` リネーム後、以下のセクションを実プロジェクトの内容に書き換える：

- `## プロジェクト概要`
- `**現在のフェーズ**`
- `## ビルドと実行`
- `## アーキテクチャ`
- `## 利用可能なエージェント` の表（プロジェクト固有エージェントを足す）
- `## ビルド / 環境の罠`（最初は空。kaizen で蓄積）
- `## 対象プラットフォーム` / `## Claude Code on the web 利用可否`

各セクションには `<!-- ... -->` で指示コメントが入っているので、それを読みつつ書き換える。

### AGENTS.md の「重点的に見てほしい観点」

`AGENTS.md` の `## Review guidelines` → `### 重点的に見てほしい観点` セクションには HTML コメントで例示だけ書かれている。プロジェクト特有のレビュー観点（公開 API 互換性、並行処理、データモデルの単一の真実 等）を 3〜5 個に絞って書く。

### `build-error-resolver` の罠リスト

**場所**：`.claude/agents/build-error-resolver.md`

最初は空。`kaizen-close` でビルド系の罠を発見したら、ここに「症状 / 原因 / 対処」の 3 行で追記していく。蓄積すると本エージェントの初動が早くなる。

## 不要な資産の削除

### `.github/ISSUE_TEMPLATE/`

Issue を使わないプロジェクトでは丸ごと削除して可。

### `scripts/capture-app-window.sh`

GUI スクリーンショット運用をしないプロジェクト（CLI ツール / バックエンドサービス / Web API 等）では削除可。その場合、`CLAUDE.md` の「動作証跡スクリーンショット運用」セクションも削除する。

### フレームワーク固有のスキル / エージェントを追加したい場合

テンプレ本体は言語 / フレームワーク非依存に保つ方針のため、特定スタック向け（Next.js, Rails, Django, Flutter 等）のスキルやビルドエラー解決エージェントは派生プロジェクト側の `.claude/skills/` / `.claude/agents/` に追加していく。テンプレ本体には逆流させない（[README.md](../README.md) メンテナンス / バージョニング セクション参照）。

## Placeholder 追加の運用

新しい placeholder を本テンプレに追加する場合（テンプレ自体を改善するときの話）：

1. テンプレ内のテキストに `{{NEW_PLACEHOLDER}}` を埋め込む
2. `scripts/apply-template.sh` に：
   - `NEW_PLACEHOLDER=""` の宣言を追加
   - `--new-placeholder` の引数パースを追加
   - `prompt_if_empty NEW_PLACEHOLDER "..."` を追加
   - sed の `-e` に `"s|{{NEW_PLACEHOLDER}}|${NEW_PLACEHOLDER}|g"` を追加
3. 本ファイルの上記表に追記
4. `docs/setup.md` の対話表にも追記

## Changelog

テンプレ自体の破壊的変更（placeholder の rename 等）が発生した場合は、ここに「いつ / 何が変わった / 既存利用者が手で当てるべき差分」を時系列で追記する。

<!-- 例：
- 2026-06-01: `{{TEST_CMD}}` を新規追加。既存派生プロジェクトで使いたい場合は CLAUDE.md / next-task SKILL に `{{TEST_CMD}}` を埋め、apply-template.sh に対応行を足す。
-->
