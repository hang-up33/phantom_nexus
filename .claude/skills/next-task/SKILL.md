---
name: next-task
description: 次の 1 タスクを実装する標準ワークフロー。設計書の「1 タスクずつ・ビルド成功を完了基準とする」を守る。「次のタスクを進めて」「Task N をやる」「次に進む」のような指示で発火。
---

# 次タスク実装ワークフロー

本プロジェクトは **1 タスクずつ、ビルド成功を完了基準** として進める。本スキルはその標準手順。

## タスク順（第一設計書。1 タスクずつ・`./gradlew build` 成功を完了基準とする）

進捗の最新は **README.md の進捗表** が正。番号は設計書タスク番号 = ブランチ `task/<N>-<短い名>` の `<N>`。

| Phase | # | タスク | 主モジュール |
|---|---|---|---|
| 0 基盤準備 | 1 | リポジトリ作成 / Bootstrap（テンプレ適用・Docs 骨格） | （済） |
| 1 基盤 | 2 | Gradle プロジェクト作成 | Infra/Build |
| | 3 | LibGDX 初期画面作成 | GameRuntime/Core,Rendering |
| | 4 | ウィンドウ表示 | GameRuntime/Core |
| 2 入力・描画・移動 | 5 | 入力処理作成 | GameRuntime/Input |
| | 6 | キャラクター描画 | Rendering, Shared/Types |
| | 7 | キャラクター移動 | Input, Battle |
| | 8 | ジャンプ処理 | Battle |
| | 9 | アニメーション管理 | Rendering |
| 3 戦闘・判定 | 10 | HP ゲージ表示 | Battle |
| | 11 | 攻撃処理（startup/active/recovery） | Battle |
| | 12 | 当たり判定処理（hit/hurt/push） | GameRuntime(Collision) |
| | 13 | ダメージ処理（のけぞり/hitstun） | Battle |
| | 14 | ラウンド勝敗判定 | Battle |
| 4 データ駆動（MVP核） | 15 | キャラクター JSON 定義 | Shared/Schema |
| | 16 | キャラクター JSON 読み込み | Shared/Schema |
| | 17 | ステージ表示 | GameRuntime, Assets |
| | 18 | デバッグ当たり判定表示 | GameRuntime/Debug |
| ── MVP ゲート（設計書 MVP 9 条件を充足）── |
| 5 コマンド・必殺技・AI | 19 | コマンド入力（波動拳/溜め/同時押し） | Shared, Input |
| | 20 | 必殺技ステート | Battle |
| | 21 | 簡易 AI | GameRuntime |
| 6 検証・整備 | 22 | キャラクター追加検証（2 体目 JSON） | Assets/Characters |
| | 23 | ドキュメント整備 | Docs |

- データ仕様変更を伴うタスク（15,16,19,22）は **同 PR で `docs/DataFormat.md` を更新**。
- 戦闘仕様変更を伴うタスク（10〜14,20,21）は **同 PR で `docs/BattleSystem.md` を更新**。
- 描画/移動（6〜9）は JSON 読込（15,16）より前。値は最初から `Shared/Types.Character` 経由で扱い、Task 16 で供給元を JSON ローダに差し替えるだけで済むようにする。

## 適用タイミング（When to Use）

- ユーザーが「次のタスクを進めて」「Task N を実装」「次に進む」と指示した時
- 直前のタスクが完了済み・ビルドが緑のときに次の一歩を踏み出す時

## 手順（How It Works）

1. **進捗確認**
   - [CLAUDE.md](../../../CLAUDE.md)「現在のフェーズ」セクションと README.md の進捗表を Read
   - 直近の git log を確認
   - 未着手の最も若い番号のタスクを次のタスクとして特定

2. **タスク宣言**
   - ユーザー宛に「<タスク識別子>: <名前> に取り組みます。完了基準は <X>」と冒頭で明示

3. **ブランチ作成**
   ```sh
   git checkout main && git pull --ff-only
   git checkout -b task/<N>-<短い名>
   ```

4. **実装範囲の限定**
   - 1 タスクのみ。「ついで」リファクタは禁止
   - 影響範囲を箇条書きで列挙してから着手

5. **実装**
   - 設計書ルール（[CLAUDE.md](../../../CLAUDE.md) の「ルール（Must Always / Must Never）」）を逐条守る
   - 特に：データモデルの単一の真実を維持 / フォルダ変更禁止 / 既存コード削除禁止

6. **ビルド検証**
   ```sh
   ./gradlew build
   ```
   - 失敗時は [build-error-resolver](../../agents/build-error-resolver.md) エージェントに委譲

7. **動作確認 + スクリーンショット**
   - 実行可能なタスクなら起動して目視（または最小の自動検証）
   - **GUI に変化があるタスクは必ずスクリーンショットを撮る**。本プロジェクトの既定は **Windows**（`.ps1`）。詳細・macOS 手順は [CLAUDE.md](../../../CLAUDE.md)「動作証跡スクリーンショット運用」を参照：
     ```powershell
     Start-Process -FilePath ".\gradlew.bat" -ArgumentList "run"
     Start-Sleep -Seconds 8
     powershell -ExecutionPolicy Bypass -File scripts/capture-app-window.ps1 -WindowTitle "Phantom Nexus" -OutPath "docs/screenshots/<N>-<短い名>.png"
     ```
     （macOS では `scripts/capture-app-window.sh <process-name> docs/screenshots/<N>-<短い名>.png` を使う。`.sh` は macOS 専用で Windows では動作しない）
   - 撮ったスクショは Read ツールで内容を確認してから採用する（黒画面・他ウィンドウが混入していないか）
   - GUI 変化が無いタスク（CLI / バックエンド / ロジックのみ）はスクショ省略可

8. **進捗反映**
   - README.md の進捗表の該当行を ⬜ → ✅ に更新

9. **継続的改善（kaizen）**
   - [kaizen-close](../kaizen-close/SKILL.md) スキルを実行し、新たな知見を CLAUDE.md / README / メモリに反映

10. **コミット + push + PR 作成**
    - [codex-pr](../codex-pr/SKILL.md) スキルに従い、`<タスク識別子>: <短い説明>` コミット → push → `gh pr create`（ready-for-review）→ Codex 自走レビューループ
    - PR URL を完了報告に含める
    - **マージは Codex レビュー後にユーザーが行う**（Claude は勝手にマージしない）

## やってはいけないこと（Must Never）

- 複数タスクを一度に進める
- 設計書外のフォルダを作る
- フレームワーク / ライブラリを独断で導入する
- main に直接 push / コミットする（必ず `task/<N>-<短い名>` ブランチ + PR）
- PR を勝手にマージする（マージはユーザーの責任）

## 例（Example）

```
ユーザー: 次のタスクを進めて
あなた  : README の進捗を確認 → Task 2 (ログイン画面) が未着手
         → 「Task 2: ログイン画面 に取り組みます。完了基準は ./gradlew build 成功 + 画面表示」
         → git checkout -b task/2-login-form
         → 実装
         → ./gradlew build → 成功
         → 起動 → 画面を目視（GUI 変化があればスクショ）
         → README 進捗を ✅ に更新
         → kaizen-close で学びをドキュメントに反映
         → codex-pr スキルへ引き継ぎ（commit → push → gh pr create → 自走ループ）
         → 完了報告に PR URL を含める（マージは Codex レビュー後にユーザー）
```
