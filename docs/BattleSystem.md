# BattleSystem — 戦闘仕様

本書は Phantom Nexus の戦闘ロジック仕様。戦闘仕様を変える PR では本書を同時に更新する（[CLAUDE.md](../CLAUDE.md) のルール）。
実装は `GameRuntime/Battle` と当たり判定（Collision）が担当し、データは `Shared/Types` 経由で受け取る。

> 本書は Task 10〜14・20・21・24・25・26・28 の各完了時に更新し、**MVP ＋ コマンド技/必殺技/AI ＋ 複数技（弱/中/強 + 複数必殺技）＋ しゃがみ ＋ しゃがみ攻撃 ＋ 複数ラウンド制（ベスト・オブ 3）まで実装済み**の現状を反映している。
> 戦闘仕様を変える今後の PR でも本書を同 PR で更新すること。

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

- **発生条件**：攻撃ボタンの**立ち上がりエッジ**（`InputAction.ATTACK_LIGHT` / `ATTACK_MEDIUM` / `ATTACK_HEAVY`）で発動。**接地中かつ非攻撃中**のみ受け付ける（空中攻撃・キャンセルは MVP 対象外）。技定義は `Character.normalMoves[]`（`Shared/Types.Move` 配列、Task 24 で拡張）。
- **区間遷移**：`Fighter` が `AttackPhase`（`NONE/STARTUP/ACTIVE/RECOVERY`）と経過フレーム `attackFrame` を持ち、`Move` の `startup → active → recovery` の累積境界で区間を進める。総フレーム終了で `NONE` に戻る。
- **技選択**：`Fighter.update(moveDir, jumpPressed, attackButton)` の `attackButton`（"light"/"medium"/"heavy"）を受け取り、`selectNormalMove()` が `normalMoves[]` をスキャンして `Move.button` と照合（case-insensitive・trim 正規化）する。
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

### 実装（Task 12）

- 判定ロジックは `GameRuntime/Battle/CollisionSystem`（状態を持たない純関数群）。矩形は `Shared/Types` の `Hitbox`（与ダメージ付き）/`Hurtbox`/`PushBox`（いずれもワールド座標 AABB）。
- **Hitbox**：`active` 区間のみ `activeHitbox()` が生成。`Move` の相対 hitbox（前方の前面・足元基準）を向きで左右反転してワールド座標に置く。
- **Hurtbox / PushBox**：MVP はキャラ矩形（`width`×`height`）。
- **ヒット判定**：`isHitting(attacker, defender)` が active hitbox × hurtbox の重なりを返す。Core が攻撃ごと 1 回だけ命中確定（`Fighter.markAttackConnected`、`hasAttackConnected` で多段防止）。ダメージ適用は Task 13。
- **押し合い**：`resolvePush(a, b)` が pushbox の横めり込み量を等分して `Fighter.nudgeX` で左右へ分離（端は `clampToStage` で片寄せ）。
- **可視化**：接触フレームに `GameRenderer` が hitbox 中心へ白い火花マーカーを描く。実 hitbox/hurtbox/pushbox 枠のデバッグ表示は `GameRuntime/Debug/DebugOverlay`（Task 18、F1 トグル / 撮影は `debug=true`）。

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

### 実装（Task 13）

- 確定ヒット（Task 12）で `Fighter.applyHit(damage, hitstun, knockbackDir)` を呼ぶ。HP 減算（`applyDamage`）＋ `hitstunFrames` 設定＋後方 knockback（`velocityX = knockbackDir * KNOCKBACK_SPEED`）を行い、**進行中の攻撃は中断**する（のけぞり優先）。
- **のけぞり中**：入力を一切受け付けず（行動不能）、`velocityX` を `KNOCKBACK_FRICTION` で毎フレーム減衰させながら横移動。残フレームが 0 になると通常状態へ復帰。重力・着地は適用。
- 調整値は `Shared/Constants`：`HITSTUN_FRAMES`・`KNOCKBACK_SPEED`・`KNOCKBACK_FRICTION`（将来 JSON / 技別へ拡張可能）。knockback 方向は攻撃者→被弾者の位置関係で決定。
- アニメは `AnimationState.HITSTUN`（**のけぞり > 攻撃 > 空中 > 歩行 > 待機**の優先順）。`GameRenderer` は被弾側を白くフラッシュし、ラベルを `hitstun <残>` にする。

