# BattleSystem — 戦闘仕様

本書は Phantom Nexus の戦闘ロジック仕様。戦闘仕様を変える PR では本書を同時に更新する（[CLAUDE.md](../CLAUDE.md) のルール）。
実装は `GameRuntime/Battle` と当たり判定（Collision）が担当し、データは `Shared/Types` 経由で受け取る。

> 本書は Task 10〜14・20・21・24〜33・35〜37 の各完了時に更新し、**MVP ＋ コマンド技/必殺技/AI ＋ 複数技（弱/中/強 + 複数必殺技）＋ しゃがみ ＋ しゃがみ攻撃 ＋ 複数ラウンド制（ベスト・オブ 3）＋ ガード ＋ しゃがみ移動（低速クロール）＋ しゃがみガード ＋ 下段判定 ＋ 空中攻撃 ＋ ガード高さ属性（overhead/mid/low）＋ 投げ技（ガード不能の近接掴み）＋ 投げ抜け（throw tech）＋ AI 読み合い反応（ガード/投げ崩し）まで実装済み**の現状を反映している。
> 戦闘仕様を変える今後の PR でも本書を同 PR で更新すること。

---

## 基本ループ

60fps 固定ステップを基準（`Shared/Constants`）。1 フレームの処理順：

```
入力取得 → ステート更新 → 物理（移動/重力/接地） → 当たり判定 → ダメージ適用 → 勝敗判定 → 描画
```

---

## ステート（MVP）

`idle / walk / jump / jump_attack(空中攻撃) / attack / throw(投げ) / crouch_attack(しゃがみ攻撃) / hitstun(のけぞり) / guard / crouch / crouch_walk / crouch_guard / KO`。
攻撃は **startup / active / recovery** の 3 区間を持ち、`active` 区間のみ hitbox が有効。

アニメーション状態の導出優先順（`FighterAnimator.resolve()` が単一の真実。優先順が変わるタスクでは本リストを更新する。旧タスク節の優先順は当時存在した状態のみの短縮表記を含むが、順序は本リストと矛盾しない）：

> **のけぞり > 投げ > しゃがみ攻撃 > 空中攻撃 > 攻撃 > 空中 > しゃがみガード > しゃがみ移動 > しゃがみ > ガード > 歩行 > 待機**

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

- **発生条件**：攻撃ボタンの**立ち上がりエッジ**（`InputAction.ATTACK_LIGHT` / `ATTACK_MEDIUM` / `ATTACK_HEAVY`）で発動。非攻撃中に受け付ける。接地中はしゃがみ遷移フレームを除き発動可、**空中でも発動可（空中攻撃 = Task 32）**。キャンセルは MVP 対象外。技定義は `Character.normalMoves[]`（`Shared/Types.Move` 配列、Task 24 で拡張）。
- **区間遷移**：`Fighter` が `AttackPhase`（`NONE/STARTUP/ACTIVE/RECOVERY`）と経過フレーム `attackFrame` を持ち、`Move` の `startup → active → recovery` の累積境界で区間を進める。総フレーム終了で `NONE` に戻る。
- **技選択**：`Fighter.update(moveDir, jumpPressed, attackButton, crouchHeld)` の `attackButton`（`Shared/Types.AttackButton` の `LIGHT`/`MEDIUM`/`HEAVY`、null = 攻撃なし）を受け取り、`selectNormalMove()` が `normalMoves[]` をスキャンして `Move.getButton()` と enum 同一性で照合する（JSON トークンの正規化は `AttackButton.fromToken` に集約。`crouchHeld` はしゃがみ遷移用 → Task 25 参照）。
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
- MVP ではガード・コンボ補正は対象外とした（ガードは Task 27 以降で実装済み。コンボ補正は将来拡張）。

---

## ダメージ数値ポップアップ（追加機能）

被弾 / ガード時に「実際に減った HP 量」を命中位置から数字で浮かび上がらせる演出（手応えの可視化 + chip 量の可読性向上）。**純粋な視覚演出で戦闘結果には影響しない**（HP 計算とは独立）。

- **生成**：`PhantomNexusGame.resolveHit` / 飛び道具命中時に、`applyHit` / `applyGuard` の適用前後の `getCurrentHp()` 差を量として `DamagePopup`（`GameRuntime/Battle`）を生成する。ダメージ式（chip = `max(1, damage/10)` 等）を複製せず HP 差で求めるため、残 HP より大きい一撃の 0 クランプも正確に表示できる。位置は通常技＝当たった hitbox 中心、飛び道具＝弾の X × 相手の胴中央。
- **種別 / 色**：通常ヒット（`HIT`）は黄、ガード成立時の chip（`CHIP`）は青で色分け（`GameRenderer`）。
- **アニメーション**：命中位置から `GameConstants.DAMAGE_POPUP_FRAMES`（既定 40f ≒ 0.67 秒）かけて上昇し、終盤（進捗 60% 以降）でフェードアウト。決着 / ラウンド間でも上昇・フェードを継続する（KO を決めた一撃の数字が止まらず最後まで浮かぶ）ため、`update()` 冒頭の凍結ガードより前で進める。
- **状態管理**：`PhantomNexusGame` が一覧を保持し（`Projectile` と同じパターン）、毎フレーム寿命を進めて期限切れを除去・ラウンドリセットでクリア。描画はテキストパス（`SpriteBatch`）で行う。

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

