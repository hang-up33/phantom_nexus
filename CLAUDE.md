# CLAUDE.md

このファイルは、本リポジトリで作業する Claude Code（claude.ai/code）への指針です。
ユーザーは日本人のため、応答・ドキュメント・コミットメッセージは日本語を基本とします（コード識別子・ファイルパス・JSON キーは英語可）。

---

## プロジェクト概要

**Phantom Nexus** は、MUGEN のようにキャラクター・ステージ・技・当たり判定・AI を **外部データ（JSON）** として追加・編集できる、拡張性の高い 2D 格闘ゲーム基盤。固定キャラ制ではなく「ユーザーが独自キャラを足せる格闘ゲームエンジン」を目指す。技術スタックは Java / LibGDX / Gradle、対象は Windows PC（将来 Linux・macOS）。詳細は [README.md](README.md) と第一設計書を参照。

**現在のフェーズ**：設計書タスク 1〜34 を完了（MVP ＋ コマンド技/必殺技/簡易 AI ＋ 2 体目検証 ＋ ドキュメント整備 ＋ 複数技 JSON 化 ＋ しゃがみ ＋ 複数ラウンド制 ＋ ガード ＋ しゃがみ攻撃 ＋ しゃがみ移動 ＋ しゃがみガード ＋ 下段判定 ＋ 空中攻撃 ＋ ガード高さ属性 ＋ スプライト描画）。さらに追加機能として **Task 35 投げ技（ガード不能の近接掴み）**・**Task 36 投げ抜け（throw tech）**・**Task 37 AI 読み合い反応（攻撃にガード／ガード偏重を投げで崩す）**・**Task 38 ヒットスパーク（命中時の火花演出）**・**Task 39 コンボカウンター（連続ヒット数の表示）**・**Task 40 第2ステージ（stage002・JSON のみで背景差し替え検証）**・**Task 41 3 体目キャラ（fighter003 Tetsu・JSON のみでキャラ追加検証）**・**Task 42 ラウンド開始イントロ（"ROUND N"/"FIGHT!" 演出＋開始前入力ロック）**・**Task 43 ガードゲージ／ガードクラッシュ（連続ガードでゲージ枯渇→ガード不能の隙）**・**Task 44 必殺技ゲージ／EX 必殺技（ゲージ満タンで強化版の飛び道具）**・**Task 45 チェーンコンボ（通常技キャンセル・弱→中→強で連続ヒット）**・**Task 46 コンボダメージ補正（連続ヒットが伸びるほど後続の与ダメージが減衰）**・**Task 47 特殊キャンセル（通常技を必殺技でキャンセルしてコンボを繋ぐ）**・**Task 48 4 体目キャラ（fighter004 Rai・高速ラッシュ型・JSON のみで追加）**・**Task 49 ダッシュ（二度押しで前ステップ／バックステップ）**・**Task 50 AI のダッシュ接近（遠距離は歩きでなくダッシュで素早く間合いを詰める）**・**Task 51 AI の投げ抜け反応（掴まれる瞬間に投げ抜けでノーダメージ＝打撃/ガード/投げの三すくみ完成）**・**Task 52 5 体目キャラ（fighter005 Sora・遠距離 zoner 型・JSON のみで追加）**・**Task 53 打撃必殺技／無敵リバーサル（昇龍拳タイプの無敵対空・`invincibleFrames` を JSON データ化）**・**Task 54 EX 打撃必殺技（メーター満タンで打撃必殺技を消費強化・ダメージ 1.6 倍＋金色 strike/`[EX]` 演出）**・**Task 55 AI の無敵対空（飛び込みを無敵リバーサルで迎撃・AI が初めて必殺技を使う・データ駆動）**・**Task 56 AI 難易度（EASY/NORMAL/HARD・実装済みの反応群を段階解放・既定 HARD で従来挙動）**・**Task 57 AI のジャンプ攻撃（飛び込み・前方ジャンプから空中攻撃を重ねる・HARD のみ・無敵対空と対の攻防）** と ダメージ数値ポップアップ を実装済み。1対1対戦・移動/ジャンプ/しゃがみ（攻撃/移動/ガード）・空中攻撃（飛び込み）・通常攻撃/必殺技（波動拳=飛び道具）・投げ（ガード不能・JSON `throwMove` でデータ化・ジャンプで回避可）・投げ抜け（掴まれる瞬間に投げ返しでノーダメージ・打撃=ガード/投げ=投げ抜け/空中=ジャンプの択が成立）・読み合い反応する簡易 AI（決定的）・ガード高さ属性（`guardHeight` を JSON データ化：overhead=立ちガード必須/mid=両ガード可/low=しゃがみガード必須）・HP ゲージ・攻撃/食らい/押し合い判定・立ち＆しゃがみガード（chip ダメージ・上段/中段/下段の読み合い）・複数ラウンド制（ベスト・オブ 3）・スプライト描画・外部 JSON 駆動（キャラ/ステージ）・デバッグ当たり判定表示・簡易 AI までが動作。次フェーズ候補は Tools（CharacterViewer / HitboxEditor）・サウンド・AI のさらなる拡張（しゃがみ系/難易度の実行時メニュー切替）等。

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

### コードレビューの担当分け（3 系統 + push 前 self-gate）

本プロジェクトの **コードレビューは 3 系統で多重化**し、観点の取りこぼしを減らす：

| レビュアー | 形態 | 発火 |
|---|---|---|
| **Codex GitHub App** | PR コメント（日本語） | `@codex review` を毎 push 後に明示発火（[codex-pr](.claude/skills/codex-pr/SKILL.md)） |
| **CodeRabbit** | PR 自動レビュー | PR push を契機に自動 |
| **Claude（fresh context）** | CI 上で別セッション起動 → PR コメント | `pull_request` イベント（[.github/workflows/claude-review.yml](.github/workflows/claude-review.yml)） |

さらに **push 前に [self-review](.claude/skills/self-review/SKILL.md) スキル**でローカル self-gate を行う（自分の差分を別コンテキストに点検させ、明白なミスを PR 前に潰す）。