---

## ラウンド / 勝敗

- 先取 2 ラウンド制（ベスト・オブ 3）。`BattleRules` に制限時間（秒）・先取ラウンド数（`roundsToWin`）を持つ（HP 上限はキャラ定義側）。
- いずれかの HP が 0 → KO で当該ラウンド勝敗確定。
- タイムアップ時は HP 残量が多い側を当ラウンドの勝ちとする。
- `roundsToWin` に達したプレイヤーがマッチ勝者（全ラウンド引き分けは続行）。

### 実装（Task 14）

- `Shared/Types/BattleRules`（制限時間 / 先取ラウンド数）と `Battle/RoundManager`（進行・勝敗の単一判定点）を新設。
- **毎フレーム**：KO を最優先で判定（両者同時 KO は `DRAW`）。無ければ制限時間を 1 減らし、尽きたら HP 残量で勝者決定（同値は `DRAW`）。
- **決着後**：`isFinished()` が true。Core は以降の入力・物理・判定の更新を**凍結**し、結果の静止画を保つ。
- **HUD**：残り秒（切り上げ）を中央上に表示。決着時は `K.O.` / `TIME UP` ＋ `<勝者> WINS` / `DRAW` のバナーを中央に描画。
- 撮影用に制限時間オーバーライド（`phantom.screenshot.timelimit`）を追加し、タイムアップ結果を短時間で撮れるようにした。

> これにより**戦闘面の MVP（移動・ジャンプ・通常攻撃・HP ゲージ・攻撃/食らい判定・1 ラウンド勝敗）が充足**。残りはステージ背景（Task 17）・JSON 読込（Task 15/16）・デバッグ表示（Task 18）。

## 複数ラウンド制（Task 26）

Task 26 で 1 ラウンド制をベスト・オブ 3（先取 2 ラウンド）に拡張した。

- **`BattleRules.defaults()`**：`rounds=2`（= `roundsToWin=2`）を既定とする。`getRoundsToWin()` を追加（`getRounds()` の別名）。
- **`RoundManager` 拡張**：
  - 勝利カウンタ `p1Wins`/`p2Wins`、現在ラウンド番号 `currentRound` を追加。
  - ラウンド決着後にどちらかが `roundsToWin` に達したらマッチ確定（`matchOver=true`）、未確定なら `BETWEEN_ROUND_FRAMES`（120f = 2 秒）の インターバルカウントダウンを開始する。
  - カウントダウンが 0 になると `nextRoundReady` フラグを立て、`startNewRound()`（タイマーリセット・ラウンド番号加算）を実行する。
  - `consumeNextRoundReady()` で 1 回だけフラグを消費（Core がファイターリセットに使う）。
  - `isFinished()` はマッチ確定（後方互換）。`isBetweenRounds()` はインターバル中を示す。
- **`Fighter.reset(spawnX, facingRight)`**：HP・位置・速度・攻撃ステートを初期状態に戻す（ラウンド間リセット用）。
- **`InputHistory.reset()`**：リングバッファをクリアして旧コマンド断片を除去する。
- **Core の `update()`**：`isBetweenRounds()` 中はファイター操作・判定を停止して `round.update()` だけを進め、`consumeNextRoundReady()` で `resetFighters()` を呼ぶ。
- **描画**：
  - HP バー内側端の下に**勝利ドット**（`roundsToWin` 個、金色=獲得/暗色=未獲得）を表示。
  - インターバル中は**ラウンド結果バナー**（理由 + ラウンド勝者 + 次ラウンドまでのカウント）を中央に描画。
  - マッチ確定後は**マッチ結果バナー**（理由 + `<勝者> WINS!` + スコア `N - N`）を表示。

---

## MVP 完成条件（戦闘面）

