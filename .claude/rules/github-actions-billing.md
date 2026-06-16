# GitHub Actions の課金注意（public→private 化時）

⚠️ **必ず確認**：本リポジトリ（`hang-up33/phantom_nexus`）は **public** のため GitHub Actions の標準ランナーが無料で、`claude-review.yml` 等のワークフローは分数課金されない（消費するのは Claude サブスク枠のみ）。

**将来 public → private に切り替えると、GitHub Actions が無料枠を超えた分は課金対象になり得る。** 可視性を private に変更する／その話題が出たときは、この点を **必ずユーザーに伝える**こと。

- ユーザーは GitHub Actions の金銭課金に敏感（過去にコスト削減で `claude-review.yml` の自動発火を停止した経緯がある）。
- private 化する場合は、Actions ワークフローの自動発火の停止／必要時だけ手動起動（`workflow_dispatch`）への切替も提案する。
- レビュー運用（CodeRabbit 自動・CI Claude 手動・Codex 最終 1 回）は CLAUDE.md「コードレビューの担当分け」を参照。