**「Claude が自分の書いたコードを自身でレビューする」の肝**は、レビュー役を **実装の経緯を持たない別コンテキストの Claude** にすること。同一セッションでそのまま見直すと自分の判断を正当化するバイアスで粗を見逃すため、(1) CI の `claude-review.yml` は **fresh な別セッション**が diff だけを見てレビューし、(2) self-gate は **サブエージェント / `/code-review`** に切り出す。レビュー観点（単一の真実・フレーム正しさ・後方互換・Must Never 再導入・docs 同期・スクショ）は [AGENTS.md](AGENTS.md) と両スキルで共有する。

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
5. **push 前に [self-review](.claude/skills/self-review/SKILL.md) スキルで差分をセルフレビュー**（明白なミスを PR 前に潰す self-gate）
6. `gh pr create` で **ready-for-review** の PR を作成（draft にしない — Codex GitHub App に即レビューさせるため）
7. **`gh pr comment <N> --body "@codex review"` で明示的にレビュー依頼**（自動レビュー任せにせず毎回コメントで発火）
8. PR URL をユーザーに提示

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
| [self-review](.claude/skills/self-review/SKILL.md) | push 前に自分の差分を別コンテキストでセルフレビューする self-gate |
| [codex-pr](.claude/skills/codex-pr/SKILL.md) | Codex / CodeRabbit / CI Claude レビュー前提の PR 作成手順 |
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
- **日本語を含む `.ps1` は UTF-8 BOM 付きで保存する** — 症状：`capture-app-window.ps1` 等を `powershell.exe`（Windows PowerShell **5.1**）で実行すると `A 'using' statement must appear before any other statements` 等のパースエラーで全滅する。原因：本環境の ANSI コードページは **932（Shift-JIS）** で、PS 5.1 は **BOM 無しスクリプトを ANSI で読む**ため、BOM 無し UTF-8 の日本語コメント/文字列がモジバケしトークンが壊れる（`[System.Text.Encoding]::Default.CodePage` で確認可）。対処：`.ps1` は **UTF-8 BOM 付き**で保存する（`[IO.File]::WriteAllText($p,$txt,(New-Object System.Text.UTF8Encoding($true)))`）。検証：`[System.Management.Automation.Language.Parser]::ParseFile($p,[ref]$null,[ref]$e); $e.Count` が 0。`.gitattributes` で `*.ps1 text eol=crlf` も固定済み（BOM は内容として保持される）。新規 `.ps1` を作る時も同様に BOM 付きにすること。
- **`InputAction` に新アクションを追加したら `ScreenshotController.toAction()` の後方互換も確認する** — 症状：`-k p1.attack` や `script=...:p1.attack` が `IllegalArgumentException` で無視される。原因：`toAction()` が `InputAction.valueOf(name.toUpperCase())` で直接マッピングするため、旧名称（`ATTACK`）→新名称（`ATTACK_LIGHT`）のリネーム後は一致しなくなる。対処：`toAction()` 内に `if (upper.equals("ATTACK")) return InputAction.ATTACK_LIGHT;` の後方互換マッピングを追加する（Task 24 で実施済み）。新しいアクションを追加・廃止するたびに同様の対処が必要。
- **必殺技を複数対応する場合は `Command.name()` ↔ `Move.command` の文字列照合を使う** — `findSpecialMove(def, cmd)` で `cmd.name()`（"HADOUKEN" 等）と JSON の `command` フィールドを `equalsIgnoreCase` で照合するパターン（Task 24 で確立）。新しいコマンド種別（`Command` enum 追加）と JSON `command` 値は必ず一致させること。
- **ガード判定は `Fighter.update()` 冒頭で単一フィールドに集約する**（Task 27 確立）— `grounded && hitstunFrames <= 0 && attackPhase == NONE && moveDir != 0 && moveDir == backDir`（後退方向）の条件を `boolean guarding` に格納し、ヒット解決・飛び道具・アニメーション・描画のすべてが `isGuarding()` で参照する。ガード中は `applyGuard(damage, dir)` を呼び chip ダメージ（`Math.max(1, damage/10)`）と 30% knockback を与え、のけぞりはなし。アニメーション優先順：hitstun > attack > jump > guard > walk > idle。ガード視覚は `GUARD_COLOR = new Color(0.30f, 0.70f, 1f, 0.55f)` の半透明オーバーレイを通常矩形の上に重ねる（`ShapeRenderer` の `rect()` を 2 回描く）。
- **複数ラウンド制で引き分けの扱いには `decisiveRounds` カウンタを使う**（Task 26 確立）— 引き分けラウンドを maxRounds にカウントすると BO3 で DRAW→P1→DRAW→P2→... が maxRounds=3 達成前に終わらないリスクがある。`p1Wins/p2Wins/decisiveRounds`（引き分け除く）を別管理し、終了条件は `p1Wins >= roundsToWin || p2Wins >= roundsToWin || decisiveRounds >= maxRounds || (maxRounds == 1 && roundWinner == DRAW)` とすることで 1 ラウンド引き分けの即終了と BO3 全引き分けのループ防止を両立する。`AiController` には `reset()` メソッドを追加しラウンド間でクールダウンをリセットする。
- **feature branch は必ず最新 main から切る**（Task 28 で踏んだ罠）— 症状：task/28 を task/26 ベースで切ったため、Task 27（ガード）が入った main を後から `git merge main` する必要が生じ、5 ファイルのコンフリクト解消が発生した。対処：ブランチ作成前に必ず `git checkout main && git pull --ff-only` を実行し、最新 main から `git checkout -b task/<N>-<name>` する（next-task スキルの手順 3 参照）。
- **PR/タスク名に未実装の効果を含めると Codex が収束しない**（Task 28 で踏んだ罠）— 症状：タスク名に「下段判定」と書いたところ Codex が3ラウンド連続で hitbox 未変更を指摘し続け、"Didn't find any major issues" まで進めなかった。対処：コミット・PR タイトル・README の機能説明は**実装済みの事実だけ**を正確に表現する（例：「下段判定」→「低姿勢を維持したまま攻撃」）。次タスクで hitbox を変えたら改めてドキュメントを更新する。
- **しゃがみ状態の chip / 被弾はスクショで観測できない（しゃがみが回避優位）**（Task 30 で踏んだ罠）— 症状：しゃがみガードの被弾スクショを撮っても P1 の HP が満タンのまま（chip が乗らない）。原因：しゃがみ時の hurtbox は `height/3`（Aoi=240 → 80px）に縮むが、全キャラの最も低い技でも `hitboxOffsetY ≥ 90px`（fighter002 medium=90, heavy=100, light=120）でこの上端を超えるため、近接技がしゃがみ食らい判定に届かず「ガード以前に空振り＝回避」になる。対処：しゃがみ系（攻撃/ガード）の証跡は **状態ラベル（`crouch_guard` 等）＋低姿勢オーバーレイの可視化**で示す（Task 28「下段判定なし」と同じ割り切り）。chip が乗る証跡が要るのは将来 `hitboxOffsetY < 80` の下段 hitbox を追加してから。
- **ガード判定を「立ち + しゃがみ」両対応にするときはアニメ優先順で `CROUCH_GUARD` を `CROUCH_WALK`/`CROUCH` より前に置く**（Task 30 確立）— `guarding` の条件から `!crouchHeld` を外すと、しゃがみ後退でも `isGuarding()==true` になる（立ちガード版のしゃがみ＝しゃがみガード）。`isCrouchGuarding()` は `guarding && crouching`。`FighterAnimator.resolve()` で `CROUCH_GUARD` を `CROUCH_WALK`/`CROUCH`/`GUARD` より前に評価しないと、しゃがみ後退が `CROUCH_WALK`（前進クロール）に化ける。なお**ガードオーバーレイ描画は追加不要** — `GameRenderer.drawFighter` の `drawHeight`（しゃがみ時 `height/3`）に `isGuarding()` のオーバーレイを重ねる既存コードが、そのまま低姿勢ガードを正しく描く。
- **Claude のレビュー用 GitHub Action は `pull_request` 専用にして実装ワークフローと発火を分離する** — 症状：レビュー用 `claude-review.yml` を `issue_comment`（`@claude`）でも発火させると、既存の実装ワークフロー `claude-issue-to-pr.yml`（issues / `@claude` コメント発火）と**二重起動**して PR コメントに実装と無関係なレビューが混ざる。原因：GitHub では PR コメントも `issue_comment` イベントとして飛ぶため、`@claude` トリガが両方に刺さる。対処：レビューは `pull_request`（opened / synchronize / ready_for_review）**だけ**で発火させる。さらに fork PR は Secrets が渡らずコケるので `head.repo.full_name == github.repository` で、WIP は `draft == false` でガードする。`anthropics/claude-code-action@v1` は prompt モードで自身が `gh` を使って PR にコメントを投稿するので、allowedTools に `Bash(gh:*)` を含め、レビューのみなら `Write`/`Edit` は外す。**認証は Claude 用 Secret が必須** — `CLAUDE_CODE_OAUTH_TOKEN`（`claude setup-token` で発行、サブスク枠）か `ANTHROPIC_API_KEY`（従量）のいずれかを `with:` に渡す（本リポジトリは前者）。未登録だとインストール成功後に `Either ANTHROPIC_API_KEY, CLAUDE_CODE_OAUTH_TOKEN, ... is required` で即失敗する（GITHUB_TOKEN とは別物）。`with:` のキー名（`claude_code_oauth_token` / `anthropic_api_key`）と Secret 名の対応を一致させること。**Secret 未登録でも PR チェックを赤にしない**には、ジョブ先頭に「トークン有無を確認」ステップを置き（`env: TOKEN: ${{ secrets.X }}` → `[ -z "$TOKEN" ]` で `GITHUB_OUTPUT` に `present=false`）、checkout / action 各ステップを `if: steps.<id>.outputs.present == 'true'` でガードする。secrets は `if:` で直接参照できないため、必ず env 経由＋step output に落としてから条件分岐する。これで未登録時はジョブが緑のままスキップし、登録した瞬間に自動で走り出す。
- **「自分が書いたコードを自身でレビュー」は fresh context にしないと形骸化する** — 同一セッションの Claude がそのまま見直すと、自分の判断を知っているぶん正当化バイアスで粗を見逃す。対処：(1) CI の `claude-review.yml` は**実装の経緯を持たない別セッションの Claude** が PR の diff だけを見てレビューする、(2) push 前 self-gate（[self-review](.claude/skills/self-review/SKILL.md)）は `/code-review` か Agent サブエージェントに**実装意図を渡さず diff とチェックリストだけ**を渡す。レビュー観点は AGENTS.md と両スキルで共有し重複させない。
- **ガード貫通など「防御が成立しなかった」証跡は対比2枚で撮る**（Task 31 確立）— 症状：下段が立ちガードを貫通する単独スクショ（full hit + hitstun）を撮っても、レビュアーは「そもそもガードしていなかっただけでは？」と区別できない。対処：**同一条件で中段=ブロック（chip + ガードオーバーレイ + `guard` 状態）と下段=貫通（full hit + `hitstun` 状態）の2枚**を並べる（`31-mid-blocked.png` / `31-low-attack.png`）。違いを「P1 がしゃがみ(下段)か立ち(中段)か」だけにすると因果が一意になる。
- **近接の被弾/ガード証跡は防御側を画面端（clamp）に置いて静止させる**（Task 31 確立）— 症状：立ちガード（後退方向保持）は `walkSpeed` で毎フレーム後退ドリフトし、攻撃の active 区間（数フレーム後）には間合いから出て空振りする。対処：防御側の初期 X を画面端付近（例 `-x p2x=1238` ≒ `WORLD_WIDTH-幅/2`）に置き、後退入力が `clampToStage` で止まる状態にして静止させる。攻撃側は反対側からその位置へ届く X に置く（例 `-x p1x=1110`）。`-x ai=false` で防御側 AI も止める。
- **しゃがみ（下段）攻撃の撮影は「DOWN を初手から保持＋攻撃を後続フレームのエッジで」発火する**（Task 31 確立）— `-k p1.down` で DOWN を frame1 から押しっぱなしにし、`-x "script=8-9:p1.attack"` で攻撃エッジを後続フレームに置く。同フレーム DOWN+攻撃は遷移フレーム抑止で無視されるため、攻撃エッジは crouching 確定後（数フレーム後）に出すこと。
- **JSON の任意フィールドはフィールド初期化子で既定値を入れれば後方互換になる**（Task 33 確立）— LibGDX `Json.fromJson` は **JSON に存在するキーのみ**を上書きし、欠落キーは Java の初期化値を保持する。よって `private String guardHeight = "mid";` と書けば旧 JSON（キー無し）は自動で `"mid"` になる。さらに getter で `null/空 → 既定`・`trim().toLowerCase()` 正規化して読み手側のブレを消し、`CharacterLoader` の検証は**正規化後の getter 値**を許可セットと照合する（生フィールドを直接見ない）。新しい列挙フィールドを足すときは「フィールド初期化子＋正規化 getter＋getter 経由の検証」をワンセットにする。
- **overhead（上段）がしゃがみガードを貫通する証跡は、hitbox をしゃがみ hurtbox（上端 ≒ 接地 Y+height/3）に届かせないと撮れない**（Task 33 確立）— 属性（読み合いのルール＝立ちガード必須）と hitbox 形状（届くか）は**独立**。高い overhead hitbox は短いしゃがみ相手に空振りして「貫通」ではなく「回避」に見える（Task 30/31 と同じ罠の裏返し）。対処：例示技は hitbox を下方向へ広げる（fighter001 `heavy_slam` を `offsetY 60 / height 90` ＝ Y60–150 占有）。本プロジェクトの接地 Y はステージ床の **y=120**、しゃがみ hurtbox は `120〜120+height/3`（Aoi で 120–200）なので、貫通を撮るには hitbox 下端をおおむね 200px 未満に入れる。
- **高さ属性ガードの対比2枚は「同一攻撃・防御側の姿勢だけ変える」で撮る**（Task 33 確立、Task 31 の対比手法を踏襲）— overhead 強攻撃に対し、防御側を画面端 clamp（`-x p2x=1238`）＋ `-x ai=false` で静止させ、(1) 立ちガード `-k "p1.attack_heavy,p2.right"` → `guard`・chip 13、(2) しゃがみガード `-k "p1.attack_heavy,p2.down,p2.right"` → `hitstun`・フル 130 を撮る。違いを「DOWN を足したか」だけにすると貫通の因果が一意になる。強攻撃トークンは `attack_heavy`（`ScreenshotController.toAction` が `InputAction.valueOf` で解決。`attack` のみ後方互換で LIGHT）。
- **列挙的な属性は「JSON は String フィールドのまま・正準値は `Shared/Types` の enum に集約」する**（guard-height-enum リファクタで確立）— 症状：`guardHeight` の正準値 `overhead`/`mid`/`low` が **Move（正規化 getter）・CharacterLoader（`VALID_GUARD_HEIGHTS` セット＋検証）・PhantomNexusGame（`switch ("low")` 等の文字列分岐）の 3 箇所に生リテラルで散在**し、タイポがコンパイルエラーにならず「単一の真実」も崩れていた。対処：`GuardHeight` enum（`OVERHEAD/MID/LOW` ＋ `DEFAULT` ＋ `fromToken(String)`：null/空→DEFAULT・既知→定数・**未知→null（不正値シグナル）**）を `Shared/Types` に新設し、(1) `Move.getGuardHeight()` は enum を返す（未知は防御的に DEFAULT へ丸め、実行時 null を出さない）・検証用に生トークンは `getGuardHeightToken()` で別途公開、(2) ローダは `VALID_*` セットを廃し `fromToken(token)==null` で未知だけ弾く、(3) Core の分岐は `switch` を enum case に。**JSON 表現（小文字トークン）と後方互換は不変**（LibGDX `Json` は依然 String フィールドへ書き込む＝デシリアライズ層は触らない）。`button`（light/medium/heavy）も同パターンで `AttackButton` enum に集約済み（**必須フィールドは `fromToken` の null/空 → `null`（既定値なし）とし、ローダの必須チェックに弾かせる**点が任意フィールドの `guardHeight` と異なる）。同型の散在は `command`（HADOUKEN 等）に残っているが、`Command` enum が `GameRuntime/Input` にあり Shared から依存できないため、enum 化には配置の設計判断（`Shared/Types` への移設等）が必要（将来候補）。型安全と単一の真実を取りつつ JSON 層の後方互換を壊さない定石として、新しい列挙属性は最初からこの形で入れる。
- **docs 更新は「冒頭サマリ・正準リスト・変更履歴・サンプル JSON」の 4 点セットで漏れなく行う**（整合性レビューで検出した drift パターン）— 症状：Task 30〜33 で新節の追加はされていたが、BattleSystem.md 冒頭の反映タスク一覧（Task 29 止まり）・ステート一覧（crouch_attack/crouch_guard/jump_attack 欠落）・旧タスク節のアニメ優先順（空中攻撃・しゃがみガード未反映）・Task 33 の変更履歴、DataFormat.md のサンプル JSON（heavy_slam の overhead 化未反映）・変更履歴が古いまま残った。対処：(1) アニメ優先順は BattleSystem.md「ステート」節の正準リスト（実装の真実は `FighterAnimator.resolve()`）を単一の真実とし、優先順が変わるタスクでは「優先順」で grep して言及箇所をまとめて更新する、(2) DataFormat.md のサンプルは実ファイル（`Assets/Characters/*.json`）と一致させる、(3) データ/戦闘仕様を変えたタスクは両 docs の変更履歴へのエントリ追加までを完了条件に含める。
- **スプライト描画は「データ（パス/レイアウト）は `Shared/Types`・GPU リソース（Texture）は `GameRuntime/Rendering`」で分割する**（Task 34 確立）— `Texture` は GL コンテキストを要する実行時リソースなので「データの単一の真実 = `Shared/`」に置けない。対処：`Shared/Types/Sprite`（`path`/`frameWidth`/`frameHeight`/`stateRows[]`）は**画像パスとグリッド寸法だけ**を持ち、`GameRuntime/Rendering/SpriteLibrary` が `Gdx.files.classpath(path)` → `new Texture` → `TextureRegion.split` してキャッシュする。PNG 欠落・読込失敗は `SpriteLibrary` 側で握って `null` を返し、`GameRenderer` がプレースホルダ矩形へフォールバックする（`CharacterLoader` は**形状のみ検証し実在チェックはしない**＝データ層は GL に触れない）。`Character.sprite` は任意フィールドで未指定（`null`）なら矩形＝後方互換（Task 33 のフィールド初期化子パターンの拡張）。
- **`ShapeRenderer` と `SpriteBatch` は同一フレーム内で `begin`/`end` を入れ子・交互にできない** — 症状：スプライト（batch）を矩形描画（shapes.begin〜end）の途中に挟むと描画が壊れる / 例外。対処：描画を**パスに分ける**。Task 34 で `renderScene` を「(1) 背景 shapes → (2) スプライト batch → (3) オーバーレイ shapes（矩形フォールバック/ガード/strike/HP）→ (4) デバッグ枠 shapes(Line) → (5) テキスト batch」の 5 パスに再構成した。各パスは前パスを `end()` してから次を `begin()` する。ブレンド有効化（`glEnable(GL_BLEND)`）はパス開始前に 1 回でよい。
- **`TextureRegion.split` で得た領域を `flip` で反転すると共有インスタンスが変わる** — 症状：向きで `region.flip(true,false)` すると、キャッシュした領域の flip 状態が次フレーム/別ファイターに持ち越され、ミラーマッチや左右混在で反転がちらつく。対処：描画直前に**目標 flip 状態へ揃える**（`if (region.isFlipX() != faceLeft) region.flip(true,false);`）。`batch.draw` は即座に頂点をコピーするので、同一パス内で同じ領域を別 flip で複数回描いても順次設定すれば正しい。シートは**右向きのみ**用意し左向きは反転で作る。
- **`Shared/Types` から `GameRuntime/Rendering` の enum（`AnimationState`）へ依存させない** — スプライトの状態→行マップは `AnimationState` で持ちたくなるが、それだと Shared が Rendering に依存し層が逆転する。対処：`SpriteStateRow.state` は**小文字ラベルの String**で保持し、描画側が `AnimationState.label()` と照合する（`guardHeight` を String で持ち `GuardHeight` enum 解釈を描画/ロジック側に置いたのと同じ構図）。未マップ状態は行 0（idle）へフォールバック。
- **プレースホルダ PNG はヘッドレス AWT（`BufferedImage`+`ImageIO`）で生成できる（PIL 不要）** — 本環境に Pillow は無いが JDK は常にある。対処：`java.awt`（`-Djava.awt.headless=true`）で 64×128 セルのスプライトシートを描いて `ImageIO.write(...,"png",...)` する使い捨てジェネレータを `/tmp` に置いて実行し、**PNG だけ**を `Assets/Characters/` に同梱する（ジェネレータはリポジトリに残さない＝フォルダ構成を増やさない）。キャラ色を引数で渡せば色違いの 2 体分を生成できる。撮影後は Read で目視。**シートは 256×896（64×128 セル × 4 列 × 7 行＝idle/walk/jump/attack/hitstun/guard/crouch）に固定**し、JSON の `sprite.stateRows[]` はこの行レイアウトを参照する（Task 41 で fighter003=紫、Task 48 で fighter004=黄緑、Task 52 で fighter005=青緑/teal=`40 170 178` を生成。ジェネレータに `<out> <r> <g> <b>` を渡して色違いを出力）。新キャラを足すたびに JSON のステータス/技で**アーキタイプを差別化**するとリスキンに留まらない（Task 52 Sora は hitboxWidth 104/124/144 の長射程通常＋高速飛び道具＋低 HP で zoner 型＝既存の Aoi/Akane/Tetsu/Rai と別の間合い）。新キャラ追加は「JSON（ステータス/技/sprite）＋同レイアウト PNG」の 2 ファイルで完結し、**追加したキャラはコード変更なしでチェーンコンボ・特殊キャンセル・コンボ補正・必殺技ゲージ等の既存戦闘機構をそのまま利用できる**（Task 48 で fighter004 が低リカバリ通常で jab→elbow→uppercut の 3 HITS を出すスクショで実証）＝データ駆動の到達点。
- **撮影用キャラ/ステージのオーバーライドは `ScreenshotController` に `xxxId(...)` getter を足し 3 点セットで配線する**（Task 40 stage / Task 41 char で確立）— 新しい「読み込むリソースの撮り分け」を足すときは、(1) `ScreenshotController` に `isEnabled()` ガード付きの getter（`stageId(fallback)` / `charId(player, fallback)`：撮影モードかつプロパティ指定時のみ差し替え・通常起動は fallback）、(2) `PhantomNexusGame.create()` のロードを getter 経由に（**`screenshot` を当該ロードより前に初期化**＝NPE 回避。Task 40 の並べ替え）、(3) `Infra/Build/build.gradle` の run タスク転送リストへ `phantom.screenshot.<key>` を追記——の 3 点をワンセットにする。プレイヤー別（`p1char`/`p2char`）はプロパティ名を `player == 2 ? ".p2char" : ".p1char"` で分岐（既存 `heldActions`/`spawnX` と同じ判定パターンに統一）。`aiEnabled`/`spawnX` と同じ「撮影時のみ効く・後方互換」パターン。
- **被弾演出で表示する「与ダメージ量」は式を複製せず適用前後の HP 差で出す**（ダメージ数値ポップアップで確立）— 症状：ポップアップに表示する数値を `move.getDamage()` や chip 式 `max(1, damage/10)` から再計算すると、ガード chip・残 HP 未満の 0 クランプ・将来の補正で実減少と食い違う。対処：`int before = defender.getCurrentHp(); applyHit/applyGuard(...); int dealt = before - defender.getCurrentHp();` の差分を表示量にする（`Fighter` 内の式に依存せず常に実減少と一致）。新しい被弾フィードバック（ヒットスパーク量・コンボ補正後ダメージ等）も同じ「HP 差」方式で出すこと。
- **純粋な視覚演出の実行時状態は `Projectile` と同じパターン（Battle POJO ＋ Core 所有リスト ＋ Renderer 描画）で足す**（ダメージ数値ポップアップで確立）— `Texture` 等の GPU リソースを持たない一過性エフェクト（数字・スパーク等）は `GameRuntime/Battle` に状態 POJO（量・原点・経過/寿命）を置き、`PhantomNexusGame` が `List` を保持して毎フレーム `update()`＋期限切れ除去・`resetFighters()` でクリアし、`GameRenderer` が読み取って描く。エフェクトを「決着/ラウンド間でも動かしたい」なら `update()` 冒頭の `round.isFinished()` 凍結ガードより**前**で aging する（KO を決めた一撃の数字が止まらず最後まで浮かぶ）。
- **「攻防のリソースゲージ」は決着点（`resolveHit`/`updateProjectiles`）に集約した 1 つのヘルパーで固定値（乱数なし）加算する**（Task 44 必殺技ゲージで確立）— ガードゲージ（防御・Task 43）の対になる攻撃リソース（必殺技ゲージ）を足すとき、命中/ガード/投げ/飛び道具の各分岐に加算を散らすと漏れる。対処：`awardMeter(attacker, defender, blocked)` を 1 つ作り、HP を適用する 2 箇所（`resolveHit` の打撃/投げ末尾・`updateProjectiles` のヒット末尾）から呼ぶ。命中=攻撃側多め・防御側少なめ、ガード=両者わずか、を**固定定数のみ**で与える（`Math.random()` 厳禁＝入力リプレイ #38 の決定性を保つ）。投げ抜け（ノーダメージ）は加算前に return しているので自然に蓄積されない。`Fighter` 側はゲージ float ＋ `gainMeter`(MAX 頭打ち)/`hasFullMeter`/`spendFullMeter`/`setMeter`/`getSuperMeter` ＋ `reset()` で 0、と guardGauge と同型で持つ。
- **「ゲージ消費の強化技（EX）」はキャラ JSON を変えず発射時の乗算＋フラグで実現する**（Task 44 EX 必殺技で確立）— EX 版を `specialMoves[]` の別エントリ（`ex:true`）にすると `findSpecialMove` の選択ロジック・データモデル・ローダ検証まで波及して重い。対処：発動時に `f.hasFullMeter()` なら `spendFullMeter()` し、`spawnProjectile(f, move, ex=true)` が**その場で** `damage = round(damage × EX_DAMAGE_MULTIPLIER)`・サイズ `× EX_PROJECTILE_SCALE` を適用、`Projectile` に `ex` フラグを足して描画を金色/大型に切り替える。JSON 不変＝DataFormat.md も不変（戦闘仕様の BattleSystem.md のみ）。EX をキャラ固有技としてデータ化するのは将来候補（全キャラの必殺技が飛び道具の現状はこの簡易版で足りる。打撃必殺技の EX も同様に後付け可能）。撮影は `initialMeter`（`-x p1meter=100`）で満タンにして貯め直し無しに EX を撮る（`spawnX`/`aiEnabled` と同じ「撮影時のみ効く」オーバーライド）。
- **「ダメージの無い行動拘束（投げ抜け/ガードクラッシュ等）」は `hitstunFrames` を流用しつつ表示用の別フラグを 1 つ持つ**（Task 36 投げ抜け → Task 43 ガードクラッシュで再確立）— ガードクラッシュの「ガード不能・行動不能の隙」は hitstun と同じ拘束が欲しいが**ダメージ無し**でラベルを分けたい。対処：`applyGuard` でゲージ 0 を検出したら `hitstunFrames = GUARD_BREAK_FRAMES`（既存の拘束・knockback 減衰ロジックを流用）と表示専用 `guardBreakFrames` を併走させる。クラッシュ中は `hitstunFrames > 0` で次フレームの `guarding` 算出が false → `resolveHit` がフル `applyHit` を呼ぶ＝ガード不能が自動的に成立する（専用の貫通分岐は不要）。`drawNameLabel` は `isThrowTeched()` → `isGuardBroken()` → `isInHitstun()` の順で評価（hitstun 流用組はすべて hitstun より先に置く・順序を誤ると "hitstun" に化ける）。`applyHit`/`applyThrow` で `guardBreakFrames` をクリア（クラッシュ硬直中にフル被弾したらラベル更新）。ゲージは float で持ち非ガード時に毎フレーム微小回復、`reset()` で満タン。グローバル定数で全キャラ共通にすれば JSON 変更不要（キャラ差が要れば将来 `Character` へ）。
- **画面端のファイター頭上に出すフローティングラベルは `drawCenteredClamped` で画面内にクランプする**（Task 43 で確立）— 症状：ガードクラッシュ等の証跡は防御側を画面端（clamp）に置いて静止させる必要がある（Task 31 確立）が、頭上ラベル（"GUARD BREAK!"）を `drawCentered`（中心 X 固定）で出すと端で見切れて読めない。対処：左端 X を `[margin, WORLD_WIDTH - 文字幅 - margin]` にクランプする `drawCenteredClamped` を使う（HUD の中央寄せテキストは真の中央維持が要るので従来の `drawCentered` のまま・フローティングラベル専用）。コンボ "N HITS!" も端で同様だが現状は中央寄りで撮るため未適用（必要時に切替）。
- **「戦闘を凍結する開始/演出フェーズ」を足すときは撮影モードで既定スキップにして既存スクショレシピを守る**（Task 42 ラウンド開始イントロで確立）— 症状：ラウンド開始に "ROUND N"/"FIGHT!" の入力ロック（90f≒1.5 秒の戦闘凍結）を入れると、`-f 14`・`script=1-1:...` のような **frame1 から戦闘が動く前提の既存スクショレシピが全滅**する（イントロ中は `resolveHit`/`updateProjectiles` がガードされ攻撃が当たらない）。対処：凍結フェーズ長を `RoundManager` のコンストラクタ引数（`introFrames`）にし、`ScreenshotController.roundIntroEnabled(fallback)` を**撮影モードでは既定 false**（`-x intro=true` 指定時のみ true）にして、Core が `screenshot.roundIntroEnabled(true) ? ROUND_INTRO_FRAMES : 0` で長さを決める。通常起動・リプレイ（記録/再生とも同一イントロ長で決定的）は ON、撮影は既定 OFF で既存レシピ不変・`-x intro=true` で演出コマだけ撮る。Core の戦闘ガードは `!isBetweenRounds() && !isRoundIntro()` に拡張。`ai=false` と同じ「撮影時のみ効く・後方互換」パターンだが、**既定値が通常 ON／撮影 OFF と逆**である点に注意（撮影の決定性・既存レシピ保護を優先）。`-x intro` を `Infra/Build/build.gradle` の転送リストへ追記するのも忘れない。
- **`BitmapFont` の色・倍率は共有状態なので、色付き/拡大した文字を描いたら同じパス内で既定（白・等倍）へ戻す**（ダメージ数値ポップアップで確立）— 症状：ポップアップを `font.setColor(...)`／`setScale(1.7f)` で描いた後そのままにすると、同じテキストパスの後続 HUD（名前・結果バナー等）に色/サイズが漏れる。対処：色付き描画ブロックの末尾で `font.setColor(Color.WHITE); font.getData().setScale(1.0f);` に戻す。毎フレームの `Color` 再確保を避けるため作業用 `Color` フィールド（`popupColor`）を 1 つ持って `set(...)` で使い回す。
- **「攻撃ボタンと別系統の新アクション」は専用 `InputAction` ＋ `Fighter.update` の専用引数で増設する**（Task 35 投げで確立 → AttackButton enum 化で確定）— 投げのような打撃と別カテゴリの行動は、(1) `InputAction.THROW` を足し（`PlayerInput` の P1/P2 へ物理キー割当）、(2) Core が `Fighter.update` に**専用引数 `throwReq`（boolean）** を渡し、(3) `Fighter` が `selectNormalMove` の前で `throwReq` を判定して `def.getThrowMove()` を選ぶ——という形にすると新行動をきれいに差し込める。**当初は打撃の `attackButton`（String）に予約語 `"throw"` を相乗りさせていた（シグネチャ非変更が利点）が、`button` を `AttackButton` enum へ集約した段階で「打撃ボタンの閉じた enum に投げという別カテゴリを混ぜられない」問題が顕在化し、専用 `throwReq` 引数へ分離した**（打撃は型安全な enum・投げは独立チャネル＝両立）。発動の最優先化・抑止（通常技/必殺技を出さない）は Core の `updateFighterInput` 側で `if (throwReq) { attackButton=null; } else if (cmd...) {special}` の分岐で行い、`throwReq` を `update` へ渡す。教訓：別系統アクションを既存パラメータの「マジック値」に相乗りさせると、そのパラメータを後で型安全化したとき衝突する。最初から専用フラグ/引数で分けるのが安全。`ScreenshotController.toAction` は `InputAction.valueOf` で解決するため、enum 名と一致する小文字トークン（`throw`）は**後方互換マッピング不要で**スクショスクリプト（`-k p1.throw`）からそのまま撃てる（Task 24 の `attack`→`ATTACK_LIGHT` のような別名だけが特例）。
- **ガード不能（投げ）の証跡は「同一ガード状態の相手に打撃 vs 投げ」の対比2枚で撮る**（Task 35 確立、Task 31/33 の対比手法を踏襲）— 単独の「投げがフルヒットした1枚」だけでは「そもそもガードしていなかっただけでは？」と区別できない。対処：防御側を画面端 clamp（`-x p2x=1238`）＋`-x ai=false`＋後退方向保持（`p2.right`）で**ガード状態に固定**し、攻撃側だけを (1) 中段打撃 `-k "p1.attack_medium,p2.right"` → `guard`・chip（青ポップアップ）、(2) 投げ `-k "p1.throw,p2.right"` → `hitstun`・フルダメージ（黄ポップアップ＋紫 grab box）に変える。違いを「打撃か投げか」だけにすると貫通の因果が一意になる。投げの被弾は `applyThrow`（フルダメージ）なので**黄（HIT）ポップアップ**で出る（ガード chip の青ではない）＝色でも貫通が読める。近接は `-x p1x=1140 -x p2x=1238`（grab box 幅 50 が相手 hurtbox に届く位置）。
- **投げ技は「相手が地上にいるか」を `resolveHit` で必ずチェックし、空中の相手に重なったら whiff として消費する**（Task 35・CodeRabbit 指摘で確定）— 投げは打撃と同じ active hitbox × hurtbox の重なりで成立させるが、`attacker.isThrowing() && !defender.isGrounded()` のとき **`markAttackConnected()` してから `return`** する（空中の相手は掴めない＝ジャンプが投げ回避の択）。**ここで mark しないと、grab box が空中の相手に重なった同じ active 区間内に相手が着地した次フレームで掴み直してしまい、「ジャンプで回避可」の約束を破る**（当初 mark せず実装して指摘された罠）。mark して whiff 消費すれば、ジャンプが確実な回避になる。ガード不能化は同 `resolveHit` で `isThrowing()` 時に `blocked` 判定を丸ごとスキップして実現する（`effectiveAttackHeight` の overhead/mid/low 分岐に入れない）。
- **入力猶予窓（buffer）系の機能は「入力フレームで窓をアーム → 毎フレーム減衰 → 解決フレームで窓をチェック」の3点で組む**（Task 36 投げ抜けで確立）— 投げ抜けのような「相手の行動に対しタイミングよく入力したら成立」系は、(1) 対象入力（投げボタン）を押した接地フレームに Core が `Fighter.armThrowTech()` で窓（`THROW_TECH_WINDOW`）を立て、(2) `Fighter.update` 冒頭で窓カウンタを毎フレーム減衰、(3) `resolveHit` の投げ成立点で**被掴み側の** `canTechThrow()` を見て分岐——とする。窓をアームする条件は「自分の行動が成立しなくても受け付ける」のがコツ（間合い外で投げが出なくても防御反応の投げ抜け入力は拾う）。`isPressed(THROW)` は forced 時に 1 フレーム 1 回しか true を返さない（CLAUDE.md 既出）ので、押下を 1 変数に読んで「窓アーム」と「自分の投げ発動 throwReq」の両方に使い回す（二重 `isPressed` 呼び出しは 2 回目が false になる）。**乱数を使わない**こと（入力と窓カウンタのみで決定的に＝入力リプレイと両立）。
- **「ダメージの無い硬直/のけぞり」は `hitstunFrames` を流用しつつ、表示・分岐用に別フラグを 1 つ持つ**（Task 36 投げ抜けで確立）— 投げ抜けの硬直は「のけぞりと同じ行動拘束＋ knockback 減衰」が欲しいが**ノーダメージ**かつラベルを分けたい。対処：`applyThrowTech` で `hitstunFrames` を立てて既存の拘束・減衰ロジックをそのまま使い（`canStartAction`/`guarding` が `hitstunFrames<=0` で自動的にロックされる）、加えて `throwTechFrames`（表示専用カウンタ）を併走させる。`GameRenderer.drawNameLabel` は `isThrowTeched()` を `isInHitstun()` より**先に**評価して `tech` ラベルを出す（hitstun を流用しているため順序を誤ると "hitstun" に化ける）。新規 `AnimationState` は足さず HITSTUN ポーズを再利用（被弾と被掴み抜けは見た目近い）＝状態爆発を避ける。ダメージ無しなのでダメージ数値ポップアップも出さない（Core で `applyThrow` ではなく `applyThrowTech` を呼ぶ＝そもそも HP 差が 0）。
- **対の関係にある防御テク（投げ↔投げ抜け）の証跡は「同じ攻撃・被防御側の入力だけ変える」対比2枚**（Task 36 確立、Task 31/33/35 を踏襲）— 近接の投げに対し、被掴み側を画面端（`-x p2x=1238`）＋`-x ai=false`で固定し、(1) 無入力 `-k p1.throw` → `hitstun`・フル 150（黄ポップアップ）、(2) 投げ返し `-k "p1.throw,p2.throw"` → 両者 `tech`・HP 不変。違いを「被掴み側が投げボタンを押したか」だけにすると投げ抜けの因果が一意。両者が反対方向へ弾かれ HP バーが減らない（満タンのまま）ことが抜け成立の決め手。
- **AI の判断は「相手の観測状態＋距離」だけで決め、乱数を使わない**（Task 37 確立）— `AiController` に読み合い反応（打撃にガード／ガード偏重を投げ）を足す時、ランダムで揺らすと**入力リプレイ（#38）の決定的シミュが壊れる**（同じ入力で同じ試合にならない）。対処：判断は `opponent.isAttacking()`/`isThrowing()`/`isGuarding()` ＋ 中心間距離 ＋ クールダウンのみで分岐し、`Math.random()` を一切使わない。AI は `updateFighterInput` を経由せず `Fighter.update(...)` を直接呼ぶため、投げ/投げ抜けを使わせるには `throwReq` を AI 側で立て（必要なら `self.armThrowTech()` も AI から呼ぶ）、`updateFighterInput` 側の配線に依存しない点に注意。優先順は「状態反応（ガード/投げ）＞ 距離行動（接近/攻撃）」。
- **AI の反応はタイミング依存なので、対比2枚は「相手（強制入力側）の状態を固定して AI の反応を撮る」**（Task 37 確立）— AI が自発的に動くため静止スクショが難しい。対処：人間側を `-k` の強制入力で特定状態に固定し、AI（P2・`ai=false` を付けない＝AI ON）の反応コマを `-f` で走査して採る。(1) AI ガード反応＝ `-k p1.attack_medium -x p1x=1140 -x p2x=1238`（P1 が攻撃 → P2 AI が `guard`・chip）、(2) AI 投げ崩し＝ `-k p1.left -x p1x=60 -x p2x=180`（P1 が後退ガード偏重 → P2 AI が `throw`・P1 `hitstun`）。フレーム順（Core は P1→P2 の順に update）により、AI は同フレームに相手の最新状態を見て反応するので、強制入力フレームの数フレーム後を撮れば反応が映る。
- **コンボの撮影は「復帰(recovery) < のけぞり(hitstun) になる技」を選ぶ**（Task 39 確立）— 真のコンボ（hitstun 連鎖）を撮るには、追撃が相手の hitstun が切れる前に当たる必要がある。停止した attacker の同技連打では `active + recovery + 次 startup < HITSTUN_FRAMES(18)` が条件。**Aoi の `light`（4+10+5=19 > 18）は 1F 足りずコンボにならず（リセット＝カウンタ 1 のまま）**、**Akane の `quick_jab`（3+8+4=15 < 18）はコンボ成立**。撮影は P2=Akane を `ai=false` で強制入力し2連 `quick_jab`：`-x ai=false -x p1x=600 -x p2x=680 -x "script=1-1:p2.attack;16-17:p2.attack" -f 22` で Aoi 頭上に `2 HITS!` が出る。停止2体は「どの active フレームで当たるか」が位置非依存（常に最初の active フレーム）なので、間合い調整では当たるタイミングをずらせない＝コンボ可否は純粋にフレームデータで決まる点に注意。
- **チェーンキャンセル（攻撃中に別技を開始）は `attackPhase == NONE` の開始ガードに `else if (canChainInto(...))` を併設して足す**（Task 45 確立）— 通常技の硬直を待たず上位技へ繋ぐガトリングは、`Fighter.update` の「新規攻撃開始（`attackPhase == NONE` 必須）」ブロックに**もう 1 本の発動経路**を `else if` で足すのがクリーン。キャンセル可否 `canChainInto(next)` の条件は「接地・進行中が**通常技**（`currentMove.getButton() != null`＝必殺/投げ除外）・`ACTIVE`/`RECOVERY`・**接触済み**（`attackConnected`＝空振り不可）・段位 `AttackButton.ordinal()` が上（弱<中<強の一方向）」。発動は `beginAttack(move)` をそのまま呼ぶだけ（`attackConnected`/`attackFrame`/`attackPhase` がリセットされ、新技が改めて 1 回命中＝多段防止 `hasAttackConnected` と自然に両立）。**フレーム遷移はリセット後の同 tick `advanceAttack()` が新技を frame1 へ進めるので、新規開始と完全に同じ挙動**になる（特別扱い不要）。乱数を使わず入力段位と接触状態だけで決まる＝リプレイ決定的。
- **二度押し系（ダッシュ等）は `Fighter` 内で「方向エッジ＋受付窓」だけで検出でき入力系/CommandDetector は不変**（Task 49 確立）— ダッシュの二度押しは `CommandDetector`（236 等の履歴照合）を拡張せず、`Fighter.update` で `moveDir` の**立ち上がりエッジ**（`moveDir != 0 && moveDir != prevMoveDir`）を見て、直近の同方向タップが受付窓（`DASH_TAP_WINDOW`）内なら `dashFrames` を立てる、で完結する（1 度目はタップ窓 `dashTapWindow` をアームのみ）。ダッシュは方向を離しても継続する確定移動なので歩行分岐の**前**に `dashFrames>0` 分岐を置き、`beginAttack`/`applyHit`/`applyThrow`/`applyThrowTech`/`reset` で `dashFrames=0`（攻撃/被弾でキャンセル）。**バックステップ（後退方向の二度押し）はガード（後退保持）と被るのでダッシュ中は `guarding=false` で抑止**（成立した二度押しを優先。連続後退保持はエッジ 1 回で二度押し不成立＝ガードのまま暴発しない）。撮影は `-x "script=1-1:p1.right;3-9:p1.right"`（frame1 で 1 度目→frame2 で離す→frame3 で 2 度目）で二度押しを再現し `-f 12` で `dash` ラベル＋通常歩行より大きい変位（Aoi で 90px≒歩行 48px の約 2 倍）を撮る。連続押し（`-k p1.right`）は `walk` のまま＝暴発しないことも対比で確認できる。
- **AI にダッシュ等の「ガードを抑止する確定移動」を使わせるときは、ガード反応分岐で自分の移動をキャンセルしてから防御する**（Task 50・Codex 指摘で確立）— `Fighter` はダッシュ中（`dashFrames>0`）に `guarding=false` を強制する（Task 49・確定移動優先）。AI にダッシュ接近を足すと、ダッシュで `GUARD_RANGE` 内へ踏み込んだ瞬間だけ「相手の打撃にはガード」(Task 37) が機能せず、歩き接近なら防げた攻撃を被弾する（＝ガードの抑止条件と AI の防御反応が衝突する死角）。対処：`Fighter` に `cancelDash()`（`dashFrames=0` にするだけの小フック・攻撃/被弾による既存キャンセルと同じ作法・呼ばれない限り挙動不変）を足し、AI のガード反応分岐冒頭で `if (self.isDashing()) self.cancelDash();` してからガードする。ダッシュは AI 自身の選択なので防御のために中断してよい。撮影は人間 P1 を `-x "script=3-30:p1.attack_medium"`（長い「攻撃中」窓＝startup+active+recovery で `isAttacking()` 継続）で固定し、AI(P2) を `-x p2x=880`（>260px＝ダッシュ開始距離）から `GUARD_RANGE`(200) 内へダッシュさせ、`-f 13` 付近で `guard` ラベル＋青オーバーレイを撮る（フレームをずらすと境界での歩き⇔ガードの揺れが映るので、青オーバーレイの出るコマを採用）。**教訓：「ガードを抑止する状態」を持つ機構（ダッシュ・将来のジャンプ移行等）の上に AI の防御反応を載せるときは、反応分岐でその状態を明示的に解除しないと黙って防御が機能しない死角ができる。**
- **「無敵フレーム」は Move の任意 int フィールド＋`Fighter.isInvincible()`＋hit-test 冒頭の defender ガードで足り、当たり判定本体は不変**（Task 53 確立）— リバーサル/対空の無敵は、(1) `Move` に `invincibleFrames`（任意 int・既定 0・getter で負値→0／後方互換）、(2) `Fighter.isInvincible()`＝`attackPhase!=NONE && currentMove.invincibleFrames>0 && attackFrame<=invincibleFrames`（**経過フレームのみ＝乱数なし**）、(3) `CollisionSystem.isHitting`/`hits` の**冒頭**で `if (defender.isInvincible()) return false;`——の 3 点で成立する。`hurtbox()` を null 返しにすると debug 描画・他呼び出しで NPE 連鎖するので**触らず**、hit-test の入口だけで弾く（攻撃側の hitbox は無効化しない＝無敵で抜けつつ反撃が刺さる）。**打撃必殺技（`projectile=false` の必殺技）は既存実装で既に動く**（`startSpecial`→`beginAttack` が通常 hitbox 経路、Core は projectile 時のみ弾生成）ので、無敵対空は「打撃必殺技＋`invincibleFrames`」のデータだけで作れる。可視化は状態ラベルに `[INV]` を付す（`drawNameLabel`・フレームデータ無敵をスクショで確認可能に）。
- **EX を打撃必殺技に広げるときは「Core で ex 判定＋メーター消費・Fighter に exAttack フラグ・hit-test でダメージ倍率」の 3 点**（Task 54 確立、Task 53 の `isInvincible` パターンを踏襲）— 飛び道具 EX（Task 44）は `spawnProjectile(f, move, ex)` でダメージ/サイズを倍化するが弾を生成しない打撃必殺技には効かない。対処：(1) `Fighter.startSpecial` に `boolean ex` オーバーロードを足し、`beginAttack` 後に `exAttack=ex` を立てる（`beginAttack` が毎回 `exAttack=false` にリセット＝通常技/チェーンは非 EX）、(2) `isExAttack()`＝`attackPhase!=NONE && exAttack`（`isInvincible` と同じ「攻撃中のみ・技終了で自動解除」）、(3) `CollisionSystem.activeHitbox` で `f.isExAttack()` なら `damage = round(damage × EX_DAMAGE_MULTIPLIER)`。Core は `boolean ex = special!=null && f.hasFullMeter()` に変え `startSpecial(special, ex)`＋満タンで `spendFullMeter()`（飛び道具/打撃の両方で消費）。可視化は strike 矩形を金色（`EX_PROJECTILE_GLOW`）＋状態ラベル `[EX]`（`[INV]` と同じ suffix 方式・無敵対空 EX は `special:active [INV] [EX]`）。新 `GameConstants`/JSON 不要（既存 `EX_DAMAGE_MULTIPLIER` 流用）。乱数なし＝決定的（メーター有無のみ）。撮影は `-x p1meter=100`（満タン）vs `-x p1meter=0`（空）の 1 変数対比で「金色+[EX]+176／赤+110」を撮る（Task 53 のリバーサル撮影レシピに `-x p1meter` を足すだけ）。打撃 EX のダメージ以外の強化（無敵延長・hitbox 拡大）は将来候補。
- **CHARGE_SHOT（溜め）コマンドの撮影は `InputHistory.CAPACITY=32` と `CHARGE_FRAMES=30` の窓に収める**（Task 53 で踏んだ罠）— 溜め技は「後を 30f 保持 → 前+攻撃」だが、`isCharge` の探索窓は履歴容量 `CAPACITY=32` に頭打ちされる。後を 32f 保持して攻撃すると、攻撃フレーム時点の直近 32 フレーム窓内には後が **29f しか残らず 1 足りない**（古い後フレームが押し出される）。対処：**後をちょうど 30f（frame 1–30）保持 → frame 31 で前 → frame 32 で前+攻撃**と最短で出す（`-x "script=1-30:p1.left;31-50:p1.right;32-32:p1.attack_light"`）。溜め中は方向保持で歩いて間合いがズレるので、溜め側を**画面端 clamp**（`-x p1x=60` 等）に置いて静止させる（Task 31 の clamp 手法）。`<CHARGE (hold 4, 6+A)>` がコマンド読取に出れば成立。後溜めは facing 相対なので、相手が右なら後=左。
- **無敵（防御が成立しなかった/した）の証跡は「同じ相手の攻撃に、通常技 vs 無敵技」の 1 変数対比2枚**（Task 53 確立、Task 31/33/35/36 の対比手法を踏襲）— 防御側を画面端 clamp＋`-x ai=false`、相手の攻撃タイミングを固定し、防御側の行動だけを (1) 通常技 → 被弾（`hitstun`・HP 減・`[INV]` 無し）、(2) 無敵技 → 無敵（`special:active [INV]`・HP 満タン・反撃ヒット）に変える。違いを「出した技」だけにすると無敵の因果が一意。相手の active が無敵窓に重なるよう、無敵技の発生・無敵長と相手の startup/active をフレーム計算して合わせる（例：reversal 発生 f32→無敵 f32–40、相手 medium を f29 開始＝active f36–40 が無敵窓に入る）。
- **AI 難易度は「反応分岐の条件に難易度フラグを足す」だけ＝判断ロジックは不変・既定 HARD で従来挙動**（Task 56 確立）— 難易度は新しい AI ロジックを書かず、既存の各反応分岐（ガード/投げ崩し/投げ抜け/ダッシュ/対空）の `if` 条件に `&& defends`（NORMAL 以上＝`difficulty != EASY`）/ `&& advanced`（HARD のみ）を足して**解放段階を変える**だけで作る。解放されない反応は分岐がスキップされ、下位の接近/通常攻撃へ自然にフォールスルーする（else-if 連鎖なので追加の制御不要）。**既定は HARD（全反応）で、Task 56 時点では Task 55 までの挙動と完全一致**にして、既存の入力リプレイの決定性・既存スクショレシピを壊さない（※その後 Task 57 で HARD に飛び込みを追加したため、Task 57 以降の HARD は Task 56 までと挙動が変わる＝難易度ゲートの仕組み自体は不変だが「HARD＝従来挙動」の同一性は新反応を足すたびに更新される）。**難易度は起動時に固定し試合中に変えない**（per-frame リプレイログに難易度を持たせず format 不変＝リプレイ互換。実行時メニュー切替は将来）。設定は `ScreenshotController.aiDifficulty(fallback)`（生トークンを返し Debug→Battle 依存を作らない・解決は Core の `Difficulty.fromToken`）＋ `-x aidiff=` ＋ `build.gradle` 転送リスト。**ただし難易度は「撮影レシピ」でなく「ゲームプレイ設定」なので、`aiDifficulty()` だけは `isEnabled()` でゲートせず通常起動（`gradle run -Dphantom.screenshot.aidiff=hard`）でも効かせる**——`charId`/`stageId` 等の撮影専用オーバーライド（撮影モード限定）と意図的に違う点（CodeRabbit 指摘で確定）。プロパティ名は転送リスト統一のため `phantom.screenshot.` 名前空間のまま（実体は撮影専用ではない）。撮影は `-x aidiff=easy` vs `hard`（既定）の 1 変数対比で「同じ攻撃に HARD はガード（青オーバーレイ・chip）／EASY はガードせず素通し」を撮る（HUD の `[F2] P2 AI(hard/easy)` 表示も難易度の証跡になる）。乱数は増やさない＝決定的。**教訓：新オーバーライドを足すとき、それが「撮影専用」か「ゲームプレイ設定（通常起動でも効かせたい）」かを区別する。後者は `isEnabled()` ガードを付けない。**
- **AI に必殺技を使わせるときは `self.startSpecial(move)` を直接呼ぶ（コマンド検出を経由しない）＋ 打撃必殺技なら Core 無改修**（Task 55 確立）— AI は `Fighter.update` を直呼びし Core の `updateFighterInput`（コマンド検出・飛び道具生成・メーター消費が起きる場所）を通らない。よって必殺技は **`AiController` が `self.startSpecial(move)` を直接呼ぶ**だけで出る（直後の最終 `self.update(0,false,null,false,false)` が `attackPhase=STARTUP` の技を `advanceAttack` で進める＝人間の「startSpecial→update」フローと同一）。**打撃必殺技（`projectile=false`）は弾生成もメーターも不要なので Core 完全無改修**で成立する（飛び道具を AI に撃たせる場合のみ Core 連携が要る＝別途）。技の選択は**データ駆動**：`self.getDef().getSpecialMoves()` を走査し用途に合う技（対空なら `!isProjectile() && getInvincibleFrames()>0`）を拾う＝キャラ JSON に該当技を足すだけで AI も使う。撮影は AI(P2) を**default のまま**（`ai=false` を付けない）にし、人間 P1 を `-k "p1.up,p1.right"` で飛び込ませて AI の対空を撮る（フレームを sweep し `special:active [INV]`＋縦長 hitbox が空中の相手を捉えるコマを採用）。**注意：AI の通常攻撃分岐に `opponent.isGrounded()` を足さないと、空中の相手に地上通常技を空振りしてクールダウンを浪費し対空の機会を潰す**（Task 55 で踏んだ：最初の撮影で AI が空中相手に light を振り cooldown が残って対空不発だった）。「空中の相手には地上通常技を出さない」を入れると対空が安定する。乱数なし＝決定的（相手の空中/下降/距離と所持技のみ）。
- **AI に「空中の振る舞い（飛び込み）」を持たせるときは、空中状態を表す自前フラグ＋`control()` 先頭の空中専用分岐に隔離し、地上反応はそのままにする**（Task 57 確立）— AI の飛び込み（前方ジャンプ→空中攻撃）は、地上反応（対空/投げ抜け/ガード/接近/通常攻撃）の else-if 連鎖に空中ロジックを混ぜると条件が複雑化する。対処：(1) `jumpingIn`（boolean・踏み切りで true・着地 `isGrounded()` で false・`reset()` でも false）を持ち、(2) `control()` の**先頭**に `if (!self.isGrounded() && jumpingIn) { ... }` の空中専用分岐を置いてドリフト（`moveDir=towardDir`）と空中攻撃発火（下降中 `getVelocityY()<=0` ＋ 間合い ＋ 非攻撃中で `attack=true`）を一手に担わせる。地上反応はすべて空中では `self.canStartAction()==false` 等で自然に無効化されるので、空中の判断が他分岐へ漏れない。踏み切りは別の地上分岐（中距離・クールダウン明け・両者接地）で `jumpReq=true`＋`jumpingIn=true`。**`self.update` の第 2 引数 `jumpPressed` に AI のジャンプ要求を渡す配線（従来 false 固定）だけが Core 側で必要なほぼ無改修**（ジャンプ機構・空中攻撃 Task 32 は流用）。空中攻撃は `attackPhase==NONE` で `attackButton` 発火なので `isAttacking()` 中は再発火せず降り際 1 回。クールダウン明けのみ＝歩き接近と交互で一辺倒にならず、無敵対空（Task 55）持ちに落とされる＝対の攻防。**撮影は AI(P2) を default のまま（`ai=false` を付けない）で中距離（`p2x` を相手＋150〜260px）に置き、`-x debug=true` でフレームを sweep して `attack:active`＋空中（`y>120`・`(air)`）＋赤 hitbox が相手 hurtbox に重なるコマを採用**（地上反応と違い AI が自発的にジャンプするので静止スクショ不可・`-f` 走査で頂点〜降り際を捉える）。乱数なし＝決定的（距離・接地・速度・クールダウンのみ）。
- **AI の防御テク反応（投げ抜け等）は「入力で窓をアームする系」を AI が `arm*()` を直接呼んで再現し Core/Fighter は無改修**（Task 51 確立）— 投げ抜け（Task 36）は人間が投げボタン押下で `armThrowTech()` し窓を立てるが、AI は `updateFighterInput` を通らない（`Fighter.update` 直呼び）ので、`AiController` が**相手の観測状態**（`opponent.isThrowing()`）を見て **`self.armThrowTech()` を直接呼ぶ**だけで成立する（`armThrowTech`/`canTechThrow`/`resolveHit` の既存配線をそのまま使う＝Core/Fighter 不変）。掴みには startup があるので、`isThrowing()` の間**毎フレーム**アームし続ければ active で確実に窓が開く（窓は毎フレーム減衰するため単発アームだと間に合わないことがある）。空中は掴めない(Task 35)ので `self.isGrounded()` 必須、`canStartAction()` を条件にすると AI の攻撃硬直/のけぞり中はアーム不可＝「硬直を投げで狩る」counterplay が自然に残る（完全な投げ無効化を避けられる）。乱数は使わない（決定的・リプレイ両立）。**撮影は「AI on＝抜ける／AI off＝フル投げ」の 1 変数対比**：(1) `-k p1.throw -x p1x=600 -x p2x=700 -f 8` で P1 が AI(P2) を投げ→両者 `tech`・HP 満タン（投げ抜け成立）、(2) 同条件に `-x ai=false` を足すと P2 が反応せず `hitstun`・HP −150（黄ポップアップ＋紫 grab box）。違いを「P2 が AI か静止か」だけにすると反応の因果が一意（Task 35/36 の対比手法を AI 反応へ適用）。投げ startup が短い（fighter001=3F）ので tech は throw 開始の数フレーム後（`-f 8` 付近）に出る。
- **特殊キャンセル（通常技→必殺技）は `startSpecial` の開始ガードを緩めるだけで足り Core は無改修**（Task 47 確立）— 必殺技は Core の `updateFighterInput` が `attackPhase` を見ずに `startSpecial(special)` を呼ぶ（コマンドは攻撃中も `InputHistory` に記録される）ため、キャンセルは **`Fighter.startSpecial` のガードを `canStartAction() || canSpecialCancel()` に拡張するだけ**で成立する（Core 側は変更不要）。チェーンコンボ（Task 45）と前提を共有するので `isCancelableNormal()`（接地・通常技・active/recovery・接触済み）を private ヘルパーに切り出し、`canChainInto`（＋段位上昇）/`canSpecialCancel`（追加条件なし）で共有する。`beginAttack` が `attackConnected`/`attackPhase` をリセットするので多段防止と両立。優先順は Core 既存の「throwReq > 必殺（コマンド+攻撃）> 通常/チェーン」がそのまま効き、必殺成立時に `attackButton=null` で通常を抑止。**注意：`startSpecial` でキャンセル開始するときは `beginAttack` の前に `crouchAttacking`/`aerialAttacking`/`throwing` を false にクリアする**（チェーン経路の `update` 内クリアと同じ作法）。クリアしないと**しゃがみ通常技→必殺技**のキャンセルで `crouchAttacking=true` が必殺技に持ち越され、必殺技の hurtbox が低姿勢のまま・`effectiveAttackHeight` が誤って LOW 扱いになる（CodeRabbit 指摘で確定）。新規発動（`attackPhase==NONE`）では既に false なので no-op。
- **特殊キャンセルの撮影は「弱P を当ててから 236+攻撃のコマンドを通常技 active/recovery 中に入力」する**（Task 47 確立）— コマンド入力（方向）は攻撃中も履歴に積まれるので、弱Pの命中後にキャンセル受付窓（active/recovery）で 236 モーション＋攻撃を完成させる。`-x ai=false -x p1x=600 -x p2x=700 -x "script=1-1:p1.attack_light;4-8:p1.down;7-10:p1.down+p1.right;9-13:p1.right;13-13:p1.attack_light" -f 24` → `2 HITS!`＋弱P 50 → 波動拳 108（=120×0.9・コンボ補正も乗る）。近接（p2x=700）なので飛び道具がほぼ即着弾し hitstun 内に当たって 2 ヒット目が成立する。必殺の攻撃トリガは任意のボタン（`attack_light` でよい＝コマンド成立時は通常技でなく必殺が優先）。
- **コンボダメージ補正は「`comboCount` 加算後に倍率を掛ける」＝1 ヒット目が等倍になる**（Task 46 確立）— スケーリングを `applyHit`/`applyThrow` 内で `comboCount = ...`（加算）の**後**に `applyDamage(scaledComboDamage(damage))` で適用すると、1 ヒット目（`comboCount==1`）は等倍・2 ヒット目以降（`>=2`）が減衰、と自然に分岐できる（`scaledComboDamage` は `comboCount<=1` で素通し）。倍率は `max(MIN, 1-(n-1)×STEP)` の決定的計算（乱数なし＝リプレイ両立）で、knockback/hitstun は不変＝与ダメージ量だけ補正。**ダメージ数値ポップアップは「適用前後の HP 差」方式（既出）なので補正後の値を自動表示**し、ポップアップ側は一切触らなくてよい（chain 撮影レシピをそのまま撮ると 50/80/130 → 50/72/104 に変わる）。ガードの chip は `comboCount` 非加算なので補正対象外（`applyGuard` は別経路）。
- **チェーンコンボの撮影は「弱→中→強を connect 直後の active/recovery で順に入力」する**（Task 45 確立）— キャンセルは接触済みが条件なので、各ボタンは前段が当たった後（active 中）に押す。Aoi（`light` 5/4/10・`medium` 8/6/16・`heavy` 14/5/28）で `-x ai=false -x p1x=600 -x p2x=680 -x "script=1-1:p1.attack_light;8-8:p1.attack_medium;18-18:p1.attack_heavy" -f 36` → 相手頭上に `3 HITS!`＋ダメージポップアップ `50/80/130`（合計 260＝HP 差と一致）が積み上がる。停止2体は最初の active で当たるので、前段の active 区間に次ボタンのエッジを置けば確実にキャンセルが乗る（同技連打 #39 と違い**異なるボタン**を順に出すのがコンボ成立の鍵）。
- **`PhantomNexusGame` 等で `java.lang.Character` を使うとコンパイルエラー**（feature/replay 確立）— 症状：`Character.isDigit(c)` が「シンボルを見つけられません: メソッド isDigit(char)」で落ちる。原因：このクラスは独自型 `import com.phantomnexus.shared.types.Character;`（キャラ定義 POJO）を import しており、`Character` がそちら（`isDigit` を持たない）に解決される＝**java.lang.Character がシャドウされる**。対処：文字判定は `c >= '0' && c <= '9'` のように素の比較で書くか、`java.lang.Character.isDigit(c)` と完全修飾する。`shared.types.Character` を import している全クラス（Core 等）に共通。
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
- **`gradlew run` のバックグラウンド起動は `Start-Process` を使う** — `cmd.exe /c "gradlew.bat run"` を Bash ツール経由で投げると cmd が対話モードで空振りし起動しないことがある。`Start-Process -FilePath "gradlew.bat" -ArgumentList "run" -WorkingDirectory <repo>` で detached 起動し、`Get-Process | Where MainWindowTitle -like '*Phantom Nexus*'` をポーリングして窓が出るまで待つのが確実。

