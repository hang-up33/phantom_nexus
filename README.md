# Phantom Nexus

**MUGEN ライクな 2D 格闘ゲーム基盤**。キャラクター・ステージ・技・当たり判定・AI を **外部データ（JSON）** として追加・編集できる、拡張性の高い 2D 格闘ゲームエンジンを目指す。

- 技術スタック：**Java / LibGDX / Gradle**、データ形式 **JSON**（LibGDX 組込み）
- 対象：**Windows PC**（将来 Linux / macOS）
- 開発運用：実装は **Claude Code**、レビューは **ChatGPT Codex（GitHub App）**
- 開発指針・ルールは [CLAUDE.md](CLAUDE.md)、Codex 向け指示は [AGENTS.md](AGENTS.md) を参照

---

## ビルドと実行

```sh
./gradlew build      # 完了基準（コンパイル + テスト）
./gradlew test       # テストのみ
./gradlew run        # デスクトップ（LWJGL3）で起動
```

JDK は Gradle toolchain で Java 17 を自動取得する（要ネットワーク）。Windows は `gradlew.bat` でも可。

---

## フォルダ構成（第一設計書準拠 — 変更禁止）

```
GameRuntime/   Core / Rendering / Input / Battle / Debug    … ゲーム本体（Java ソース）
Assets/        Characters / Stages / Sounds / Effects       … 実行時リソース（外部キャラ JSON 等）
Shared/        Schema / Types / Constants                   … ★データ I/O の唯一の真実★
Tools/         CharacterViewer / HitboxEditor               … 開発補助（将来）
Infra/Build/                                                … Gradle ビルドロジックの実体
docs/          DataFormat.md / BattleSystem.md / screenshots
```

> Gradle ラッパー（`gradlew*`）と最小シム（root の `settings.gradle` / `build.gradle`）のみ root に置き、実体は `Infra/Build/` に委譲する（[CLAUDE.md](CLAUDE.md) 参照）。

---

## 進捗（第一設計書タスク）

1 タスク = 1 ブランチ `task/<N>-<短い名>` = 1 PR。完了基準は `./gradlew build` 成功。

| # | タスク | 状態 |
|---|---|---|
| 1 | リポジトリ作成 / Bootstrap（テンプレ適用・docs 骨格） | 🟦 進行中 |
| 2 | Gradle プロジェクト作成 | ⬜ |
| 3 | LibGDX 初期画面作成 | ⬜ |
| 4 | ウィンドウ表示 | ⬜ |
| 5 | 入力処理作成 | ⬜ |
| 6 | キャラクター描画 | ⬜ |
| 7 | キャラクター移動 | ⬜ |
| 8 | ジャンプ処理 | ⬜ |
| 9 | アニメーション管理 | ⬜ |
| 10 | HP ゲージ表示 | ⬜ |
| 11 | 攻撃処理 | ⬜ |
| 12 | 当たり判定処理 | ⬜ |
| 13 | ダメージ処理 | ⬜ |
| 14 | ラウンド勝敗判定 | ⬜ |
| 15 | キャラクター JSON 定義 | ⬜ |
| 16 | キャラクター JSON 読み込み | ⬜ |
| 17 | ステージ表示 | ⬜ |
| 18 | デバッグ当たり判定表示 | ⬜ |
| — | **MVP ゲート（設計書 MVP 9 条件を充足）** | — |
| 19 | コマンド入力 | ⬜ |
| 20 | 必殺技ステート | ⬜ |
| 21 | 簡易 AI | ⬜ |
| 22 | キャラクター追加検証 | ⬜ |
| 23 | ドキュメント整備 | ⬜ |

状態：⬜ 未着手 / 🟦 進行中 / ✅ 完了

---

## MVP の完成条件（第一設計書）

2 体表示 / キーボードで移動・ジャンプ・攻撃 / HP ゲージ / 攻撃・食らい判定 / 1 ラウンドの勝敗判定 / 外部 JSON からのキャラ読込 / 通常攻撃の定義 / ステージ背景 / デバッグ当たり判定表示。
→ **Task 18 + Task 22** 完了時点で全条件を満たすことを確認する。

---

## 開発ワークフロー

```
「次のタスクを進めて」
  → next-task（ブランチ作成 → 実装 → ./gradlew build 緑 → 動作確認/スクショ → README 進捗更新）
  → kaizen-close（学びを CLAUDE.md / README / メモリへ反映）
  → codex-pr（commit → push → gh pr create → @codex review → 自走レビューループ）
  → Codex のクリーン後にユーザーがマージ
```

詳細は [.claude/skills/](.claude/skills/) と [docs/workflow.md](docs/workflow.md)。
