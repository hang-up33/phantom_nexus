# CLAUDE.md

このファイルは、本リポジトリで作業する Claude Code（claude.ai/code）への指針です。
ユーザーは日本人のため、応答・ドキュメント・コミットメッセージは日本語を基本とします（コード識別子・ファイルパス・JSON キーは英語可）。

---

## プロジェクト概要

**Phantom Nexus** は、MUGEN のようにキャラクター・ステージ・技・当たり判定・AI を **外部データ（JSON）** として追加・編集できる、拡張性の高い 2D 格闘ゲーム基盤。固定キャラ制ではなく「ユーザーが独自キャラを足せる格闘ゲームエンジン」を目指す。技術スタックは Java / LibGDX / Gradle、対象は Windows PC（将来 Linux・macOS）。詳細は [README.md](README.md) と第一設計書を参照。

**現在のフェーズ**：MVP 実装中（1対1対戦・HP・攻撃/食らい判定・外部 JSON 読込までに集中。コマンド技/AI/エディタは MVP 後に段階追加）

---

## コア原則

1. **1 タスクずつ完結** — 設計書のタスク順を厳守し、各タスクは `./gradlew build` 成功を完了基準とする。
2. **データモデルの単一の真実** — キャラ・技・ステージ等のデータ型と JSON I/O は **`Shared/`（`Schema`/`Types`/`Constants`）にのみ**実装する。`GameRuntime` や `Battle` から直接 JSON を読まない。
3. **継続的改善（kaizen）の遵守** — タスク完了報告の前に、得た知見を CLAUDE.md / README / メモリ / 設定のいずれかに反映する（[kaizen-close](.claude/skills/kaizen-close/SKILL.md) スキルで標準化）。
4. **既存資産の保護** — フォルダ構成変更禁止・既存コード削除禁止（例外は明示されたプレースホルダーのみ）。
5. **計画してから実装** — 複雑なタスクはエージェントに計画させてから手を動かす。
6. **データ/戦闘仕様を変えたら必ず docs を更新** — データ仕様変更時は [docs/DataFormat.md](docs/DataFormat.md)、戦闘仕様変更時は [docs/BattleSystem.md](docs/BattleSystem.md) を同じ PR 内で更新する。

---

## ビルドと実行

```sh
./gradlew build      # 完了基準（コンパイル + テスト）
./gradlew test       # テストのみ
./gradlew run        # デスクトップ（LWJGL3）でゲームを起動
```

- JDK は Gradle toolchain で Java 17 を自動取得する設定（手元に JDK 無しでも可。要ネットワーク）。
- Windows では `gradlew.bat` を使用可（`./gradlew` は Git Bash / PowerShell から）。

---

## アーキテクチャ

単一 Gradle モジュール（desktop-only）。**設計書のフォルダ構成を唯一の真実**とし、Gradle はソース/アセットのパスをそのフォルダに向ける。

| ディレクトリ | 役割 |
|---|---|
| `GameRuntime/Core` | ゲームループ・`ApplicationListener`・デスクトップ起動（Lwjgl3） |
| `GameRuntime/Rendering` | `SpriteBatch`・カメラ・スプライト描画・アニメーション |
| `GameRuntime/Input` | キーボード入力の抽象化（`Gdx.input` のラップ） |
| `GameRuntime/Battle` | HP・ゲージ・ラウンド・タイマー・勝敗・攻撃ステート（startup/active/recovery）・ダメージ/のけぞり |
| `GameRuntime/Debug` | 当たり判定オーバーレイ・フレーム情報・入力ログ（トグル表示） |
| `Shared/Schema` | **データ I/O の単一の真実**。JSON ↔ Types のローダ + バリデーション |
| `Shared/Types` | `Character`・`Move`・`Hitbox`・`Hurtbox`・`PushBox`・`Stage`・`BattleRules` の POJO |
| `Shared/Constants` | 画面サイズ・物理定数・フレームレート・レイヤ順 |
| `Assets/{Characters,Stages,Sounds,Effects}` | 実行時リソース（外部キャラ JSON はここ） |
| `Tools/{CharacterViewer,HitboxEditor}` | 開発補助（将来。MVP では雛形のみ） |
| `Infra/Build` | Gradle ビルドロジックの実体（`build.gradle`・依存・toolchain・packaging） |
| `docs` | `DataFormat.md`・`BattleSystem.md`・`screenshots/`（テンプレ既存の `docs/` を流用。Windows の大文字小文字非区別により設計書の `Docs/` と同一フォルダになるため小文字で統一） |