2 体表示 / 移動・ジャンプ・通常攻撃 / HP ゲージ / 攻撃・食らい判定 / 1 ラウンドの勝敗判定。
→ Task 14 で戦闘面の MVP を満たす（描画・JSON 読込・デバッグ表示と合わせて Task 18 + 22 で全 MVP 充足）。

## 必殺技ステート（Task 20）

- **発動**：コマンド検出（Task 19, 波動拳=236+A）成立かつ行動可能（接地・非攻撃・非のけぞり）で `Fighter.startSpecial(Move)`。通常攻撃ステートと同じ startup/active/recovery を選択した `Move` のフレームで進める。発動時は同フレームの通常攻撃入力を抑止する。
- **技選択**：Core の `findSpecialMove(def, cmd)` が `Character.specialMoves[]` をスキャンし、`cmd.name()` と `Move.command` を equalsIgnoreCase（trim 正規化）で照合する（Task 24 で複数必殺技配列へ拡張）。
- **飛び道具（projectile）**：`Move.projectile` が true の必殺技は、発動時に前方へ `Battle/Projectile` を 1 発発射する（速度 `projectileSpeed`）。弾は等速で進み、相手 hurtbox 命中で `applyHit`（ダメージ＋のけぞり＋knockback）して消滅、画面外でも消滅する。1 発 1 ヒット（多段なし）。
- 飛び道具技は **body 付随の hitbox を持たない**（`CollisionSystem.activeHitbox` が projectile 技で null を返す）。ダメージは弾のみが運ぶ。弾の所有者には当たらない。
- **可視化**：`GameRenderer` が弾を二重円（グロー＋コア）で描画。状態ラベルは `special:<区間>`。

---

## 簡易 AI（Task 21）

- `GameRuntime/Battle/AiController` が 1 体を状態ベースで操作する（人間の `PlayerInput` の差し替え）。MVP の方針は「近づいて、間合い（中心間 ≤ 150px）に入ったら通常攻撃」。攻撃後はクールダウン（45F）で連打を防ぐ。
- Core は P2 を既定で AI 制御（**F2** でトグル、撮影は `ai=false` で無効化）。AI は `Fighter.update` を人間と同じ経路で呼ぶため、移動・攻撃・押し合い・被弾はすべて共通ロジックを通る。
- ジャンプ・必殺技・ガード・読み合いは将来拡張（第一設計書「MVP は高度な AI をやらない」）。

---

## しゃがみ（Task 25）

- **入力**：DOWN キー押し続け（P1: S、P2: ↓）で発動。**接地中・非攻撃中・非のけぞり中**のみ遷移する。
- **行動拘束**：しゃがみ中は横移動・ジャンプを受け付けない。通常技は入力可（しゃがみ攻撃として発動 → Task 28 参照）。DOWN を離すと立ち上がる。ただし DOWN 押下と同フレームの攻撃入力は無視（遷移フレームの誤入力防止）。
- **食らい判定**：しゃがみ中は hurtbox の高さを 1/3（`def.getHeight() / 3`、height=240 なら 80px）にする。これにより `hitboxOffsetY ≥ 100px` の飛び道具や高めの攻撃をかわせる（`CollisionSystem.hurtbox()` が `Fighter.isCrouching()` を参照）。
- **攻撃・被弾による解除**：攻撃開始または `applyHit()` で `crouching = false` にリセットする。
- **アニメーション**：`AnimationState.CROUCH`（2 フレームループ）。優先順は **のけぞり > しゃがみ攻撃 > 攻撃 > 空中 > しゃがみ > 歩行 > 待機**（Task 28 でしゃがみ攻撃を追加）。
- **プレースホルダ描画**：`GameRenderer` がしゃがみ中のキャラを `height / 3` の矩形で描く（スプライト導入後は専用コマに差し替え）。
- **AI**：`AiController` は常に `crouchHeld=false` を渡す（AI はしゃがまない）。

---

## しゃがみ攻撃（Task 28）