## ガード（Task 27）

接地中に相手と逆方向（後退方向）を保持することでガード状態に入り、攻撃を受けると chip ダメージ（通常の 10%、最低 1）のみを受け、のけぞり（hitstun）が発生しない。

| 条件 | 値 |
|---|---|
| 発動条件 | 接地中 + 非のけぞり + 非攻撃中 + 後退方向入力 |
| 後退方向 | `facingRight=true` → LEFT（moveDir=-1）、`facingRight=false` → RIGHT（moveDir=+1） |
| chip ダメージ | `Math.max(1, attackDamage / 10)` |
| knockback | 通常の 30%（のけぞりなし、微小後退のみ） |
| 空中ガード | 不可（接地中のみ） |
| しゃがみガード | 可（Task 30。しゃがみ中の後退方向保持で低姿勢ガード） |
| 中段 / 下段 | 中段（立ち攻撃）はどちらのガードでも防げる。下段（しゃがみ攻撃, Task 31）は**しゃがみガードでのみ**防げ、立ちガードでは通常ヒットになる |
| 視覚 | 半透明ブルーオーバーレイ（`GameRenderer.GUARD_COLOR`）。しゃがみ時は低姿勢（`height/3`）の高さで重なる |

- **`Fighter.guarding`** フィールドを毎 `update()` の先頭で算出する（接地 + 非のけぞり + 非攻撃 + 後退方向入力）。
- **`Fighter.applyGuard(attackDamage, knockbackDir)`** — chip ダメージ適用と微小 knockback を行う。のけぞりカウンタは変更しない。
- **`PhantomNexusGame.resolveHit()`** および **`updateProjectiles()`** で `defender.isGuarding()` を確認し、ガード中は `applyHit()` の代わりに `applyGuard()` を呼ぶ。下段に対する立ちガードの不成立は Task 31 で `resolveHit()` に追加（下段判定節を参照）。
- **`AnimationState.GUARD`** と **`FighterAnimator.resolve()`** への GUARD 優先度（のけぞり > しゃがみ攻撃 > 空中攻撃 > 攻撃 > 空中 > しゃがみガード > しゃがみ移動 > しゃがみ > ガード > 歩行 > 待機）を追加。

---

## しゃがみ攻撃（Task 28）

| 項目 | 仕様 |
|---|---|
| 発動条件 | `crouching == true`（既にしゃがみ状態）かつ攻撃ボタン押下 |
| 遷移フレームブロック | DOWN 押下と同フレームの攻撃入力は無視（`crouching=false` の遷移フレームでは `!crouchHeld` が false） |
| 技データ | `normalMoves[]` の同一技を使用。ただし hitbox の Y は脚部の低位に下がり**下段技になる**（Task 31。`CollisionSystem` が `LOW_ATTACK_HITBOX_OFFSET_Y` を使用） |
| 姿勢維持 | 攻撃中も `crouching=true` を維持 → hurtbox 低高さ・プレースホルダ矩形短縮 |
| 立ち上がり | 攻撃終了後に `crouchHeld` が `false` なら自動で `crouching=false`（既存ロジックと共通） |
| 中断 | `applyHit()` で `crouchAttacking=false` / `crouching=false` にリセット |
| アニメーション | `AnimationState.CROUCH_ATTACK`（単一ポーズ）。優先順: hitstun > **crouch_attack** > jump_attack > attack > jump > crouch_guard > crouch_walk > crouch > guard > walk > idle |
| AI | `AiController` は `crouchHeld=false` を渡すのでしゃがみ攻撃は発動しない |

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

## 簡易 AI（Task 21 → Task 37）

- `GameRuntime/Battle/AiController` が 1 体を状態ベースで操作する（人間の `PlayerInput` の差し替え）。Task 21 の方針は「近づいて、間合い（中心間 ≤ 150px）に入ったら通常攻撃」。攻撃後はクールダウン（45F）で連打を防ぐ。
- Core は P2 を既定で AI 制御（**F2** でトグル、撮影は `ai=false` で無効化）。AI は `Fighter.update` を人間と同じ経路で呼ぶため、移動・攻撃・押し合い・被弾はすべて共通ロジックを通る。

### 読み合い反応（Task 37）

Task 37 で「相手の現在状態に反応する」2 つの行動を追加した。判断は相手の観測状態（`isAttacking()` / `isThrowing()` / `isGuarding()`）と距離のみに基づき、**乱数を使わない（決定的＝入力リプレイと両立）**。

| 反応 | 条件 | 行動 |
|---|---|---|
| ガード反応 | 相手が**打撃中**（`isAttacking() && !isThrowing()`）＋ 中心間 ≤ `GUARD_RANGE`(200px) ＋ 自分が行動可能 | 後退方向を保持して**ガード**（chip のみで凌ぐ）。投げはガード不能なので対象外 |
| 投げ崩し | 相手が**ガード中**（`isGuarding()`）＋ 中心間 ≤ `THROW_RANGE`(130px) ＋ `throwMove` あり ＋ クールダウン明け | ガード不能の**投げ**で崩す（打撃は防がれるため。`throwReq=true`） |

