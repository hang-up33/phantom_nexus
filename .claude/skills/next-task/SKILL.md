---
name: next-task
description: 次の 1 タスクを実装する標準ワークフロー。設計書の「1 タスクずつ・ビルド成功を完了基準とする」を守る。「次のタスクを進めて」「Task N をやる」「次に進む」のような指示で発火。
---

# 次タスク実装ワークフロー

本プロジェクトは **1 タスクずつ、ビルド成功を完了基準** として進める。本スキルはその標準手順。

<!-- {{TASK_LIST_PLACEHOLDER}}
  ここを実プロジェクトのタスク順 / フェーズ管理に書き換える。例：
    1. プロジェクト初期化 ✅
    2. データモデル定義
    3. ログイン画面
    ...
  タスク順序が動的（バックログから随時引く運用）なら、このコメントごと削除して
  「README の進捗表を参照」と書き換えてもよい。
-->

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
   git checkout {{DEFAULT_BRANCH}} && git pull --ff-only
   git checkout -b {{BRANCH_PREFIX}}/<N>-<短い名>
   ```

4. **実装範囲の限定**
   - 1 タスクのみ。「ついで」リファクタは禁止
   - 影響範囲を箇条書きで列挙してから着手

5. **実装**
   - 設計書ルール（[CLAUDE.md](../../../CLAUDE.md) の「ルール（Must Always / Must Never）」）を逐条守る
   - 特に：データモデルの単一の真実を維持 / フォルダ変更禁止 / 既存コード削除禁止

6. **ビルド検証**
   ```sh
   {{BUILD_CMD}}
   ```
   - 失敗時は [build-error-resolver](../../agents/build-error-resolver.md) エージェントに委譲

7. **動作確認 + スクリーンショット**
   - 実行可能なタスクなら起動して目視（または最小の自動検証）
   - **GUI に変化があるタスクは必ずスクリーンショットを撮る**（撮影手順は [CLAUDE.md](../../../CLAUDE.md)「動作証跡スクリーンショット運用」を参照）：
     ```sh
     {{APP_BINARY_HINT}} &
     sleep 2
     scripts/capture-app-window.sh <process-name> {{SCREENSHOT_DIR}}/<N>-<短い名>.png
     kill %1
     ```
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
- {{DEFAULT_BRANCH}} に直接 push / コミットする（必ず `{{BRANCH_PREFIX}}/<N>-<短い名>` ブランチ + PR）
- PR を勝手にマージする（マージはユーザーの責任）

## 例（Example）

```
ユーザー: 次のタスクを進めて
あなた  : README の進捗を確認 → Task 2 (ログイン画面) が未着手
         → 「Task 2: ログイン画面 に取り組みます。完了基準は {{BUILD_CMD}} 成功 + 画面表示」
         → git checkout -b {{BRANCH_PREFIX}}/2-login-form
         → 実装
         → {{BUILD_CMD}} → 成功
         → 起動 → 画面を目視（GUI 変化があればスクショ）
         → README 進捗を ✅ に更新
         → kaizen-close で学びをドキュメントに反映
         → codex-pr スキルへ引き継ぎ（commit → push → gh pr create → 自走ループ）
         → 完了報告に PR URL を含める（マージは Codex レビュー後にユーザー）
```