| 項目 | 仕様 |
|---|---|
| 発動条件 | `crouching == true`（既にしゃがみ状態）かつ攻撃ボタン押下 |
| 遷移フレームブロック | DOWN 押下と同フレームの攻撃入力は無視（`crouching=false` の遷移フレームでは `!crouchHeld` が false） |
| 技データ | `normalMoves[]` の同一技を使用（しゃがみ専用技は将来追加可） |
| 姿勢維持 | 攻撃中も `crouching=true` を維持 → hurtbox 低高さ・プレースホルダ矩形短縮 |
| 立ち上がり | 攻撃終了後に `crouchHeld` が `false` なら自動で `crouching=false`（既存ロジックと共通） |
| 中断 | `applyHit()` で `crouchAttacking=false` / `crouching=false` にリセット |
| アニメーション | `AnimationState.CROUCH_ATTACK`（単一ポーズ）。優先順: hitstun > **crouch_attack** > attack > jump > crouch > walk > idle |
| AI | `AiController` は `crouchHeld=false` を渡すのでしゃがみ攻撃は発動しない |

---

## 複数技対応（Task 24）

Task 24 で技定義を 1 件から配列に拡張した。

- **通常技 `normalMoves[]`**：`Move.button`（"light"/"medium"/"heavy"）でボタンと紐付ける。P1: F/G/H、P2: Numpad1/2/3 がそれぞれ light/medium/heavy に対応。Core は押されたボタンを文字列で `Fighter.update(moveDir, jumpPressed, attackButton)` に渡し、`Fighter.selectNormalMove()` が配列をスキャンして照合する（case-insensitive・trim 正規化）。
- **必殺技 `specialMoves[]`**：`Move.command`（"HADOUKEN" 等、`Command.name()` と照合）で技を識別。`CharacterLoader.VALID_COMMANDS` に列挙されたコマンドのみ許可。`Command` enum を追加した場合は同セットも更新する。
- **後方互換**：旧形式 JSON（`normalAttack` / `specialMove` 単体フィールド）は `CharacterLoader.migrateIfLegacy()` が自動で配列へ移行する。`normalAttack` には `button="light"` を補完する。
- **検証**：`CharacterLoader.validate()` が `normalMoves[]`（1 件以上必須）と `specialMoves[]`（任意）の各要素を個別に検証する。button は `VALID_BUTTONS`、command は `VALID_COMMANDS` で許可値を制限する。

---

## やらないこと（MVP）

ガード／コンボ補正／高度な物理／オンライン対戦／高度な AI（第一設計書「MVP でやらないこと」）。

## 変更履歴