- **優先順**：ガード反応 ＞ 投げ崩し ＞ 接近 ＞ 通常攻撃。状態反応（ガード/投げ）を距離ベース行動より優先する。
- これにより「打撃＝ガードされる → 投げで崩す／投げ＝ガード不能だがジャンプ・投げ抜けで対応」という読み合いを CPU 戦でも体験できる。AI 自身の投げ抜け反応・ジャンプ・必殺技・しゃがみ系は将来拡張。

---

## しゃがみ（Task 25）

- **入力**：DOWN キー押し続け（P1: S、P2: ↓）で発動。**接地中・非攻撃中・非のけぞり中**のみ遷移する。
- **行動拘束**：しゃがみ中はジャンプを受け付けない。通常技は入力可（しゃがみ攻撃として発動 → Task 28 参照）。横移動は **Task 29 で低速クロールとして許可**。DOWN を離すと立ち上がる。ただし DOWN 押下と同フレームの攻撃入力は無視（遷移フレームの誤入力防止）。
- **食らい判定**：しゃがみ中は hurtbox の高さを 1/3（`def.getHeight() / 3`、height=240 なら 80px）にする。これにより `hitboxOffsetY ≥ 100px` の飛び道具や高めの攻撃をかわせる（`CollisionSystem.hurtbox()` が `Fighter.isCrouching()` を参照）。
- **攻撃・被弾による解除**：攻撃開始（立ち攻撃のみ）または `applyHit()` で `crouching = false` にリセットする。しゃがみ攻撃中は低姿勢を維持する（Task 28）。
- **アニメーション**：`AnimationState.CROUCH`（2 フレームループ）、移動中は `CROUCH_WALK`（Task 29）、攻撃中は `CROUCH_ATTACK`（Task 28）。優先順は **のけぞり > しゃがみ攻撃 > 空中攻撃 > 攻撃 > 空中 > しゃがみガード > しゃがみ移動 > しゃがみ > ガード > 歩行 > 待機**。
- **プレースホルダ描画**：`GameRenderer` がしゃがみ中のキャラを `height / 3` の矩形で描く（スプライト導入後は専用コマに差し替え）。
- **AI**：`AiController` は常に `crouchHeld=false` を渡す（AI はしゃがまない）。

---

## 複数技対応（Task 24）

Task 24 で技定義を 1 件から配列に拡張した。

- **通常技 `normalMoves[]`**：`Move.button`（JSON トークン "light"/"medium"/"heavy"）でボタンと紐付ける。P1: F/G/H、P2: Numpad1/2/3 がそれぞれ light/medium/heavy に対応。Core は押されたボタンを `Shared/Types.AttackButton`（`LIGHT`/`MEDIUM`/`HEAVY`）として `Fighter.update(moveDir, jumpPressed, attackButton, crouchHeld)` に渡し、`Fighter.selectNormalMove()` が配列をスキャンして `Move.getButton()` と enum 同一性で照合する（トークンの大文字小文字・空白の正規化は `AttackButton.fromToken` が担う）。
- **必殺技 `specialMoves[]`**：`Move.command`（"HADOUKEN" 等、`Command.name()` と照合）で技を識別。`CharacterLoader.VALID_COMMANDS` に列挙されたコマンドのみ許可。`Command` enum を追加した場合は同セットも更新する。
- **後方互換**：旧形式 JSON（`normalAttack` / `specialMove` 単体フィールド）は `CharacterLoader.migrateIfLegacy()` が自動で配列へ移行する。`normalAttack` には `button="light"` を補完する。
- **検証**：`CharacterLoader.validate()` が `normalMoves[]`（1 件以上必須）と `specialMoves[]`（任意）の各要素を個別に検証する。button は `AttackButton.fromToken`（必須：null/空と未知値を弾く）、command は `VALID_COMMANDS` で許可値を制限する。

---

## しゃがみ移動（Task 29）

- **入力**：しゃがみ状態（DOWN 押し続け + 接地中）に左右入力を同時押しすることで発動する低速クロール。
- **速度**：通常の歩行速度（`Character.walkSpeed`）の **50%**。
- **拘束維持**：クロール中もしゃがみ中の制約（ジャンプ不可・hurtbox 1/3 高さ）を維持する。クロール中に攻撃ボタンを押すとしゃがみ攻撃として発動する（Task 28・`moveDir=0` で停止して攻撃）。
- **ガードとの関係**：**後退方向**へのクロールは Task 30 でしゃがみガード（低姿勢ガード）になる。**前進方向**（相手側）へのクロールのみが純粋なしゃがみ移動として `CROUCH_WALK` に解決される。
- **アニメーション**：`AnimationState.CROUCH_WALK`（2 フレームループ・8 tick/frame）で小刻みな低姿勢移動を可視化。`FighterAnimator.resolve()` で `isCrouchGuarding()==false`（＝前進クロール）かつ `isCrouchWalking()==true` の場合に選択される。
- **`isWalking()` との分離**：`isWalking()` は `!crouching` を条件に含めたため、クロール中に `WALK` アニメに遷移しない。
- **AI**：`AiController` は `crouchHeld=false` のままなのでクロールは行わない（影響なし）。

---

## しゃがみガード（Task 30）

しゃがみ（DOWN 押し続け）中に**後退方向**を保持すると、低姿勢を維持したままガード状態に入る。立ちガード（Task 27）のしゃがみ版。