### Gradle 配置に関する重要な前提（設計書フォルダ厳守の唯一の譲歩）

すべてのソース/アセット/ドキュメントは設計書フォルダに収める。ビルドの**実体ロジックは `Infra/Build/`** に置く。ただし Gradle ラッパー（`gradlew`・`gradlew.bat`・`gradle/wrapper/`）と**最小のシム `settings.gradle` / `build.gradle`** は、Gradle と IDE が root を認識するために root 配置が必須なため root に置き、実体は `apply from: 'Infra/Build/build.gradle'` で委譲する。これがフォルダ構成厳守に対する唯一の物理的譲歩。**この 3 点に加えて、リポジトリ運用上の VCS メタデータ（`.gitignore` / `.gitattributes`）のみ root 配置を許可する**（ソース/アセット/ビルド設定の実体ではなく、既存の `.github/` / `.claude/` と同カテゴリのメタ情報のため）。**それ以外（ソース/アセット/ビルドロジック等の実体）は root に増やさない。** なお `.gitignore` の `build/` は Windows の casing で `Infra/Build/` を巻き込まないよう `/build/` とルート固定し、`.gitattributes` は wrapper の改行（`gradlew`=LF / `*.bat`=CRLF / `*.jar`=binary）を固定する。

---

## 利用可能なエージェント

能動的に手を動かす作業は専門エージェントへ委譲する（Agent ツール経由）。

| エージェント | 役割 | 呼ぶタイミング |
|---|---|---|
| [build-error-resolver](.claude/agents/build-error-resolver.md) | ビルド / 依存エラー解決 | `./gradlew build` 失敗時 |

独立したタスクは並列起動して効率化する。

### コードレビューの担当分け

本プロジェクトの **コードレビューは ChatGPT Codex（GitHub App）** が担当する。Claude 側でレビュー専用エージェント / スキル（読み取り＋指摘のみのもの）は作らない。実装・ビルド修正・ドキュメント更新など **能動的な作業** に限ってエージェント化する。

---

## Codex 連携 / PR ワークフロー

本プロジェクトはレビューを Codex GitHub App に委ねるため、**タスクは PR 経由で main に取り込む**。Claude は以下のフローに従う。

Codex 向けの永続的な指示は **リポジトリ直下の [AGENTS.md](AGENTS.md)** に記述する（Codex 公式推奨形式）。レビュー言語ルールやスコープ制限を含み、Codex GitHub App / Codex CLI の両方がレビュー時に参照する。Codex 関連で「全 PR に効かせたい」指示が増えたら、CLAUDE.md ではなく AGENTS.md 側に追記すること。

### ブランチ命名

- 通常タスク：`task/<N>-<短い名>` 形式（例：`task/4-window-display`）。`<N>` は設計書タスク番号。
- ドキュメント・設定変更：`docs/<短い名>` 形式（例：`docs/bootstrap`）
- 1 単位 = 1 ブランチ = 1 PR。いずれも main から派生させる

### タスク完了時のフロー

1. 実装 → `./gradlew build` で緑を確認 → 動作確認
2. README の進捗表を更新
3. [kaizen-close](.claude/skills/kaizen-close/SKILL.md) スキルを実行
4. `task/<N>-<短い名>` ブランチを作成し、コミット
5. `gh pr create` で **ready-for-review** の PR を作成（draft にしない — Codex GitHub App に即レビューさせるため）
6. **`gh pr comment <N> --body "@codex review"` で明示的にレビュー依頼**（自動レビュー任せにせず毎回コメントで発火）
7. PR URL をユーザーに提示

詳細は [codex-pr](.claude/skills/codex-pr/SKILL.md) スキルを参照。

### PR タイトル / 本文

- **タイトル**：`<タスク識別子>: <短い説明>`（コミット先頭行と同じ）
- **本文**：Summary / 変更点 / Test plan / Codex 向け補足（不安な観点や重点的に見てほしい箇所）を含める。`.github/PULL_REQUEST_TEMPLATE.md` が雛形。

### マージ戦略

- **Squash and merge**。1 タスク = main 上の 1 コミットになるよう統合する。
- マージは **ユーザーが行う**（Codex のレビュー指摘を反映してから）。Claude は勝手にマージしない。

### Codex 指摘への対応

- Codex のレビュー結果は **PR コメントとして自動投稿される**。Claude は `codex-pr` スキルの「自走ループ」セクションに従い、`gh pr view <番号> --comments` と `gh api repos/hang-up33/phantom_nexus/pulls/<N>/comments` で内容を取得し、当該ブランチで対応コミットを追加 push する。
- 修正 push の **直後にも必ず** `gh pr comment <N> --body "@codex review"` で再レビューを依頼する（PR 作成時と同じ運用）。
- 大きな方針差し戻しになる場合は新タスクとして切り出すかを相談する。

