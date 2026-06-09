# Workflow — 開発フローの全体像

「実装は Claude / レビューは Codex」を回す 1 ループの詳細。

## 1 タスク 1 ブランチ 1 PR

```
main
 ├── task/1-init           (PR #1 — マージ済み)
 ├── task/2-login          (PR #2 — マージ済み)
 ├── task/3-dashboard      (PR #3 — レビュー中)
 └── ...
```

- 1 タスク = 1 ブランチ = 1 PR
- すべて `main`（または設定した `main`）から派生
- マージは **Squash and merge**（main 上で 1 タスク = 1 コミット）

## 全体フロー

```
[ユーザー]                  [Claude Code]                  [GitHub]              [Codex]
    │                            │                             │                    │
    │ "次のタスクを進めて"       │                             │                    │
    │───────────────────────────>│                             │                    │
    │                            │ next-task SKILL 起動        │                    │
    │                            │  ├─ 進捗確認                │                    │
    │                            │  ├─ ブランチ作成            │                    │
    │                            │  ├─ 実装                    │                    │
    │                            │  ├─ ビルド検証              │                    │
    │                            │  ├─ 動作確認 + スクショ     │                    │
    │                            │  ├─ README 進捗更新         │                    │
    │                            │  └─ kaizen-close            │                    │
    │                            │                             │                    │
    │                            │ self-review SKILL 起動      │                    │
    │                            │  └─ 差分を別コンテキストで  │                    │
    │                            │     セルフレビュー(self-gate)│                   │
    │                            │                             │                    │
    │                            │ codex-pr SKILL 起動         │                    │
    │                            │  ├─ commit                  │                    │
    │                            │  ├─ push ──────────────────>│                    │
    │                            │  ├─ gh pr create (ready)────>│                    │
    │                            │  └─ @codex review コメント─>│───────────────────>│
    │                            │                             │                    │ レビュー実行
    │                            │ sleep 180-240               │                    │ (1〜5 分)
    │                            │                             │<───────────────────│ コメント投稿
    │                            │                             │                    │
    │                            │<─ レビュー取得（reviews/    │                    │
    │                            │   review comments/issue     │                    │
    │                            │   comments API）            │                    │
    │                            │                             │                    │
    │                            │ 判定（state 優先）          │                    │
    │                            │  ├─ 指摘あり                │                    │
    │                            │  │   └─ 修正コミット → push─>│                    │
    │                            │  │      └─ @codex review再依頼─────────────────>│
    │                            │  │         (ループ)                              │
    │                            │  └─ クリーン                │                    │
    │                            │                             │                    │
    │ "Codex 指摘 N 件を         │                             │                    │
    │  M ラウンドで解消"         │                             │                    │
    │<───────────────────────────│                             │                    │
    │                            │                             │                    │
    │ ユーザーが Squash merge ──>│                             │                    │
    │                            │                             │                    │
```

## 各スキルの責任範囲

| スキル | 開始条件 | 終了条件 |
|---|---|---|
| [next-task](../.claude/skills/next-task/SKILL.md) | ユーザーが「次のタスク」を指示 | `codex-pr` への引き継ぎ完了 |
| [self-review](../.claude/skills/self-review/SKILL.md) | push 直前（kaizen-close 完了後） | 差分の別コンテキスト点検と明白なミスの修正完了 |
| [codex-pr](../.claude/skills/codex-pr/SKILL.md) | タスク実装・ビルド検証・kaizen-close・self-review 完了後 | Codex レビュー指摘 0 件 + PR URL 提示 |
| [kaizen-close](../.claude/skills/kaizen-close/SKILL.md) | 任意のタスク完了直前 | 反映先の選定と追記、または「追加学習なし」明示 |

## ブランチ命名規約

| 用途 | 命名 | 例 |
|---|---|---|
| 通常タスク | `task/<N>-<短い名>` | `task/3-dashboard` |
| ドキュメント / 設定 | `docs/<短い名>` | `docs/codex-auto-review` |
| バグ修正（任意） | `fix/<短い名>` | `fix/login-validation` |

`<短い名>` は kebab-case、3〜5 単語まで。

## マージ戦略

- **Squash and merge**：1 タスク = main 上の 1 コミット
- マージは **ユーザー** が実施（Codex のクリーン後）
- Claude は **マージしない**（codex-pr SKILL の Must Never に明記）

## 自走ループの判定基準

Codex は指摘の有無で投稿先 API が変わるため、3 種類すべて取得して判定する：

| API | 用途 |
|---|---|
| `pulls/<N>/reviews` | review summary（state: `CHANGES_REQUESTED` / `COMMENTED` / `APPROVED`） |
| `pulls/<N>/comments` | inline コメント本体 |
| `issues/<N>/comments` | PR 会話タブ。**クリーン文言（"Didn't find any major issues"）はここに来る** |

判定優先順位：

1. `state == CHANGES_REQUESTED` → 必ず修正
2. inline コメント 1 件以上（HEAD SHA で絞った後） → 修正
3. 本文に `[Major]` / `[Minor]` / `P0`〜`P3` などの指摘 badge → 修正
4. issue comments / reviews に「クリーン文言」 → ループ脱出
5. どのシグナルも未到着 → 待機継続

詳細は [codex-pr SKILL 手順 7](../.claude/skills/codex-pr/SKILL.md) を参照。

## 動作証跡（スクリーンショット）

GUI 変化がある PR には必ず添付。本プロジェクトの既定は **Windows**（`.ps1`）。詳細・macOS 手順は [CLAUDE.md](../CLAUDE.md)「動作証跡スクリーンショット運用」を参照：

```powershell
Start-Process -FilePath ".\gradlew.bat" -ArgumentList "run"
Start-Sleep -Seconds 8
powershell -ExecutionPolicy Bypass -File scripts/capture-app-window.ps1 -WindowTitle "Phantom Nexus" -OutPath "docs/screenshots/<N>-<短い名>.png"
```

（macOS では `scripts/capture-app-window.sh <process-name> docs/screenshots/<N>-<短い名>.png` を使う。`.sh` は macOS 専用で Windows では動作しない）

PR 本文では **commit SHA 固定の raw URL** で参照（ブランチ削除後も生きる）。