| 項目 | 仕様 |
|---|---|
| 発動条件 | 接地中 + 非のけぞり + 非攻撃中 + しゃがみ中（DOWN 保持）+ 後退方向入力 |
| ガード判定 | 立ちガードと共通の `Fighter.guarding`（`update()` 先頭で算出）。条件から `!crouchHeld` を撤廃し、立ち・しゃがみ両方で後退方向保持を許可 |
| chip / knockback / のけぞり | 立ちガードと同一（`applyGuard()`：chip = `max(1, damage/10)`、knockback 30%、のけぞりなし） |
| 食らい判定 | しゃがみの hurtbox（`height/3` ≒ 80px）を維持。立ち中段技は依然この高さを超えて空振り（回避優位）だが、**下段攻撃（Task 31）は脚部の低位 hitbox なのでしゃがみ食らい判定に届き、しゃがみガードで chip が乗る** |
| アニメーション | `AnimationState.CROUCH_GUARD`（単一ポーズ）。優先順: hitstun > しゃがみ攻撃 > 空中攻撃 > 攻撃 > 空中 > **しゃがみガード** > しゃがみ移動 > しゃがみ > 立ちガード > 歩行 > 待機 |
| 視覚 | 立ちガードと同じ半透明ブルーオーバーレイ。`GameRenderer` は `drawHeight`（しゃがみ時 `height/3`）に重ねるため自動で低姿勢になる（描画コード変更なし） |
| 判定ヘルパ | `Fighter.isCrouchGuarding()`（`guarding && crouching`） |
| AI | `AiController` は `crouchHeld=false` のままなのでしゃがみガードはしない |

---

## 下段判定（Task 31）

しゃがみ攻撃（Task 28）を**下段技**にする。立っている相手の脚に当たり、**立ちガードでは防げず**、しゃがみガードでのみ防げる（中段との読み合いが生まれる）。

| 項目 | 仕様 |
|---|---|
| 下段の成立条件 | `Fighter.isCrouchAttacking()` が true の攻撃（しゃがみ中に出した通常技）。立ち通常技・必殺技・飛び道具は**中段**扱い（従来どおり） |
| hitbox 位置 | 下段は技定義の `hitboxOffsetY`（立ち用で 90px 以上）を使わず、足元基準 `GameConstants.LOW_ATTACK_HITBOX_OFFSET_Y`（= 0px, 脚部）に出す。`CollisionSystem.activeHitbox` と `GameRenderer.drawAttackStrike` の両方で同じ低位に揃える |
| 当たる相手 | 立ち hurtbox（足元〜全高）にもしゃがみ hurtbox（足元〜`height/3` ≒ 80px）にも届く。これで Task 30 で観測できなかった「しゃがみガードの chip」が下段に対して実際に発生する |
| ガード正誤 | Task 33 で高さ属性に統合。`resolveHit` は `effectiveAttackHeight(attacker)` が `low`（しゃがみ通常技）のとき **`blocked = defender.isCrouchGuarding()`**＝立ちガード不成立 → 通常ヒット（のけぞり）、しゃがみガードなら chip。中段（mid）は従来どおり立ち / しゃがみどちらでも成立。詳細は「ガード高さ属性（Task 33）」を参照 |
| 飛び道具 | 中段扱い（`updateProjectiles` は従来どおり `isGuarding()` で chip 判定。下段飛び道具は未対応） |
| AI | `AiController` は `crouchHeld=false` のままなので下段攻撃は出さない（影響なし） |

> 中段（立ち攻撃）は依然としてしゃがみ食らい判定（低い）に届かず空振りするため、「中段はしゃがみで回避／下段はしゃがみガードで防ぐ」という非対称が成立する。**その対となる「上段（overhead）＝立ちガード必須・しゃがみガード貫通」は Task 33 で高さ属性としてデータ化した**（下記参照）。

---

## 空中攻撃（Task 32）

滞空中（`!grounded`）に攻撃ボタンを押すと、その場で通常技を**空中攻撃**として発動できる。降り際に相手へ攻撃を重ねる「飛び込み」が可能になる。

| 項目 | 仕様 |
|---|---|
| 発動条件 | `attackPhase == NONE` かつ攻撃ボタン押下かつ `!grounded`（接地中はしゃがみ遷移フレームを除き従来どおり）。`aerialAttacking = !grounded` を記録 |
| hitbox | 通常技の `hitboxOffsetX/Y` をそのまま使用（しゃがみ攻撃のような低位化はしない）。原点は滞空中の足元 `getY()` なので、hitbox は空中の高さに出る。降下に伴い hitbox も下がり、地上の相手に届く |
| 中段/下段 | 空中攻撃は**中段**扱い（`isCrouchAttacking()` ではないので `resolveHit` の `low=false`）。立ち / しゃがみどちらのガードでも chip |
| 行動拘束 | 攻撃中は横移動・再ジャンプ不可（`moveDir=0`）。重力・着地は攻撃中も適用するため、空中攻撃中も落下して着地する。着地後も技の残りフレームは進行し、終了で `aerialAttacking=false` |
| 必殺技 | 空中での必殺技は不可（`startSpecial` は接地必須。空中でコマンド+攻撃が成立しても通常の空中攻撃にフォールバック） |
| アニメーション | `AnimationState.JUMP_ATTACK`（単一ポーズ）。優先順: hitstun > しゃがみ攻撃 > **空中攻撃** > 攻撃 > 空中 > しゃがみガード > … |
| AI | `AiController` はジャンプしないので空中攻撃も出さない（影響なし） |