### 前提（Codex 側のセットアップ）

- **ChatGPT Codex（Web 版エージェント）** を使用する。
- chatgpt.com の **Codex** → **Settings** から GitHub を OAuth 連携し、`hang-up33/phantom_nexus` への権限を付与しておく。
- **Codex 側の自動レビュー設定が有効** になっており、`ready-for-review` で PR を push すれば手動操作なしに Codex が PR コメントでレビューを返す。Claude は `gh pr view <番号> --comments` で結果を取得できる。

---

## 利用可能なスキル

| スキル | 用途 |
|---|---|
| [next-task](.claude/skills/next-task/SKILL.md) | 次タスクを進める標準ワークフロー（設計書タスク順を内蔵） |
| [codex-pr](.claude/skills/codex-pr/SKILL.md) | Codex レビュー前提の PR 作成手順 |
| [kaizen-close](.claude/skills/kaizen-close/SKILL.md) | タスク完了直前に学びをドキュメントに反映する手順 |

---

## ルール（Must Always / Must Never）

### Must Always

- 設計書由来のタスク順を守る。
- データ型 / JSON I/O は `Shared/` にのみ実装（"単一の真実" の維持）。
- 描画/移動など JSON 読込前のタスクでも、値は `Shared/Types` の型経由でアクセスし、後の JSON ローダ差替で手戻りが出ない構造にする。
- データ仕様変更で [docs/DataFormat.md](docs/DataFormat.md)、戦闘仕様変更で [docs/BattleSystem.md](docs/BattleSystem.md) を同 PR で更新する。
- 完了報告の直前に [kaizen-close](.claude/skills/kaizen-close/SKILL.md) を実行し、得た知見を反映する。
- 出力（応答・ドキュメント・コミットメッセージ）は日本語を基本とする。

### Must Never

- 複数のタスクを 1 回で実装する。
- フォルダ構成を勝手に変更する（root に増やしてよいのは wrapper / シム 3 点 ＋ VCS メタデータ `.gitignore` / `.gitattributes` のみ。ソース/アセット/ビルドロジックの実体は設計書フォルダに収める）。
- 既存コードを理由なく削除する。
- 設計書未承認のフレームワーク / ライブラリを独断で追加する（JSON は LibGDX 組込みを使い追加依存を作らない。YAML 等が必要になったら相談）。
- MUGEN の名称・仕様・素材をそのままコピーする。
- main ブランチに直接 push / コミットする。タスクは必ず `task/<N>-<短い名>` ブランチ + PR で取り込む。

---

## ビルド / 環境の罠

- **`apply-template.sh` は `CLAUDE.md.template` を置換しない** — スクリプトは `*.md` のみ sed 対象とするため、拡張子 `.template` のファイルはプレースホルダー未置換のままリネームされる。テンプレ初期化時は `CLAUDE.md` のプレースホルダー（`{{BUILD_CMD}}` 等）を手で埋める必要がある（本リポジトリでは初期化時に対応済み）。
- **`docs/` は小文字で統一する** — 設計書は `Docs/`（大文字）だが、テンプレ既存の `docs/`（小文字）と Windows の大文字小文字非区別により同一フォルダに衝突する。GitHub は casing を区別するため、ドキュメントパス・スクショ raw URL は **必ず `docs/` 小文字**で書く（大文字 `Docs/` を新規に書かない）。他のトップ階層（`GameRuntime/` 等）は衝突しないので設計書どおり大文字。
- **`apply-template.sh` の置換対象は `*.md`/`*.json`/`*.sh`/`*.ps1` のみ** — `.github/workflows/*.yml` は対象外。Issue→PR 自動化（`claude-issue-to-pr.yml`）の `--allowedTools` は手で保守する必要があり、本プロジェクトのビルドツール `Bash(./gradlew:*),Bash(gradle:*)` を追加済み（ビルドツールを変えたら要更新）。また置換は `docs/customize.md`/`docs/setup.md` 内の placeholder 名（`{{OWNER}}` 等）まで潰すため、これらは初期コミットから復元してトークンを保持している。
- **`.gitignore` の `build/` は必ずルート固定 `/build/` で書く** — Windows は `core.ignorecase=true` のため、非アンカーの `build/` が大文字小文字を無視して設計書フォルダ **`Infra/Build/`（大文字 B）に一致し、ビルドロジック本体ごと無視**してしまう（`docs/`↔`Docs/` と同種の casing 罠）。ビルド出力は単一モジュール root 直下のみなので `/build/`・`/.gradle/` とアンカーする。`.gitignore` 変更時は `git check-ignore -v Infra/Build/build.gradle` が空（＝未無視）であることを必ず確認する。
- **`Infra/Build/build.gradle` は `apply from:` で読まれるため `plugins {}` DSL が使えない** — applied script の制約。コアプラグインは `apply plugin: 'java'` / `apply plugin: 'application'` のレガシー構文で適用する（`plugins {}` ブロックは root の `build.gradle`/`settings.gradle` のみ）。なお相対パス（`srcDirs` 等）は applied script でも **root プロジェクトディレクトリ基準**で解決される。
- **Gradle の `Executing Gradle on JVM versions 16 and lower has been deprecated` は無害** — ローカル launcher JVM が Java 11 のため出るが、コンパイルは settings.gradle の foojay が auto-provision する **JDK17 toolchain** を使う。Gradle 9 移行時のみ要対応。`./gradlew javaToolchains` で Temurin 17 が provisioned 済みか確認できる。
<!-- 以降、kaizen-close でビルド系の罠を発見したら「症状 / 原因 / 対処」で追記する。 -->

