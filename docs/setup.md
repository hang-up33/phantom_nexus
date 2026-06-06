# Setup — 初回セットアップ詳細

このドキュメントは [README.md](../README.md) の「Use this template したらやること」を補足する。

## 前提

- GitHub アカウント（本テンプレを派生させる先のリポジトリを作成できる）
- ChatGPT Plus / Pro / Team アカウント（Codex Web 版を使うため）
- ローカルに `git` と `gh` CLI
- bash 4 以上（macOS デフォルトの 3.2 でも `apply-template.sh` は動くが `mapfile` の代替実装が要るので、できれば `brew install bash` 推奨）

## 詳細手順

### 1. テンプレ派生

GitHub の本テンプレリポジトリページで **"Use this template" → "Create a new repository"** をクリック。
- Owner：自分の user / org
- Repository name：新プロジェクト名
- Public / Private：任意
- "Include all branches" は OFF（main のみで OK）

### 2. clone と placeholder 置換

```sh
git clone git@github.com:<your-org>/<new-repo>.git
cd <new-repo>
bash scripts/apply-template.sh
```

対話で以下を聞かれる：

| 質問 | 例 | デフォルト |
|---|---|---|
| GitHub owner | `your-handle` | （なし） |
| Repository name | `<new-repo>` | （なし） |
| Human-readable project name | `My New Project` | （なし） |
| Default branch | `main` | `main` |
| Task branch prefix | `task` / `feature` | `task` |
| Build / verify command | `npm run build` / `cmake --build build` | （なし） |
| Test / lint command | `npm test` | （空可） |
| Screenshot directory | `docs/screenshots` | `docs/screenshots` |
| App binary hint | `./build/app` 等の起動説明 | （なし） |
| Codex bot login | `chatgpt-codex-connector[bot]` | 同左 |
| Review language | `日本語` / `English` | `日本語` |

完了後、placeholder 残存検査が走り、すべて置換されていれば `✅ 全 placeholder が置換されました。` と出る。

### 3. CLAUDE.md の埋め込み

`CLAUDE.md.template` → `CLAUDE.md` にリネームされた後、HTML コメント（`<!-- ... -->`）で示された箇所を実プロジェクトの内容に書き換える：

- プロジェクト概要
- 現在のフェーズ
- ビルドと実行
- アーキテクチャ
- 「利用可能なエージェント」表（プロジェクト固有エージェントを足す）
- Codex 連携の重点観点（AGENTS.md 側にも同じセクションあり）
- 「ビルド / 環境の罠」（最初は空、kaizen で蓄積していく）

### 4. ChatGPT Codex 連携

1. [chatgpt.com/codex](https://chatgpt.com/codex) を開く
2. 右上アバター → **Settings** → **Connectors / GitHub**
3. GitHub OAuth → 新リポジトリへの権限を付与
4. GitHub 側の **Settings → Installed Apps** に "ChatGPT" / "OpenAI Codex" 等が出ていれば成功

### 5. gh CLI 認証

```sh
gh auth status
```

未認証なら：

```sh
gh auth login
# → GitHub.com → HTTPS → ブラウザでログイン
```

### 6. 疎通確認（最初のダミー PR）

```sh
git checkout -b task/0-smoke
# 適当な変更（README に空行を足す等）
git add README.md
git commit -m "Task 0: smoke test"
git push -u origin task/0-smoke
gh pr create --fill
gh pr comment <PR番号> --body "@codex review"
```

2〜5 分で Codex が PR コメントで日本語のレビューを返せば成功。返ってこない場合は [troubleshooting](#troubleshooting) を参照。

### 7. （任意）Issue → PR 自動化を有効化

`.github/workflows/claude-issue-to-pr.yml` を有効化すると、Issue 起点で Claude が実装ブランチと PR を自動生成する。提出された PR は既存の Codex レビューループに自動で乗る。

1. **API キー登録**：Settings → Secrets and variables → Actions → New repository secret
   - Name: `ANTHROPIC_API_KEY`
   - Value: Anthropic Console で発行した API キー
2. **Actions 権限**：Settings → Actions → General
   - "Workflow permissions" を **Read and write permissions** に
   - **Allow GitHub Actions to create and approve pull requests** にチェック
3. **ラベル作成**：Issues → Labels → New label で `claude` を追加（任意の色）
4. **動作確認**：テスト Issue を立てて `claude` ラベルを付ける。または本文に `@claude ...` と書く。Actions タブで `Claude Issue → PR` が走り、数分で実装ブランチと PR が現れる。

トリガーの選択肢：

| 操作 | 反応 |
|---|---|
| Issue に `claude` ラベルを付ける | 起動 |
| 新規 Issue 本文に `@claude` を含める | 起動 |
| 既存 Issue / PR のコメントに `@claude ...` | 起動（追加指示として渡る） |
| ラベルなし・@claude 言及なし | 何もしない |

カスタマイズしたい点：

- **自動実行範囲を絞りたい** → `if:` 条件にラベル名を増やす、特定ユーザーのみに制限する、等
- **実行コマンドを絞りたい** → `claude_args` の `--allowedTools` を編集（デフォルトは多くの言語のビルド/テストツールを許可）
- **PR 提出後に Codex レビューを自動依頼しない** → `prompt:` 内の手順 6 を削除

## Troubleshooting

### Codex が無反応（30 分経っても）

- chatgpt.com の Codex Settings で GitHub 連携が切れていないか確認
- 該当リポジトリへの権限が付与されているか（GitHub Installed Apps）
- Codex の自動レビュー設定が ON か（Codex Web 上の "Auto-review pull requests" 等）
- `@codex review` コメントを再度投稿してみる

### `apply-template.sh` が `mapfile: command not found`

- macOS デフォルトの bash 3.2 で起きる。`brew install bash` して `/opt/homebrew/bin/bash scripts/apply-template.sh` で実行する

### placeholder が一部置換されない

- `{{TASK_LIST_PLACEHOLDER}}` のような **意図的に残してある placeholder** はコメント内に置かれており、利用者が手で内容を埋める。詳細は [customize.md](customize.md) を参照

### Issue → PR ワークフローが起動しない

- Actions タブで該当 run があるかまず確認。run が無い場合は `claude` ラベルの綴りが完全一致しているか確認（大文字小文字も区別）
- run があるのに失敗している場合は、`ANTHROPIC_API_KEY` の secret 登録漏れ・残高切れ・Actions の write 権限不足のいずれかが多い
- PR 作成だけ失敗する場合は Settings → Actions → General の "Allow GitHub Actions to create and approve pull requests" を再確認