#### 過渡的な状態（ジャンプ等）のスクショ撮影法

ジャンプ・攻撃 startup/active・のけぞり・KO のように **一瞬しか映らない状態**は、`capture-app-window.ps1` の前面化後 600ms 待機では頂点を撮れない（例：ジャンプ滞空は約 `2·jumpPower/GRAVITY` フレーム ≈ 0.67 秒、頂点 ≈ 0.33 秒後）。対処：

1. 対象ウィンドウを `SetForegroundWindow` で前面化（GLFW 窓に入力を届けるため必須）。
2. `[System.Windows.Forms.SendKeys]::SendWait("w")` などでトリガキーを送出（`isKeyJustPressed` 系の立ち上がり発動はワンショットで足りる）。
3. **直後から ~80ms 間隔で複数フレームを連写**し、HUD の状態値（例：`y=` の高さ）が目的に最も近いコマを採用。`CopyFromScreen` は数十 ms と速いので頂点付近を捕捉できる。
4. 連写コマを Read で確認 → 採用コマを `docs/screenshots/<N>-<短い名>.png` に確定し、一時コマは削除する。

### 撮影手順（macOS — 将来の対応用に残置）

```sh
./gradlew run &
sleep 8
scripts/capture-app-window.sh <process-name> docs/screenshots/<N>-<短い名>.png
kill %1
```