---

## ガード高さ属性（Task 33）

技ごとに「どのガードで防げるか」を **JSON の `guardHeight` でデータ化**し、ガード判定を属性駆動に一元化する。Task 31 の下段（しゃがみガード必須）と対になる**上段（overhead＝立ちガード必須・しゃがみガード貫通）**を新規に導入する。

| 値 | 意味 | 立ちガード | しゃがみガード |
|---|---|---|---|
| `overhead` | 上段 | ✅ 成立（chip） | ❌ 貫通 → 通常ヒット |
| `mid`（既定） | 中段 | ✅ 成立 | ✅ 成立 |
| `low` | 下段 | ❌ 貫通 → 通常ヒット | ✅ 成立（chip） |

| 項目 | 仕様 |
|---|---|
| データ | `Shared/Types.Move.guardHeight`（string, 既定 `"mid"`）。`Shared/Schema.CharacterLoader` が `overhead`/`mid`/`low` を検証。未指定の旧 JSON は `mid` に正規化（後方互換） |
| 実効高さ | `PhantomNexusGame.effectiveAttackHeight(attacker)`：しゃがみ通常技は状態優先で `low`（Task 31）、それ以外は技定義の `guardHeight` |
| ガード解決 | `resolveHit` で `defender.isGuarding()` 時に `low`→`isCrouchGuarding()`、`overhead`→`!isCrouchGuarding()`（＝立ちガード）、`mid`→常に成立。成立で chip、不成立で通常ヒット |
| 例（Aoi=fighter001） | `heavy_slam` を `guardHeight:"overhead"` 化。hitbox を `offsetY 60 / height 90`（Y60–150 を占有）に下げ、立ち hurtbox（0–全高）にもしゃがみ hurtbox（0–`height/3`≒80px）にも届かせて、しゃがみガード貫通を実観測できるようにした |
| 飛び道具 | 既定 `mid` 運用（`updateProjectiles` は `isGuarding()` で chip）。`guardHeight` 自体は specialMoves でも検証する |
| AI | `AiController` は `crouchHeld=false` のままなのでしゃがみガードをせず、overhead の不成立分岐には入らない（影響なし） |

> overhead が**しゃがみガードを貫通する**には、hitbox がしゃがみ hurtbox（上端≒80px）に届く必要がある（Task 30/31 で観測した「高い hitbox はしゃがみ相手に空振り」の裏返し）。そのため例示の `heavy_slam` は hitbox を下方向へ広げてある。属性（読み合いのルール）と hitbox 形状（届くか）は独立した設定で、両方を満たして初めて貫通が成立する。

---

## 投げ技（Task 35）

地上・立ちで**投げボタン**（P1: T / P2: Numpad0）を押すと、近接の相手を掴む**ガード不能の投げ**を発動する。立ち・しゃがみどちらのガードでも防げず、ガード偏重の相手を崩す択になる（打撃＝ガードで凌げる／投げ＝ガード貫通、の二択を作る）。

| 項目 | 仕様 |
|---|---|
| データ | `Shared/Types.Character.throwMove`（任意の `Move`）。未指定なら投げを持たない（後方互換）。button / command / guardHeight は不要（専用の投げボタンで起動し、ガードを無視するため）。再利用する `Move` の damage / フレーム / hitbox 矩形が「掴み判定（grab box）」を表す |
| 発動条件 | 接地中 + 立ち（非しゃがみ）+ 非攻撃中 + 投げボタンの立ち上がり + キャラに `throwMove` あり。空中・しゃがみ中は発動しない。Core が `Fighter.update` の専用引数 `throwReq=true`（打撃の `attackButton` とは別チャネル）を渡し、`Fighter` が `throwing=true` で専用経路を起動する（通常技 / 必殺技より最優先） |
| 成立範囲 | active 区間中に grab box が相手 hurtbox と重なれば成立（通常打撃と同じ `CollisionSystem.isHitting`）。短い range（狭い hitbox 幅）で近接限定にする |
| ガード不能 | `resolveHit` で `attacker.isThrowing()` のとき `blocked` 判定をスキップし、ガード中でも常にフルダメージを適用する |
| 空中の相手 | 掴めない（`defender` が `!grounded`）。grab box が空中の相手に重なった時点で whiff として消費（`markAttackConnected`）し、同じ active 区間内に着地しても掴み直さない＝**ジャンプで確実に回避できる**。「ジャンプで投げを避ける」読み合いが成立する |
| 被弾 | `Fighter.applyThrow(damage, dir)`：フルダメージ ＋ 長い hitstun（`THROW_HITSTUN_FRAMES`=30）＋ 強い knockback（`KNOCKBACK_SPEED × THROW_KNOCKBACK_SCALE`=1.6 倍）。のけぞり扱いなので進行中の攻撃を中断する |
| 空振り | range 外・空中の相手には grab box が当たらず、startup→active→recovery を消化して空振り（隙）になる |
| アニメーション | `AnimationState.THROW`（攻撃ステート中だが strike とは別の単一ポーズ）。優先順: のけぞり > **投げ** > しゃがみ攻撃 > 空中攻撃 > 攻撃 > …。被弾側は通常どおり `HITSTUN` |
| 視覚 | grab box を通常打撃の区間色ではなく**紫**（`GameRenderer.THROW_COLOR`）で描き、掴みであることを区別。状態ラベルは `throw:<区間>`。被弾側はフルダメージのため黄色のダメージ数値ポップアップ（`HIT`）が出る（ガード chip の青ではない） |
| 投げ抜け | 掴まれる瞬間に投げ返すと投げ抜け（throw tech）でノーダメージに抜けられる（Task 36。「投げ抜け（Task 36）」節を参照） |
| AI | `AiController` は投げボタンを送らないため投げを出さない（影響なし） |

