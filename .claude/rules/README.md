# .claude/rules — プロジェクト知見の置き場

CLAUDE.md の肥大化を避けるため、**プロジェクト固有の知見・罠・運用ルールはこのフォルダにトピック別の Markdown で置く**。CLAUDE.md には「コア原則・フォルダ構成・Must Always/Never」など骨子だけを残し、詳細な知見はここへ集約する。

## 運用ルール（今後の知見は全てここへ）

- **新しく得た知見（罠・工夫・好み・運用ノウハウ）は CLAUDE.md でなくこのフォルダの該当トピックファイルへ追記する。** 該当トピックが無ければ新規ファイルを作る（`<topic>.md`・小文字ハイフン区切り）。
- 各ファイルは 1 トピック。冒頭に何の知見かを 1 行で書く。
- **Claude Code は `.claude/rules/` を自動ロードしない**ため、常に効かせたい rule は CLAUDE.md 末尾の「プロジェクト知見（.claude/rules/）」セクションから `@.claude/rules/<file>.md` で import する（新規ファイルを足したら import 行も 1 行追加する）。
- [kaizen-close](../skills/kaizen-close/SKILL.md) スキルはタスク完了時の知見をこのフォルダへ反映する（CLAUDE.md への直書きはしない）。

## 現在のファイル

- [github-actions-billing.md](github-actions-billing.md) — public→private 化時の GitHub Actions 課金注意（CLAUDE.md から import）
- [macos-screenshot-capture.md](macos-screenshot-capture.md) — macOS でのゲーム起動・スクショ撮影（Xvfb 不要のネイティブ撮影。参照系なので import はしない）
