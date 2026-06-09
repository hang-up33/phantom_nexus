# AGENTS.md

このファイルは **ChatGPT Codex（GitHub App / Codex CLI）** に対するリポジトリ共通の指示です。
リポジトリの開発指針・アーキテクチャ・ビルド手順・ブランチ運用は [CLAUDE.md](CLAUDE.md) と [README.md](README.md) を参照してください。

---

## Review guidelines

> 本リポジトリのレビューは **Codex ＋ CodeRabbit ＋ Claude（CI / fresh context）** の 3 系統で多重化しています。Codex は下記の「重点的に見てほしい観点」（プロジェクト固有の不変条件）を優先し、汎用的な lint/スタイル指摘は控えめで構いません（重複は許容）。

Codex が PR レビューを行う際は、以下のルールに従ってください。

### 言語

- **レビューコメントはすべて日本語で記述してください**（コード識別子・ファイルパス・引用ブロックは英語のままで構いません）。
- PR サマリ・指摘・サジェスト本文・コミット提案など、Codex が生成する全ての自然言語出力を日本語にしてください。

### 指摘の書き方

- 指摘ごとに **「何が」「なぜ問題か」「どう直すべきか」** の 3 点を簡潔に書いてください。
- 該当ファイル・行番号を明示してください（GitHub の suggestion ブロックを使える場合は積極的に使用）。
- 重要度を `[Blocker]` / `[Major]` / `[Minor]` / `[Nit]` のいずれかでラベル付けしてください。

### スコープ

- 本リポジトリの現フェーズと禁則事項は [CLAUDE.md](CLAUDE.md) の「現在のフェーズ」「Must Always / Must Never」を参照してください。設計書の優先順序を逸脱する大規模リファクタ提案は控えめにし、必要であれば `[Future]` ラベルで分離してください。
- フォルダ構成変更・代替ビルドシステム導入など [CLAUDE.md](CLAUDE.md) の Must Never に触れる変更は提案しないでください。

### 重点的に見てほしい観点

- **データモデルの単一の真実**：キャラ・技・ステージ等のデータ型と JSON I/O が `Shared/`（`Schema`/`Types`/`Constants`）に集約されているか。`GameRuntime` / `Battle` が直接 JSON を読んでいたり、データ型が二重定義されていないか。
- **当たり判定のフレーム正しさ**：攻撃の startup / active / recovery フレーム、hitbox × hurtbox の AABB 判定、pushbox の押し合い解決が 1 フレーム単位で正しいか（多段ヒット・判定の出っぱなし・すり抜け）。
- **データ駆動の後方互換**：JSON スキーマ（`Shared/Schema`・`docs/DataFormat.md`）の変更が既存キャラ JSON を壊さないか。必須/任意フィールドとデフォルト値の扱いが妥当か。
- **Must Never の再導入**：フォルダ構成変更（root に増やしてよいのは wrapper / シム 3 点 ＋ VCS メタデータ `.gitignore` / `.gitattributes` のみ。それ以外のソース/アセット/ビルドロジックの実体を root に置いていないか）・既存コード削除・無断ライブラリ追加（特に JSON/YAML パーサ）・MUGEN 素材や名称の流用が混入していないか。
- **既知のビルド/環境の罠の再導入**：[CLAUDE.md](CLAUDE.md)「ビルド / 環境の罠」を参照。

---

## Codebase context

ビルド・実行・アーキテクチャの詳細は [CLAUDE.md](CLAUDE.md) を参照してください。Codex CLI が自律的にコードを編集する場合も、CLAUDE.md の「Must Always / Must Never」を遵守してください。