> 投げと打撃の対比：**同じガード状態の相手**に対し、中段打撃は `guard`（chip のみ）で凌がれるが、投げは `hitstun`（フルダメージ）でガードを貫通する。両者の差は「使った技が打撃か投げか」だけで、ガード貫通の因果が一意になる（Task 31/33 の対比手法を踏襲）。

---

## 投げ抜け（Task 36）

ガード不能の投げ（Task 35）に対する唯一の対抗策。**掴まれる直前〜その瞬間にこちらも投げボタンを押す**と投げ抜け（throw tech）が成立し、掴みがノーダメージで相互に弾かれる。これで読み合いの択が「打撃＝ガード／投げ＝投げ抜け／空中＝ジャンプ」と揃う。

| 項目 | 仕様 |
|---|---|
| 入力 | 専用ボタンは増やさず、**投げと同じボタン**（P1: T / P2: Numpad0）。投げボタンを押した接地フレームに `Fighter.armThrowTech()` で猶予窓（`THROW_TECH_WINDOW`=10f）をアームする。自分の投げが成立しない間合い／状況でも防御反応として受け付ける |
| 成立条件 | `resolveHit` で投げが成立する瞬間（`attacker.isThrowing()` ＆ 被掴み側が地上）に、**被掴み側の猶予窓が残っていれば**（`defender.canTechThrow()`）投げ抜け。さもなくば通常の投げ（フルダメージ） |
| 効果 | `Fighter.applyThrowTech(pushDir)` を**両者**に適用：ノーダメージ・相互に反対方向へ `THROW_TECH_PUSHBACK` で弾き・`THROW_TECH_FRAMES`（14f）の短い硬直。進行中の投げ/攻撃は中断。硬直は `hitstunFrames` を流用（行動拘束・knockback 減衰の既存ロジック再利用）し、`throwTechFrames` で表示と区別する |
| 視覚 | 状態ラベルは `tech`（`isThrowTeched()` を hitstun より優先表示）。ダメージは無いのでポップアップは出ない。両者が反対方向へ弾かれ HP は不変 |
| 猶予窓の長さ | 掴みの発生（startup 3 ＋ active）を跨げるよう `THROW_TECH_WINDOW`=10f。短すぎると目押し必須、長すぎると投げが死ぬためのバランス値 |
| AI | `AiController` は投げボタンを送らないため投げ抜けもしない（影響なし。将来の AI 拡張候補） |
| 決定性 | 乱数なし（入力と窓カウンタのみ）。入力リプレイ（決定的シミュ）と矛盾しない |

> 投げ成立と投げ抜けの対比：**同じ近接の投げ**に対し、被掴み側が無入力なら `hitstun`＋フルダメージ（黄ポップアップ）、被掴み側も投げボタンを返すと両者 `tech`・HP 不変。違いは「被掴み側が投げボタンを押したか」だけで、投げ抜けの因果が一意になる（Task 31/33/35 の対比手法を踏襲）。

---

## 入力リプレイ（記録 / 再生）