---

## 動作証跡スクリーンショット運用

GUI に変化がある PR には **スクリーンショットを必ず添付する**。Codex / 人間レビュアーの両方が、コードを脳内実行せず「実際にこう見える」を確認できるようにするため。

### 撮影手順（Windows / 本プロジェクトの既定）

```powershell
# ゲームを起動（別プロセス）
Start-Process -FilePath ".\gradlew.bat" -ArgumentList "run"
Start-Sleep -Seconds 8   # LibGDX ウィンドウが出るまで待つ
# 対象ウィンドウをキャプチャ（プロセス名 or ウィンドウタイトルで特定）
powershell -ExecutionPolicy Bypass -File scripts/capture-app-window.ps1 -WindowTitle "Phantom Nexus" -OutPath "docs/screenshots/<N>-<短い名>.png"
```

- `scripts/capture-app-window.ps1` は .NET（`System.Windows.Forms` / `System.Drawing`）でウィンドウ矩形を取得し PNG 保存する。プロセス名 `-ProcessName` でも特定可。
- 撮影後は Read ツールで内容を確認（黒画面・他ウィンドウ混入が無いか）。

### 撮影手順（macOS — 将来の対応用に残置）

```sh
./gradlew run &
sleep 8
scripts/capture-app-window.sh <process-name> docs/screenshots/<N>-<短い名>.png
kill %1
```

- `scripts/capture-app-window.sh` は Swift + `screencapture`。macOS の画面収録権限が必要。**Windows では動作しないため `.ps1` を使う。**

### ファイル配置と PR への埋め込み

- 出力先：`docs/screenshots/<N>-<短い名>.png`（branch 名と揃える）
- PR 本文には **commit SHA 固定の raw URL** で参照する：
  ```markdown
  ![<タスク識別子>: <短い説明>](https://raw.githubusercontent.com/hang-up33/phantom_nexus/<commit-sha>/docs/screenshots/<N>-<短い名>.png)
  ```
- SHA は push 後 `git rev-parse HEAD` で取得。後続の修正コミットでスクショ自体を撮り直したら SHA も更新する。
- GUI 変化が無いタスク（ロジックのみ）はスクショ省略可。

---

## ファイル命名規約

- 小文字 + ハイフン区切り（例：`build-error-resolver.md`, `next-task/SKILL.md`）。ただし **Java ソースのディレクトリ/型名は設計書のパスカルケース**（`GameRuntime/Core` 等）に従う。
- エージェント：`.claude/agents/<name>.md`（YAML frontmatter に `name` `description` `tools` `model`）
- スキル：`.claude/skills/<name>/SKILL.md`（YAML frontmatter に `name` `description`）
- コミットメッセージ：`<タスク識別子>: <短い説明>` を基本フォーマットとする

---

## 対象プラットフォーム

- 開発・動作確認は **Windows PC** で実施（第一設計書の Target）。将来 Linux / macOS に対応予定。

### Claude Code on the web 利用可否

- リモート実行環境では **GUI 起動（`./gradlew run`）とスクリーンショット撮影は不可**（ウィンドウシステムが無いため）。`./gradlew build` / `./gradlew test`（ヘッドレス）までは可能な想定。**GUI 動作確認とスクショは必ずローカル Windows で実施する。**
