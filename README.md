# Claude Codex Ops Template

「**実装は Claude Code、レビューは ChatGPT Codex GitHub App**」運用を新規プロジェクトで即開始するための GitHub Template Repository。

`.claude/` 配下のスキル / エージェント、`AGENTS.md`、PR・ブランチ・スクリーンショット運用ルール、汎用補助スクリプトを **言語 / フレームワーク非依存** にまとめたもの。Web / モバイル / バックエンド / CLI / GUI など、`{{BUILD_CMD}}` で完了基準を表現できる任意のプロジェクトに適用できる。

---

## Use this template したらやること（5 分）

1. **GitHub UI** で "Use this template" → 新規リポジトリを作成
2. **ローカルに clone**：
   ```sh
   git clone git@github.com:<your-org>/<new-repo>.git
   cd <new-repo>
   ```
3. **placeholder を一括置換**：
   ```sh
   bash scripts/apply-template.sh
   ```
   対話で `{{OWNER}}` / `{{REPO}}` / `{{BUILD_CMD}}` 等を聞かれるので答える。`{{BUILD_CMD}}` には言語問わず「完了基準にしたいコマンド」を入れる（`npm run build` / `cargo build` / `go build ./...` / `pytest` / `cmake --build build` 等）。完了後、`CLAUDE.md.template` → `CLAUDE.md` のリネームを促されるので Yes。
4. **`CLAUDE.md` を埋める**：プロジェクト概要・アーキテクチャ・ビルド手順を追記（テンプレ骨格にコメントで指示が入っている）。
5. **ChatGPT Codex Web で連携**：[chatgpt.com/codex](https://chatgpt.com/codex) → Settings → GitHub OAuth → 新リポジトリを連携。
6. **gh CLI 認証**：`gh auth status` で OK か確認。
7. **最初のダミー PR で疎通確認**：適当な変更を `task/0-smoke` ブランチで PR にして、Codex が日本語コメントを返すか確認。
8. **（任意）Issue → PR 自動化を有効化**：
   - Settings → Secrets and variables → Actions に `ANTHROPIC_API_KEY` を登録
   - Settings → Actions → General で "Read and write permissions" と "Allow GitHub Actions to create and approve pull requests" を ON
   - ラベル `claude` を作成
   - Issue に `claude` ラベルを付けるか本文に `@claude` を含めると [.github/workflows/claude-issue-to-pr.yml](.github/workflows/claude-issue-to-pr.yml) が起動し、Claude が実装ブランチと PR を自動生成する。PR 提出後は既存の Codex レビューループに乗る。

---

## 含まれる資産

```
.claude/
├── settings.json                       # autoCompactEnabled のみ
├── skills/
│   ├── kaizen-close/SKILL.md           # タスク完了直前に kaizen を反映
│   ├── codex-pr/SKILL.md               # PR 作成 + Codex 自走レビューループ
│   └── next-task/SKILL.md              # 次タスク実装の標準ワークフロー
└── agents/
    └── build-error-resolver.md         # ビルド/依存エラー解決の汎用エージェント

.github/
├── PULL_REQUEST_TEMPLATE.md            # PR 本文の雛形（Summary / 変更点 / Test plan / Codex 向け補足）
├── ISSUE_TEMPLATE/                     # 任意
└── workflows/
    └── claude-issue-to-pr.yml          # `claude` ラベル / @claude メンションで Issue→PR を自動化

scripts/
├── apply-template.sh                   # placeholder 一括置換
└── capture-app-window.sh               # macOS のウィンドウキャプチャ（GUI アプリのスクショ用 / 不要なら削除可）

docs/
├── setup.md                            # 初回セットアップ詳細
├── workflow.md                         # 開発フロー（task ブランチ → PR → 自走レビュー）
└── customize.md                        # placeholder 一覧と書き換え指針

CLAUDE.md.template                      # Claude Code への指針の雛形
AGENTS.md                               # Codex GitHub App / Codex CLI への指示
```

---

## 主要ワークフロー

```
ユーザー: 次のタスクを進めて
   ↓
Claude (next-task SKILL)
   ↓ ブランチ作成 → 実装 → ビルド検証
   ↓
Claude (kaizen-close SKILL)
   ↓ 学びを CLAUDE.md / README に反映
   ↓
Claude (codex-pr SKILL)
   ↓ commit → push → gh pr create → @codex review
   ↓
ChatGPT Codex (PR コメントで日本語レビューを自動投稿)
   ↓
Claude (codex-pr SKILL の自走ループ)
   ↓ 指摘修正コミット → @codex review 再依頼 → 指摘 0 件まで繰り返し
   ↓
ユーザー: Codex のクリーン後にマージ
```

---

## カスタマイズ

- **placeholder 一覧**は [docs/customize.md](docs/customize.md) 参照。
- **`build-error-resolver` の「既知の罠リスト」** は最初は空。プロジェクトで踏んだ罠を `kaizen-close` 経由で追記していくと、本エージェントが早く解決できるようになる。
- **CLI / バックエンド等で GUI スクショ運用が不要な場合**は `scripts/capture-app-window.sh` を削除し、`CLAUDE.md` の「動作証跡スクリーンショット運用」セクションも削除する。
- **フレームワーク固有のスキル / エージェント**（例：CMake ビルドの罠、Next.js の SSR ハンドリング、Rails マイグレーション手順 等）はテンプレ本体には含めず、`apply-template.sh` 適用後に各派生プロジェクトの `.claude/` 配下に追加していく。
- **CI を足したい場合**は `.github/workflows/` を任意に追加。本テンプレ自体は CI を強制しない。

---

## メンテナンス / バージョニング

- 本テンプレリポジトリは **タグでリリースを切る**（例：`v0.1.0`, `v0.2.0`）
- 大きな破壊的変更（placeholder の rename 等）は minor バージョンを上げる
- 汎用化に値する kaizen / 罠 / ワークフロー改善が見つかった時は、`task/sync-<topic>` ブランチで反映し、影響を `docs/customize.md` の changelog に追記する（フレームワーク固有の例示やコマンドは持ち込まず、リテラル値は placeholder 化する）
- 既存の派生プロジェクトは "Use this template" 後にテンプレと切り離されるため、新版を取り込みたい場合は手動で差分を当てる（テンプレ更新の自動同期機構は GitHub には無いため）

---

## 関連リンク

- ChatGPT Codex：[chatgpt.com/codex](https://chatgpt.com/codex)
- Claude Code：[claude.com/claude-code](https://claude.com/claude-code)