- `scripts/capture-app-window.sh` は Swift + `screencapture`。macOS の画面収録権限が必要。**Windows では動作しないため `.ps1` を使う。**

### 撮影手順（ヘッドレス Linux ＝ Claude Code on the web / CI）

ウィンドウシステムの無いリモート環境では、外部キャプチャの代わりに **アプリ内スクショモード** を使う。

```sh
# Xvfb 起動・ソフトウェア GL 設定・アプリ起動・PNG 書き出し・終了まで一括
scripts/capture-app-screenshot-linux.sh -o docs/screenshots/<N>-<短い名>.png -f 90
```

- 仕組み：`Xvfb`（仮想ディスプレイ）＋ Mesa `swrast`/`llvmpipe`（`LIBGL_ALWAYS_SOFTWARE=1` / `GALLIUM_DRIVER=llvmpipe` / `MESA_GL_VERSION_OVERRIDE=3.3`）で GLFW 窓を作り、`-Dphantom.screenshot.path=...` `-Dphantom.screenshot.frame=...` を渡して起動する。`ScreenshotController`（`GameRuntime/Debug`）が指定フレームで `ScreenUtils.getFrameBufferPixmap` → `PixmapIO.writePNG(...,flipY=true)` し、`Gdx.app.exit()` で自動終了する。
- **apt 追加は不要**（基盤イメージに Xvfb / Mesa 同梱。万一 Xvfb が無い場合のみ SessionStart フックがベストエフォートで導入）。
- 過渡状態（ジャンプ頂点・攻撃 active 等）は `-f` の値を変えて狙う（既定 90 ≒ 1.5 秒@60fps の静止）。`-W`/`-H` で仮想解像度も変更可。
- **入力を伴う過渡状態は `-k`（`phantom.screenshot.hold`）で起動時から押下注入する**。書式は `p1.up`・`p2.left`・`attack`（接頭辞省略時は p1）をカンマ/空白区切り。例：ジャンプ頂点は `-k p1.up -f 21`（頂点 ≒ `jumpPower/GRAVITY` フレーム後）。立ち上がり発動（ジャンプ/攻撃）は最初の 1 フレームだけ just-pressed として消費され、以降は押しっぱなし扱いになる（`PlayerInput.setForcedHold`）。
- **近接が必要な過渡状態（被弾・接触など）は `-x p1x=<X> -x p2x=<X>` で初期中心 X をオーバーライドする**（`phantom.screenshot.p1x`/`p2x`）。既定 spawn（420/860）は間合いが広く攻撃の active 区間（数フレーム）に相手へ届かないため、被弾スクショは両者を近づけて撮る。例：`-k attack -x p1x=600 -x p2x=720 -f 14` で P1 のパンチが P2 に当たり HP 減少＋hitstun を撮れる。`-x` は `-Dphantom.screenshot.<key>=<val>` に展開され、`Infra/Build/build.gradle` の run タスク転送リストに `p1x`/`p2x` を追加済み（新プロパティを足す時は同リストも要更新）。
- **撮影オーバーライド一覧**（`scripts/capture-app-screenshot-linux.sh` の `-x key=val` → `-Dphantom.screenshot.<key>`。新規追加時は `Infra/Build/build.gradle` の run タスク転送リストにも要追記）：
  - `p1x` / `p2x`：初期中心 X（近接が必要な被弾・接触の再現）
  - `timelimit`：ラウンド制限時間（秒）。タイムアップ結果バナーを短時間で撮る（例 `-x timelimit=1 -f 80`）
  - `debug=true`：デバッグ当たり判定表示を起動時 ON（F1 トグルの代替）
  - `ai=false`：P2 の AI を無効化（コマンド/飛び道具の撮影で P2 を静止させる）
  - `stage=<id>`：読み込むステージ ID のオーバーライド（既定 `stage001`）。背景の撮り分けに使う（Task 40）
  - `p1char=<id>` / `p2char=<id>`：読み込むキャラ ID のオーバーライド（既定 `fighter001` / `fighter002`）。新キャラの撮り分けに使う（Task 41。`stage=` のキャラ版）
  - `intro=true`：ラウンド開始イントロ（"ROUND N"/"FIGHT!"・Task 42）を撮影モードでも有効化（既定はスキップ＝既存レシピの後方互換）。開始演出を撮るとき以外は付けない
  - `p1meter=<値>` / `p2meter=<値>`：初期必殺技ゲージ量（0〜100）のオーバーライド（Task 44）。EX 必殺技（満タンで強化）の見え方を貯め直しなしで撮る用（例：`-x p1meter=100`）
  - `aidiff=easy|normal|hard`：P2 の AI 難易度（Task 56・既定 HARD＝全反応）。難易度別の反応の見え方を撮り分ける（例：`-x aidiff=easy` でガード反応が消える）。**唯一の例外：これだけは撮影モードに依らず通常起動でも効く**（ゲームプレイ設定のため・他の `-x` は撮影モード限定）
  - `script=start-end:tok+tok;...`：タイムド入力スクリプト（コマンド技の再現）。例（波動拳）：`-x "script=1-12:p1.down;8-18:p1.down+p1.right;19-30:p1.right;22-22:p1.attack" -f 42`。区間は重ねてよく、各フレームで和集合を `setForcedHold`。攻撃の立ち上がりエッジは押下開始フレームのみ発火するため、連続フレームに置いても発火は 1 回（基礎 `-k` hold と script の併用でも基礎 hold が毎フレーム再発火しない。`PlayerInput.setForcedHold` が「前フレーム未押下 → 今フレーム押下」のアクションにのみエッジを供給する仕様に修正済み）。
