# BattleSystem — 戦闘仕様

本書は Phantom Nexus の戦闘ロジック仕様。戦闘仕様を変える PR では本書を同時に更新する（[CLAUDE.md](../CLAUDE.md) のルール）。
実装は `GameRuntime/Battle` と当たり判定（Collision）が担当し、データは `Shared/Types` 経由で受け取る。

> 本書は **Phase 3〜5（Task 10〜14, 20, 21）で各タスク完了時に随時更新**する。以下は MVP のドラフト。

---

## 基本ループ

60fps 固定ステップを基準（`Shared/Constants`）。1 フレームの処理順：

```
入力取得 → ステート更新 → 物理（移動/重力/接地） → 当たり判定 → ダメージ適用 → 勝敗判定 → 描画
```

---

## ステート（MVP）

`idle / walk / jump / attack / hitstun(のけぞり) / KO`。
攻撃は **startup / active / recovery** の 3 区間を持ち、`active` 区間のみ hitbox が有効。

| 区間 | 内容 |
|---|---|
| startup | 技を出してから攻撃判定が出るまで |
| active | hitbox が有効（この間に hurtbox と重なるとヒット） |
| recovery | 技後の硬直（行動不能） |

---

## 移動 / 向き（Task 7）

- **左右移動**：押下中の左右入力に応じて中心 X を `walkSpeed`（px/frame）で増減する。入力の読み取りは Core が担い、`Fighter.update(moveDir)` には方向（-1/0/+1）のみ渡す（入力配線と分離し AI 差し替え・テストを容易にする）。
- **画面端クランプ**：キャラ矩形が画面外に出ないよう中心 X を `[width/2, WORLD_WIDTH - width/2]` に制限する。
- **向き**：毎フレーム相手側を向く（相手の X が自分より大きければ右向き）。
- 押し合い（pushbox）は Task 12。

---

## ジャンプ / 重力（Task 8）

- **入力**：ジャンプは押下中ではなく**立ち上がりエッジ**（`isKeyJustPressed`）で発動する。Core は `InputAction.UP` の立ち上がりを `Fighter.update(moveDir, jumpPressed)` の `jumpPressed` として渡す（左右移動の押下中検出とは別系統）。
- **発動条件**：**接地中（`grounded`）のみ**ジャンプ可。空中での再ジャンプ（多段ジャンプ）は MVP では不可。発動時は垂直速度 `velocityY` に `Character.jumpPower`（px/frame, 上向き正）を与えて離地する。
- **重力 / 積分**：毎フレーム `velocityY -= GRAVITY`（`Shared/Constants.GRAVITY`, px/frame²）し、足元 Y に `velocityY` を加算する（明示オイラー積分・60fps 固定ステップ基準）。
- **着地判定**：足元 Y が `GROUND_Y` 以下に達したら Y を `GROUND_Y` にスナップし、`velocityY = 0`・`grounded = true` に戻す。
- **空中横移動**：MVP では空中でも左右移動を許可する（地上と同じ `walkSpeed`）。
- 滞空高さ・時間は `jumpPower` と `GRAVITY` で決まる（頂点高さ ≈ `jumpPower² / (2·GRAVITY)`、滞空 ≈ `2·jumpPower / GRAVITY` フレーム）。値は将来 JSON（Task 16）で調整可能にする。

---

## アニメーション管理（Task 9）

描画は `GameRuntime/Rendering` の `FighterAnimator` + `AnimationState` が担当する（戦闘ロジックではなく描画状態だが、戦闘ステートと密結合するため本書に記す）。

- **状態導出**：`Fighter` の実行時状態から毎フレーム `idle / walk / jump` を導出する（優先順：空中 > 歩行 > 待機）。歩行検出のため `Fighter` は直近フレームの移動方向 `moveDir` を保持し `isWalking()`（接地中かつ `moveDir != 0`）を公開する。攻撃（Task 11）・のけぞり・KO の各ステートは、確定後に同様の優先順へ織り込む。
- **フレーム進行**：`AnimationState` が状態ごとに `frameCount / ticksPerFrame / looping` を持ち、状態継続中の経過 tick から現在フレーム番号を算出する。状態遷移時に経過 tick を 0 リセットして先頭から再生する。
- **時間基準**：**1 回の更新 = 1 tick**（60fps 固定ステップ。物理積分と同じく delta 時間に依存しない）。これによりヘッドレススクショ（`-f <フレーム>`）でアニメ位相が決定的になる。後続のアニメ系（攻撃の startup/active/recovery 表示等）も同じ tick 基準に揃える。
- **MVP の可視化**：スプライト素材は未導入のため、プレースホルダ矩形に「縦ボブ（待機=呼吸 / 歩行=弾み）」＋「足元のフレームピップ（現在フレーム点灯）」＋「状態ラベル `idle f3` 等」で進行を可視化する。Task 15/16 でキャラ JSON にスプライトシート / アニメ定義が入った段階で、`AnimationState` の枚数・尺を JSON 由来に差し替え、状態 / フレーム番号で `TextureRegion` を引く描画へ置換する（`FighterAnimator` の責務は不変）。

---

## 攻撃処理（Task 11）