本エンジンのシミュレーションは **「1 render = 1 固定ステップ」で dt 非依存**（[基本ループ](#基本ループ)）、AI（`AiController`）も乱数を持たない決定的処理。したがって **毎フレームの入力さえ記録すれば、同じ試合を完全に再現できる**（ゲーム状態を丸ごと保存する必要がない）。これを `GameRuntime/Debug/ReplayController` が担う（`ScreenshotController` と同じくシステムプロパティ駆動で、未指定なら通常起動と完全に同一）。

| 用途 | プロパティ | 動作 |
|---|---|---|
| 記録 | `-Dphantom.replay.record=<path>` | 毎フレームの P1/P2 押下状態と P2 AI 状態を 1 行追記（強制終了でもログを失わないよう毎フレーム flush） |
| 再生 | `-Dphantom.replay.play=<path>` | 記録した押下集合を `PlayerInput.setForcedHold` で注入し直し、AI 状態も復元（再生中は F2 トグルを無視） |
| 記録時 AI OFF | `-Dphantom.replay.ai=false` | 記録開始時から P2 を静止（人間）に。開始後の F2 トグルもフレーム単位で記録・再生される |

- **記録形式**（テキスト）：ヘッダ `PHANTOM_REPLAY v1` ＋ 1 行 `p1mask,p2mask,ai`。mask は各プレイヤーの押下アクションを `InputAction.ordinal()` のビット位置で畳んだ整数（**列挙順を変えると旧ログの解釈がずれる**）。`ai` は当該フレームで P2 が AI 制御だったか（AI フレームの `p2mask` は再生側で使わないため 0）。
- 撮影モード（`phantom.screenshot.*`）と併用でき、**再生中の任意フレームを PNG 化**できる（決定的に同一フレームを再現するため、連番化すればリプレイ GIF も作れる）。

---

## やらないこと（MVP）

コンボ補正／高度な物理／オンライン対戦（第一設計書「MVP でやらないこと」）。
※当初 MVP 対象外だったガード・投げ抜け・高度な AI は順次実装済み：ガードは Task 27（立ちガード）・Task 30（しゃがみガード）・Task 31/33（下段・ガード高さ属性）、投げ抜け（throw tech）は Task 36、AI の読み合い反応（ガード/投げ崩し）は Task 37。AI のさらなる拡張（投げ抜け反応・ジャンプ・しゃがみ系・難易度）は将来拡張。

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
- (Task 27) ガードを追記。`Fighter.guarding` フィールド・`applyGuard()`・`isGuarding()`、`AnimationState.GUARD`、`FighterAnimator` 優先順の更新、`GameRenderer.GUARD_COLOR` のオーバーレイ描画、`resolveHit()`/`updateProjectiles()` のガード分岐を実装。「ガード（Task 27）」節を追加。
- (Task 28) しゃがみ攻撃追加。`Fighter` に `crouchAttacking` フィールド・`isCrouchAttacking()` を追加。しゃがみ状態中の攻撃入力を解禁し、発動時に `crouchAttacking=true` → 攻撃中も `crouching=true` を維持して低姿勢で攻撃する。立ち上がりは攻撃終了後に `crouchHeld` の状態で決まる（押し続け中はそのまましゃがみへ）。`AnimationState.CROUCH_ATTACK` を追加し `FighterAnimator.resolve()` に織り込み（しゃがみ攻撃 > 通常攻撃の優先順）。しゃがみ（Task 25）節の行動拘束記述を更新。「しゃがみ攻撃（Task 28）」節を追加。
- (Task 29) しゃがみ移動（低速クロール）を追記。`Fighter` のしゃがみ分岐で `moveDir` を記録し `walkSpeed * 0.5f` で移動するよう変更。`isWalking()` を `!crouching` 条件付きに修正し `isCrouchWalking()` を追加。`AnimationState.CROUCH_WALK`（2 フレームループ）と `FighterAnimator.bobOffset()` の CROUCH_WALK ケースを追加。「しゃがみ移動（Task 29）」節を追加。
- (Task 30) しゃがみガードを追記。`Fighter.guarding` のガード条件から `!crouchHeld` を撤廃し、しゃがみ中の後退方向保持でも低姿勢ガードに入れるようにした。`isCrouchGuarding()`（`guarding && crouching`）を追加。`AnimationState.CROUCH_GUARD` を追加し `FighterAnimator.resolve()` の優先順（しゃがみ攻撃/攻撃/空中 > しゃがみガード > しゃがみ移動/しゃがみ > 立ちガード）に織り込み。ガードオーバーレイは既存の `drawHeight` 流用で自動的に低姿勢になる（描画変更なし）。ガード（Task 27）節の「しゃがみガード」行・しゃがみ移動（Task 29）節のガード記述を更新し「しゃがみガード（Task 30）」節を追加。
- (Task 31) 下段判定を追記。しゃがみ攻撃（`isCrouchAttacking()`）を下段技にし、`CollisionSystem.activeHitbox` と `GameRenderer.drawAttackStrike` で hitbox の Y を `GameConstants.LOW_ATTACK_HITBOX_OFFSET_Y`（脚部）へ下げて立ち / しゃがみ両 hurtbox に届くようにした。`resolveHit` のガード分岐を `blocked = isGuarding() && (!low || isCrouchGuarding())` に拡張し、**下段は立ちガードで防げず通常ヒット・しゃがみガードでのみ chip**とした（中段は従来どおり）。これで Task 30 で観測できなかったしゃがみガードの chip が下段に対して発生する。「下段判定（Task 31）」節を追加し、ガード（Task 27）/しゃがみ攻撃（Task 28）/しゃがみガード（Task 30）節を更新。
- (Task 32) 空中攻撃を追記。`Fighter` の攻撃発動条件を `grounded ? (!crouchHeld || crouching) : true` に拡張し、滞空中でも通常技を出せるようにした。`aerialAttacking` フィールド・`isAerialAttacking()` を追加（`reset()`/`applyHit()` でクリア）。`AnimationState.JUMP_ATTACK` を追加し `FighterAnimator.resolve()` に織り込み（しゃがみ攻撃 > 空中攻撃 > 通常攻撃の優先順）。空中攻撃は中段扱い（`resolveHit` の low=false）。攻撃処理（Task 11）節の「空中攻撃は対象外」記述を更新し「空中攻撃（Task 32）」節を追加。
- (Task 33) ガード高さ属性を追記。`Move.guardHeight`（overhead/mid/low, 既定 mid）をデータ化し、`PhantomNexusGame.effectiveAttackHeight()`（しゃがみ通常技は状態優先で low）と `resolveHit` のガード成立分岐（low→しゃがみガードのみ / overhead→立ちガードのみ / mid→両成立）に一元化。`CharacterLoader` に `VALID_GUARD_HEIGHTS` 検証を追加し、fighter001 `heavy_slam` を overhead 化（hitbox を `offsetY 60 / height 90` に下げてしゃがみ hurtbox へ届かせる）。「ガード高さ属性（Task 33）」節を追加し、下段判定（Task 31）節のガード正誤を属性駆動の記述へ更新。
- (追加機能) ダメージ数値ポップアップを追記。`GameRuntime/Battle/DamagePopup` を新設し、`resolveHit`/`updateProjectiles` で適用前後の `getCurrentHp()` 差を量として命中位置に生成（通常ヒット=黄 / ガード chip=青）。`GameConstants.DAMAGE_POPUP_FRAMES`（40f）を追加。`GameRenderer.renderScene` に `popups` 引数を追加しテキストパスで上昇＋フェード描画。`PhantomNexusGame` が一覧を保持・毎フレーム更新（凍結ガード前）・ラウンドリセットでクリア。純粋な演出で戦闘結果には影響しない。「ダメージ数値ポップアップ（追加機能）」節を追加。
- (Task 35) 投げ技（ガード不能の近接掴み）を追記。`Character.throwMove`（任意の `Move`）・`InputAction.THROW`（P1=T / P2=Numpad0）を追加。`Fighter` に `throwing` フィールド・`applyThrow()`（フル damage ＋ 長 hitstun ＋ 強 knockback）・`isThrowing()` を追加し、地上・立ちで専用経路で発動（通常技 / 必殺技より最優先）。`PhantomNexusGame.resolveHit` で `isThrowing()` 時はガード判定をスキップ（ガード不能）＋空中の相手は掴めない（不成立）分岐を追加。`AnimationState.THROW` を追加し `FighterAnimator.resolve()` の優先順（のけぞり > 投げ > しゃがみ攻撃 > …）に織り込み。`GameRenderer` は grab box を紫（`THROW_COLOR`）で描き状態ラベルを `throw:<区間>` に。`GameConstants.THROW_HITSTUN_FRAMES`（30）/`THROW_KNOCKBACK_SCALE`（1.6）を追加。`CharacterLoader.validateThrowMove()`（button/command/guardHeight 不要）を追加。fighter001/002 に `throwMove` と sprite `throw` 行を追加。「投げ技（Task 35）」節を追加し、ステート一覧・アニメ優先順・やらないこと節を更新。
- (feature/replay) 入力リプレイ（記録 / 再生）を追記。`GameRuntime/Debug/ReplayController` を新設し、毎フレームの押下マスク＋P2 AI 状態を記録 → `PlayerInput.setForcedHold` で再生。決定性（1 render = 1 固定ステップ・dt 非依存・AI 乱数なし）に依拠し、入力列のみで試合を完全再現する。`build.gradle` の run プロパティ転送に `phantom.replay.record`/`play`/`ai` を追加。**戦闘ロジックは不変**（記録/再生フックを Core の render に足しただけ）。設計書タスク順の外の開発ツール追加。「入力リプレイ（記録 / 再生）」節を追加。
- (refactor) 通常技のボタン種別を `Shared/Types/AttackButton` enum（`LIGHT`/`MEDIUM`/`HEAVY`）に集約（`GuardHeight` と同パターン・戦闘仕様の変更なし）。`Fighter.update` の `attackButton` 引数を String → `AttackButton` に変更し、`selectNormalMove()` の照合を equalsIgnoreCase から enum 同一性に置換（トークン正規化は `AttackButton.fromToken` に一元化）。**Task 35 の投げ起動は予約語 `attackButton="throw"`（String）に依存していたため、本 enum 化に合わせて `Fighter.update` へ専用の `throwReq`（boolean）引数を新設して分離**（`AttackButton` は打撃 3 種に限定し、投げは別チャネルで通す）。攻撃処理（Task 11）/複数技対応（Task 24）/投げ技（Task 35）節の起動記述を更新。
- (Task 36) 投げ抜け（throw tech）を追記。`Fighter` に `throwTechWindow`（投げボタン押下でアームする猶予窓）・`throwTechFrames`（抜け後の表示/硬直）フィールドと `armThrowTech()`/`canTechThrow()`/`applyThrowTech(pushDir)`/`isThrowTeched()` を追加。`PhantomNexusGame.updateFighterInput` で投げボタン押下（接地）時に `armThrowTech()`、`resolveHit` の投げ成立分岐に「被掴み側が `canTechThrow()` なら両者へ `applyThrowTech` でノーダメージ相互 knockback」を追加。`GameRenderer.drawNameLabel` は `isThrowTeched()` を hitstun より優先して `tech` 表示。`GameConstants` に `THROW_TECH_WINDOW`（10）/`THROW_TECH_FRAMES`（14）/`THROW_TECH_PUSHBACK` を追加。硬直は `hitstunFrames` を流用（新規 `AnimationState` は足さず HITSTUN ポーズを再利用）。`applyHit`/`applyThrow`/`applyThrowTech` で `guarding` を即解除し単一集約状態を一貫させる（CodeRabbit 指摘）。「投げ抜け（Task 36）」節を追加。
- (Task 37) AI 読み合い反応を追記。`AiController.control` に「相手が打撃中ならガード（後退方向保持）」「相手がガード中なら投げで崩す（`throwReq=true`）」の 2 反応を追加（優先順：ガード ＞ 投げ ＞ 接近 ＞ 攻撃）。判断は相手の観測状態（`isAttacking`/`isThrowing`/`isGuarding`）＋距離のみで**乱数なし＝決定的**（入力リプレイと両立）。`GUARD_RANGE`(200)/`THROW_RANGE`(130) 定数を追加。`Fighter`/Core の戦闘ロジックは不変（AI の判断分岐のみ）。簡易 AI（Task 21）節に「読み合い反応（Task 37）」サブ節を追加。