- **強制エッジは 1 フレーム 1 回しか消費されない** — `PlayerInput.isPressed`（forced 時）は `forcedEdgePending.remove` で消費するため、同一フレームに 2 回呼ぶと 2 回目は false。Core は攻撃/ジャンプ入力を 1 回だけ読み、その値を `Fighter.update` と入力履歴の両方に使い回す（`updateFighterInput`）。
- 撮影後は **必ず Read ツールで PNG を目視**（黒画面・崩れが無いか）。ALSA の `cannot find card` 警告は音源無しによる無害ログ。
- 注意：対話的に動かす確認はローカル Windows が確実。web は静止画前提。

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

- `./gradlew build` / `./gradlew test`（ヘッドレス）は web リモート環境で実行可能。セッション開始時に `.claude/hooks/session-start.sh`（SessionStart フック）が依存と JDK17 toolchain をウォームアップする。
- **GUI 起動とスクショも web で可能になった**（旧「不可」を撤回）。リモート Linux はウィンドウシステムが無いが、**Xvfb（仮想ディスプレイ）＋ Mesa ソフトウェア GL（`swrast`/`llvmpipe`）** の上で LWJGL3/GLFW を動かし、**アプリ自身がフレームバッファを PNG に書き出して自動終了**する方式で撮影する（`scripts/capture-app-screenshot-linux.sh` + `GameRuntime/Debug/.../ScreenshotController.java`）。実画面どおりの絵が得られるため、Codex/人間レビュー用の証跡を web セッションだけで完結できる。
- ただし **対話的なプレイ確認（キーを押して動かす）はローカル Windows の方が確実**。web のスクショは「指定フレームでの静止画」前提。過渡状態（ジャンプ頂点・攻撃 active 等）は `-f <フレーム番号>` で撮るタイミングを合わせる。