- **発生条件**：攻撃入力の**立ち上がりエッジ**（`InputAction.ATTACK`）で発動。**接地中かつ非攻撃中**のみ受け付ける（空中攻撃・キャンセルは MVP 対象外）。技定義は `Character.normalAttack`（`Shared/Types.Move`）。
- **区間遷移**：`Fighter` が `AttackPhase`（`NONE/STARTUP/ACTIVE/RECOVERY`）と経過フレーム `attackFrame` を持ち、`Move` の `startup → active → recovery` の累積境界で区間を進める。総フレーム終了で `NONE` に戻る。
- **行動拘束**：攻撃中は横移動・ジャンプ・新規攻撃を受け付けない（`moveDir` を 0 に固定）。重力・着地は攻撃中も適用（地上開始のため通常は接地維持）。
- **hitbox 有効**：`isHitboxActive()` は `ACTIVE` 区間のみ true（実際の重なり判定は Task 12、デバッグ枠表示は Task 18）。
- **可視化（MVP）**：`GameRenderer` が攻撃中に前方へ strike 矩形を区間色（startup=黄 / active=赤 / recovery=灰）で描き、状態ラベルを `attack:<区間>` に切り替える。アニメは `AnimationState.ATTACK`（攻撃 > 空中 > 歩行 > 待機の優先順）。

---

## 当たり判定（Collision）

3 種の矩形（AABB）を扱う：

| 種類 | 役割 |
|---|---|
| Hitbox | 攻撃判定。`active` 中のみ有効 |
| Hurtbox | 食らい判定。相手の hitbox と重なると被弾 |
| PushBox | 押し合い判定。キャラ同士のめり込みを解消 |

- ヒット判定：自 hitbox × 相手 hurtbox の AABB 重なり。1 つの active 区間で同一相手に多段ヒットしないよう **ヒット済みフラグ**で制御。
- 押し合い：両者の pushbox が重なったら左右に押し戻す（画面端は押し戻し優先）。

---

## HP / HP ゲージ（Task 10）

- **実行時状態**：`Fighter` が `currentHp` を保持し、初期値はキャラ定義 `Character.hp`（最大 HP）。`applyDamage(int)` で 0 未満にならないよう減算し、`isKO()`（`currentHp <= 0`）・`getHpRatio()`（0.0〜1.0）を公開する。減算の発火は Task 13（ダメージ処理）。
- **ゲージ描画**：`GameRenderer` が HUD 上端に左右ミラーで 1 本ずつ描く。P1 は左端固定で右へ、P2 は右端固定で左へ（中央側から）減る。残量割合に応じて色を 緑（>50%）→ 黄（>25%）→ 赤 と変える。名前（外側）・`現在/最大`（内側）のラベルを重ねる。
- レイアウト定数は `GameRenderer` に集約（バー幅・高さ・余白・枠太さ）。

---

## ダメージ / のけぞり

- 被弾で `damage` 分 HP を減算。
- 被弾側は `hitstun`（のけぞり）ステートへ遷移し、一定フレーム行動不能。
- MVP ではガード・コンボ補正は対象外（将来拡張）。

---

## ラウンド / 勝敗

- 1 ラウンド制（MVP）。`BattleRules` にタイマー・HP 上限を持つ。
- いずれかの HP が 0 → KO で勝敗確定・結果表示。
- タイムアップ時は HP 残量が多い側を勝ちとする（MVP）。

---

## MVP 完成条件（戦闘面）

2 体表示 / 移動・ジャンプ・通常攻撃 / HP ゲージ / 攻撃・食らい判定 / 1 ラウンドの勝敗判定。
→ Task 14 で戦闘面の MVP を満たす（描画・JSON 読込・デバッグ表示と合わせて Task 18 + 22 で全 MVP 充足）。

## やらないこと（MVP）

ガード／コンボ補正／高度な物理／オンライン対戦／高度な AI（第一設計書「MVP でやらないこと」）。
コマンド技（Task 19）・必殺技ステート（Task 20）・簡易 AI（Task 21）は MVP 後に追加し、その際に本書へ追記する。

## 変更履歴

- (Bootstrap) 第一設計書の戦闘要素・MVP 条件に基づく初版ドラフトを作成。
- (Task 7) `GameRuntime/Battle/Fighter` を新設し、左右移動・画面端クランプ・相手方向への向き更新を追記。
- (Task 8) ジャンプ / 重力（立ち上がりエッジ発動・接地判定・空中横移動）を追記。`Fighter.update` をジャンプ入力受け取りへ拡張、`Shared/Constants.GRAVITY` を追加。
- (Task 9) アニメーション管理（`FighterAnimator` / `AnimationState`）を追記。`Fighter` に `moveDir` 保持 + `isWalking()` を追加し、idle/walk/jump 状態導出と tick ベースのフレーム進行（delta 非依存）を定義。MVP はプレースホルダ矩形へボブ + フレームピップ + 状態ラベルで可視化。
- (Task 10) HP / HP ゲージを追記。`Fighter` に `currentHp` + `applyDamage` / `isKO` / `getHpRatio` を追加し、`GameRenderer` が HUD 上端へ左右ミラーの HP バー（残量で緑/黄/赤）を描画。
- (Task 11) 攻撃処理を追記。`Shared/Types.Move` と `Battle.AttackPhase` を新設し、`Fighter` に startup/active/recovery の区間遷移・行動拘束・`isHitboxActive` を追加。`GameRenderer` が strike 矩形を区間色で可視化。
