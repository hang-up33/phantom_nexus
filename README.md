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

## 操作方法

| 操作 | P1 | P2（人間時） |
|---|---|---|
| 移動 | A / D | ← / → |
| ジャンプ | W | ↑ |
| しゃがみ | S | ↓ |
| 弱攻撃（light） | F | Numpad 1 |
| 中攻撃（medium） | G | Numpad 2 |
| 強攻撃（heavy） | H | Numpad 3 |
| 必殺技（波動拳） | ↓→ + 攻撃（236+任意攻撃ボタン） | 同左 |

- **F1**：デバッグ当たり判定表示の ON/OFF（push=青 / hurt=緑 / hit=赤）
- **F2**：P2 の AI ⇄ 人間 切替（既定は P2=AI）
- 勝敗：制限時間内に相手の HP を 0 にすれば KO。時間切れは HP 残量が多い側の勝ち。

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
| 1 | リポジトリ作成 / Bootstrap（テンプレ適用・docs 骨格） | ✅ 完了 |
| 2 | Gradle プロジェクト作成 | ✅ 完了 |
| 3 | LibGDX 初期画面作成 | ✅ 完了 |
| 4 | ウィンドウ表示 | ✅ 完了 |
| 5 | 入力処理作成 | ✅ 完了 |
| 6 | キャラクター描画 | ✅ 完了 |
| 7 | キャラクター移動 | ✅ 完了 |
| 8 | ジャンプ処理 | ✅ 完了 |
| 9 | アニメーション管理 | ✅ 完了 |
| 10 | HP ゲージ表示 | ✅ 完了 |
| 11 | 攻撃処理 | ✅ 完了 |
| 12 | 当たり判定処理 | ✅ 完了 |
| 13 | ダメージ処理 | ✅ 完了 |
| 14 | ラウンド勝敗判定 | ✅ 完了 |
| 15 | キャラクター JSON 定義 | ✅ 完了 |
| 16 | キャラクター JSON 読み込み | ✅ 完了 |
| 17 | ステージ表示 | ✅ 完了 |
| 18 | デバッグ当たり判定表示 | ✅ 完了 |
| — | **MVP ゲート（設計書 MVP 9 条件を充足）** | — |
| 19 | コマンド入力 | ✅ 完了 |
| 20 | 必殺技ステート | ✅ 完了 |
| 21 | 簡易 AI | ✅ 完了 |
| 22 | キャラクター追加検証 | ✅ 完了 |
| 23 | ドキュメント整備 | ✅ 完了 |
| 24 | 複数技の JSON 化（弱/中/強 + 複数必殺技） | ✅ 完了 |
| 25 | しゃがみ（Crouch） | ✅ 完了 |
| 26 | 複数ラウンド制（ベスト・オブ 3） | ✅ 完了 |

状態：⬜ 未着手 / 🟦 進行中 / ✅ 完了

---

## 実装済み機能（タスク 1〜26）

- **基盤**：LibGDX/LWJGL3 ウィンドウ・固定仮想解像度（1280×720）・60fps 固定ステップ
- **操作/移動**：キーボード入力抽象・左右移動・ジャンプ（重力/接地）・向き自動追従・しゃがみ（DOWN 押しで半高さ hurtbox）
- **アニメーション**：idle / walk / jump / attack / hitstun / crouch の状態機械（tick ベース、プレースホルダ可視化）
- **戦闘**：HP ゲージ・通常攻撃（startup/active/recovery）・当たり判定（hit/hurt/push）・ダメージ/のけぞり/knockback・ラウンド勝敗（KO/タイムアップ）
- **複数ラウンド制（Task 26）**：先取 2 ラウンド（ベスト・オブ 3）・インターバルバナー・勝利ドット（HP バー横）・マッチ結果表示（勝者 + スコア）
- **データ駆動**：キャラ/ステージを外部 JSON から読込（`Shared/Schema` の単一の真実 + バリデーション）
- **複数技（Task 24）**：`normalMoves[]`（弱/中/強 3 ボタン）・`specialMoves[]`（複数コマンド技）を JSON で定義。P1: F/G/H、P2: Numpad 1/2/3
- **コマンド技/必殺技**：入力履歴＋コマンド検出（波動拳 236+A 等）・飛び道具の必殺技
- **AI**：接近して間合いで攻撃する簡易 AI（P2 既定）
- **デバッグ**：当たり判定オーバーレイ（F1）・ヘッドレス自動スクショ

動作証跡は [docs/screenshots/](docs/screenshots/) を参照（各タスクの静止画を収録）。

---

## MVP の完成条件（第一設計書）— 達成済み

2 体表示 / キーボードで移動・ジャンプ・攻撃 / HP ゲージ / 攻撃・食らい判定 / 1 ラウンドの勝敗判定 / 外部 JSON からのキャラ読込 / 通常攻撃の定義 / ステージ背景 / デバッグ当たり判定表示。
→ **Task 18 + Task 22 で全条件を充足済み**。以降の Task 19〜21（コマンド技・必殺技・AI）・Task 23（ドキュメント整備）も完了。

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