- (Bootstrap) 第一設計書の戦闘要素・MVP 条件に基づく初版ドラフトを作成。
- (Task 7) `GameRuntime/Battle/Fighter` を新設し、左右移動・画面端クランプ・相手方向への向き更新を追記。
- (Task 8) ジャンプ / 重力（立ち上がりエッジ発動・接地判定・空中横移動）を追記。`Fighter.update` をジャンプ入力受け取りへ拡張、`Shared/Constants.GRAVITY` を追加。
- (Task 9) アニメーション管理（`FighterAnimator` / `AnimationState`）を追記。`Fighter` に `moveDir` 保持 + `isWalking()` を追加し、idle/walk/jump 状態導出と tick ベースのフレーム進行（delta 非依存）を定義。MVP はプレースホルダ矩形へボブ + フレームピップ + 状態ラベルで可視化。
- (Task 10) HP / HP ゲージを追記。`Fighter` に `currentHp` + `applyDamage` / `isKO` / `getHpRatio` を追加し、`GameRenderer` が HUD 上端へ左右ミラーの HP バー（残量で緑/黄/赤）を描画。
- (Task 11) 攻撃処理を追記。`Shared/Types.Move` と `Battle.AttackPhase` を新設し、`Fighter` に startup/active/recovery の区間遷移・行動拘束・`isHitboxActive` を追加。`GameRenderer` が strike 矩形を区間色で可視化。
- (Task 12) 当たり判定処理を追記。`Shared/Types` に `Hitbox`/`Hurtbox`/`PushBox`、`Battle` に `CollisionSystem` を新設。hit 判定（多段防止フラグ）と push 解消（`Fighter.nudgeX`）を実装し、接触マーカーを可視化。
- (Task 13) ダメージ処理を追記。`Fighter.applyHit`（HP 減算＋hitstun＋knockback＋攻撃中断）と `AnimationState.HITSTUN` を追加。調整値を `Shared/Constants`（HITSTUN_FRAMES/KNOCKBACK_SPEED/KNOCKBACK_FRICTION）に集約。撮影用に初期 X オーバーライド（p1x/p2x）を追加。
- (Task 14) ラウンド勝敗判定を追記。`Shared/Types/BattleRules` と `Battle/RoundManager`（KO / タイムアップ / 引き分け）を新設。タイマー HUD・結果バナー・決着後フリーズを実装。これで戦闘面 MVP を充足。撮影用に制限時間オーバーライド（timelimit）を追加。
- (Task 18) デバッグ当たり判定表示（`GameRuntime/Debug/DebugOverlay`、push=青/hurt=緑/hit=赤の線枠、F1 トグル）を追記。撮影用に `debug=true` 強制 ON を追加。
- (Task 20) 必殺技ステートを追記。`Character.specialMove` ＋ `Move.projectile`/`projectileSpeed` ＋ `Battle/Projectile` を新設し、波動拳で飛び道具を発射。`Fighter` を `currentMove` ベースに整理（通常 / 必殺の共通化）。
- (Task 21) 簡易 AI（`Battle/AiController`、接近 → 間合いで通常攻撃）を追記。Core は P2 を既定 AI（F2 トグル / 撮影 `ai=false`）。
- (Task 23) ドキュメント整備。ドラフト註記を実装済みの記述へ更新し、README の操作方法 / 実装済み機能、CLAUDE.md の現フェーズを整合させた（仕様変更なし）。
- (Task 25) しゃがみ追加。`Fighter` に `crouching` フィールド・`isCrouching()` を追加し、DOWN 押し続けで接地中のみ遷移。しゃがみ中は横移動/ジャンプ/通常技不可（同フレームの DOWN+攻撃も抑止）、hurtbox 高さを 1/3（80px, `hitboxOffsetY≥100` の弾をかわせる値）に削減（`CollisionSystem.hurtbox()` を更新）。`AnimationState.CROUCH` を追加し `FighterAnimator.resolve()` に織り込み。`Fighter.update()` に `crouchHeld` 引数を追加し全呼び出し元（`PhantomNexusGame`・`AiController`）を更新。
- (Task 24) 複数技対応。`InputAction.ATTACK` → `ATTACK_LIGHT`/`ATTACK_MEDIUM`/`ATTACK_HEAVY` の 3 ボタン化、`Character.normalMoves[]`/`specialMoves[]` への配列拡張、後方互換マイグレーション（`migrateIfLegacy`）、button/command バリデーション（`VALID_BUTTONS`/`VALID_COMMANDS`）を追記。攻撃処理・必殺技ステートの各節を更新し「複数技対応（Task 24）」節を追加。
- (Task 26) 複数ラウンド制（ベスト・オブ 3）を追記。`BattleRules.defaults()` を `rounds=2` へ、`RoundManager` を複数ラウンド対応（インターバル・マッチ確定・リセット）へ拡張。`Fighter.reset()`・`InputHistory.reset()`・Core の between-round ガード・勝利ドット描画・ラウンド結果バナーを実装。「複数ラウンド制（Task 26）」節を追加し、ラウンド/勝敗節を更新。
- (Task 28) しゃがみ攻撃追加。`Fighter` に `crouchAttacking` フィールド・`isCrouchAttacking()` を追加。しゃがみ状態中の攻撃入力を解禁し、発動時に `crouchAttacking=true` → 攻撃中も `crouching=true` を維持して低姿勢で攻撃する。立ち上がりは攻撃終了後に `crouchHeld` の状態で決まる（押し続け中はそのまましゃがみへ）。`AnimationState.CROUCH_ATTACK` を追加し `FighterAnimator.resolve()` に織り込み（しゃがみ攻撃 > 通常攻撃の優先順）。しゃがみ（Task 25）節の行動拘束記述を更新。
