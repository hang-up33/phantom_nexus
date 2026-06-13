# BattleSystem — 戦闘仕様

本書は Phantom Nexus の戦闘ロジック仕様。戦闘仕様を変える PR では本書を同時に更新する（[CLAUDE.md](../CLAUDE.md) のルール）。
実装は `GameRuntime/Battle` と当たり判定（Collision）が担当し、データは `Shared/Types` 経由で受け取る。

> 本書は Task 8・10〜14・20・21・24〜33・35〜39・42〜47・49〜57・59・60・63〜66・68〜71・74・75 の各完了時に更新し、**MVP ＋ コマンド技/必殺技/AI ＋ 複数技（弱/中/強 + 複数必殺技）＋ しゃがみ ＋ しゃがみ攻撃 ＋ 複数ラウンド制（ベスト・オブ 3）＋ ガード ＋ しゃがみ移動（低速クロール）＋ しゃがみガード ＋ 下段判定 ＋ 空中攻撃 ＋ ガード高さ属性（overhead/mid/low）＋ 投げ技（ガード不能の近接掴み）＋ 投げ抜け（throw tech）＋ AI 読み合い反応（ガード/投げ崩し）＋ コンボカウンター ＋ ラウンド開始イントロ（"ROUND N"/"FIGHT!"）＋ ガードゲージ／ガードクラッシュ ＋ 必殺技ゲージ／EX 必殺技 ＋ チェーンコンボ（通常技キャンセル）＋ コンボダメージ補正 ＋ 特殊キャンセル（通常技→必殺技）＋ ダッシュ（二度押しステップ）＋ AI のダッシュ接近 ＋ AI の投げ抜け反応 ＋ 打撃必殺技／無敵リバーサル（対空）＋ EX 打撃必殺技（メーター消費でダメージ強化）＋ AI の無敵対空 ＋ AI 難易度（EASY/NORMAL/HARD）＋ AI のジャンプ攻撃（飛び込み）＋ 空中ガード（滞空中の後退保持で飛び道具・中段/上段を chip ガード）＋ ダウン（knockdown・特定技で相手を転ばせる・ダウン中無敵）＋ AI の下段読みしゃがみガード（相手の下段にしゃがみガードで対応・HARD のみ）＋ AI の飛び道具牽制（zoner・遠距離で飛び道具を撃つ・HARD のみ）＋ ダッシュ攻撃（ダッシュ中の攻撃で出る突進打撃・データ駆動）＋ 受け身（ukemi・ダウン直後の行動入力でクイック起き上がり）＋ 二段ジャンプ（air jump・`airJumps` でデータ化）＋ 空中ダッシュ（air dash・`airDashes` でデータ化）＋ 空中投げ（air throw・`airThrowMove` でデータ化）＋ カウンターヒット（相手の攻撃 startup を潰すとダメージ増＋のけぞり延長）＋ 多段ヒット技（`Move.hits`/`hitGap` でデータ化）＋ AI 受け身（HARD の AI がダウン直後にクイック起き上がり）まで実装済み**の現状を反映している。
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
空中ガード（Task 59）は専用の `AnimationState` を持たず JUMP ポーズ＋青オーバーレイを流用し、ラベル `air_guard` で識別する。
ダウン（Task 60）も専用 `AnimationState` を持たず HITSTUN ポーズを流用し、ラベル `knockdown` で識別する（ダウン中は被弾無敵）。受け身（Task 66）成立中は `knockdown(ukemi)` ラベルでクイック起き上がりを識別する（ポーズは HITSTUN 流用のまま）。
ダッシュ攻撃（Task 65）も専用 `AnimationState` を持たず ATTACK ポーズを流用し、ラベル `dash_attack:<区間>` で識別する（ダッシュ中の攻撃で出る突進打撃）。
攻撃は **startup / active / recovery** の 3 区間を持ち、`active` 区間のみ hitbox が有効。

アニメーション状態の導出優先順（`FighterAnimator.resolve()` が単一の真実。優先順が変わるタスクでは本リストを更新する。旧タスク節の優先順は当時存在した状態のみの短縮表記を含むが、順序は本リストと矛盾しない）：

> **ダウン > のけぞり > 投げ > しゃがみ攻撃 > 空中攻撃 > 攻撃 > 空中 > しゃがみガード > しゃがみ移動 > しゃがみ > ガード > 歩行 > 待機**

（ダウン（Task 60・ラベル `knockdown`）はのけぞり（hitstun）と同じく `HITSTUN` ポーズを流用するが、`FighterAnimator.resolve()` が `isKnockedDown()` を `isInHitstun()` より先に評価するため最優先。空中ガード（Task 59・ラベル `air_guard`）は「空中」ポーズ＋青オーバーレイ流用のため本リストでは「空中」の位置。）

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
- **発動条件**：**接地中（`grounded`）のみ**地上ジャンプ可。発動時は垂直速度 `velocityY` に `Character.jumpPower`（px/frame, 上向き正）を与えて離地する。空中での再ジャンプ（二段ジャンプ）は **Task 68 でデータ駆動の `Character.airJumps`（任意・既定 0）として追加**（下記参照）。
- **重力 / 積分**：毎フレーム `velocityY -= GRAVITY`（`Shared/Constants.GRAVITY`, px/frame²）し、足元 Y に `velocityY` を加算する（明示オイラー積分・60fps 固定ステップ基準）。
- **着地判定**：足元 Y が `GROUND_Y` 以下に達したら Y を `GROUND_Y` にスナップし、`velocityY = 0`・`grounded = true` に戻す。
- **空中横移動**：MVP では空中でも左右移動を許可する（地上と同じ `walkSpeed`）。
- 滞空高さ・時間は `jumpPower` と `GRAVITY` で決まる（頂点高さ ≈ `jumpPower² / (2·GRAVITY)`、滞空 ≈ `2·jumpPower / GRAVITY` フレーム）。値は将来 JSON（Task 16）で調整可能にする。

---

## 二段ジャンプ（air jump）（Task 68）

`Character.airJumps`（任意 int・既定 0）を持つキャラは、地上ジャンプの後に**空中でもう一度ジャンプ**できる（機動型キャラの差別化）。データ駆動で、キャラ JSON に `airJumps` を足すだけで増やせる（持たないキャラ＝既定 0 は従来どおり地上ジャンプのみ・後方互換）。

| 項目 | 仕様 |
|---|---|
| 発動条件 | **空中**（`!grounded`）＋ジャンプ入力の立ち上がり ＋ 残り空中ジャンプ回数 `airJumpsRemaining > 0` |
| 効果 | `velocityY` を `jumpPower`（上向き初速）へ**上書き**して再上昇（下降中でも跳ね上がる）。残り回数を 1 消費 |
| 回復 | 接地（着地）で `airJumpsRemaining = def.getAirJumps()` に回復。`reset()`（ラウンド間）・コンストラクタ（初期接地）でも満タン |
| 攻撃中 | 空中攻撃（Task 32）中（`attackPhase != NONE`）は移動 / ジャンプ分岐が走らないため二段ジャンプ不可（降り際まで攻撃が進行） |

- **実装（`Fighter.update` の空中分岐）**：地上ジャンプ判定 `if (jumpPressed && grounded)` の `else if (jumpPressed && !grounded && airJumpsRemaining > 0)` として追加。`velocityY` を上書きし `airJumpsRemaining--`。着地ブロックで回数回復。地上ジャンプ自体は回数を消費しない（airJumps=1 なら「地上 1 ＋ 空中 1」の計 2 段）。
- **既存機構との両立**：空中攻撃（Task 32）・空中ガード（Task 59）はそのまま機能する（二段ジャンプは `velocityY` のみ操作し、滞空状態・横移動・ガード判定は不変）。
- **決定性**：ジャンプ入力（立ち上がり）と接地状態・残り回数のみで決まり**乱数なし**（入力リプレイと両立）。`airJumps` はキャラ JSON のデータで、回数定数を増やさない。**リプレイ format 不変**だが、滞空中にジャンプ入力を含む既存リプレイで `airJumps>0` のキャラは「無視→二段ジャンプ」へ結果が変わり得る（戦闘仕様変更）。
- **データ例**：fighter004 Rai に `airJumps: 1`（高速ラッシュ＋空中機動）。

---

## 空中ダッシュ（air dash）（Task 69）

`Character.airDashes`（任意 int・既定 0）を持つキャラは、**滞空中に方向二度押し**で水平バーストダッシュ（前/後）できる。地上ダッシュ（Task 49）の二度押し検出・移動を**滞空でも許可**したもので、ダッシュ中も重力が掛かるため弧を描いて滑空する。データ駆動で、キャラ JSON に `airDashes` を足すだけで増やせる（持たないキャラ＝既定 0 は従来どおり空中ダッシュなし・後方互換）。

| 項目 | 仕様 |
|---|---|
| 発動条件 | **空中**（`!grounded`）＋方向入力の二度押し（同方向タップが受付窓内）＋残り回数 `airDashesRemaining > 0` ＋非攻撃（`attackPhase==NONE`）＋ダッシュ非継続（`dashFrames<=0`） |
| 効果 | 地上ダッシュと同じ `dashFrames`／`dashDir` を立て、`dashFrames>0` 分岐で `walkSpeed × DASH_SPEED_MULTIPLIER` の水平移動。重力は従来どおり毎フレーム適用＝水平バーストしつつ落下（弧）。残り回数を 1 消費 |
| 回復 | 接地（着地）で `airDashesRemaining = def.getAirDashes()` に回復。`reset()`・コンストラクタでも満タン |
| 終了 | 着地（`!wasGrounded` → 接地）で `dashFrames` を 0 にクリアし、地上ダッシュへ持ち越さない |

- **実装（`Fighter.update` のダッシュ検出）**：地上ダッシュ可否 `canGroundDash`（`grounded && ...`）に加え `canAirDash`（`!grounded && airDashesRemaining > 0 && attackPhase==NONE && dashFrames<=0`）を併設し、`(canGroundDash || canAirDash) && moveDir==dashTapDir && dashTapWindow>0` で `dashFrames=DASH_FRAMES` を起動。空中ダッシュ成立時のみ `airDashesRemaining--`。既存の `dashFrames>0` 滑空分岐をそのまま流用（新しい移動分岐を増やさない）。
- **既存機構との両立**：ダッシュ攻撃（Task 65）は `grounded && dashFrames>0` 条件なので**空中ダッシュ中は発動しない**（空中攻撃は通常どおり）。空中ガード（Task 59）・二段ジャンプ（Task 68）は不変（ダッシュ中は `guarding=false`＝ダッシュ優先・滑空分岐に入るため air jump は次フレーム以降）。
- **決定性**：方向入力の二度押し（タップ窓）と接地状態・残り回数のみで決まり**乱数なし**（入力リプレイと両立）。`airDashes` はキャラ JSON のデータ。**リプレイ format 不変**だが、滞空中に方向二度押しを含む既存リプレイで `airDashes>0` のキャラは「空振り→空中ダッシュ」へ結果が変わり得る（戦闘仕様変更）。
- **データ例**：fighter004 Rai に `airDashes: 1`（二段ジャンプ＋空中ダッシュ＝高機動ラッシュ型）。

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

## ダウン（knockdown）（Task 60）

特定の技（`Move.knockdown=true`）を**非ガードで**食らうと、通常のけぞりではなく**ダウン**に陥る。ダウンはデータ駆動で、技 JSON にフラグを足すだけで増やせる。

| 条件 | 値 |
|---|---|
| 発動条件 | `Move.knockdown=true` の技が**非ガード**でヒット（ガード時は通常どおり chip。投げ・飛び道具は対象外＝打撃ヒットのみ） |
| 行動不能 | `KNOCKDOWN_FRAMES`(60) フレーム（のけぞり `HITSTUN_FRAMES`(18) より長い） |
| ダウン中無敵 | あり（**起き攻め / OTG なし**）。当たり判定（`CollisionSystem.isHitting` / `hits`）が `isKnockedDown()` を見て一律外す（打撃・飛び道具とも当たらない） |
| knockback | 通常被弾の `KNOCKDOWN_KNOCKBACK_SCALE`(1.4) 倍で強く転ばせる |
| コンボ | ダウンはコンボの締め（`knockdownFrames` が尽きた瞬間に `comboCount=0`）。コンボ補正（Task 46）は通常被弾と同じく適用 |
| 起き上がり | `KNOCKDOWN_FRAMES` 経過で自動的に起き上がる（idle へ復帰）。ただし**受け身**（Task 66）で早めに起き上がれる |
| 視覚 | 専用 `AnimationState` は持たず `HITSTUN` ポーズを流用し、状態ラベル `knockdown` で識別（`air_guard`/`tech`/`guard_break` と同じ「既存ポーズ流用＋ラベルで区別」方式） |

- **`Move.knockdown`**（任意 boolean・既定 `false`＝後方互換）：`Shared/Types/Move` に追加。旧 JSON はキー無しで `false`＝通常のけぞり。
- **`Fighter.applyKnockdown(damage, knockbackDir)`**：`resolveHit` が非ガード・ダウン技ヒット時に `applyHit` の代わりに呼ぶ。`knockdownFrames` を立て、コンボ計数・補正は `applyHit` と同じ。`update()` 冒頭に `hitstunFrames` と並ぶ inert 分岐（ダウン優先）を持ち、行動不能・knockback 減衰・起き上がりを担う。
- **`Fighter.isKnockedDown()`**：ダウン中（被弾無敵）か。`CollisionSystem` の hit-test 冒頭で `isInvincible()` と並べて参照し、ダウン中の相手への攻撃を一律外す（OTG なし）。
- **決定性**：フレームカウンタのみで乱数なし（入力リプレイと両立）。リプレイ format も不変。ただしダウン技ヒットを含む既存リプレイは「のけぞり→ダウン」へ結果が変わり得る（戦闘仕様変更）。
- **データ例**：`fighter001` の `heavy_slam`（overhead 強攻撃）に `knockdown: true` を付与（非ガードヒットでダウン＝強攻撃の見返り）。飛び道具のダウンは将来拡張。

---

## 受け身（ukemi・クイック起き上がり）（Task 66）

ダウン（Task 60）した側が、**ダウン直後の受付窓内に行動入力**（攻撃 / ジャンプ / 投げ）をすると、残りダウンフレームを短縮して早く起き上がる**受け身**。フル `KNOCKDOWN_FRAMES`(60) を待つより素早く復帰でき、**起き攻め（ダウン中の重ね）への対抗択**になる。グローバル機構（JSON 変更なし・全キャラ共通）。

| 項目 | 仕様 |
|---|---|
| 受付窓 | ダウン開始から `UKEMI_WINDOW`(12) フレーム以内に受け身入力（経過 = `KNOCKDOWN_FRAMES - knockdownFrames`） |
| 受け身入力 | 攻撃ボタン / ジャンプ / 投げのいずれか（`attackButton != null \|\| jumpPressed \|\| throwReq`）。専用キーは増やさない |
| 効果 | 残りダウンを `UKEMI_RISE_FRAMES`(20) に短縮（フル 60 → 20＝約 40 フレーム早く起き上がる） |
| トレードオフ | 早く起き上がる＝**ダウン中無敵が早く切れる**（メアリー／重ねへの隙が残る＝ノーリスクではない） |
| 視覚 | 受け身成立中は状態ラベルを `knockdown(ukemi)` にして識別（`isUkemiRecovering()`。ポーズは HITSTUN 流用のまま） |

- **実装（`Fighter.update` のダウン inert 分岐）**：`knockdownFrames` 減算の**前**に経過フレームを算出し、`ukemiInput && elapsed <= UKEMI_WINDOW && knockdownFrames > UKEMI_RISE_FRAMES` なら `knockdownFrames = UKEMI_RISE_FRAMES` に短縮して `ukemiRecovery=true`。短縮済み（残り ≤ 20）や窓超過では無効＝フルダウンを待つ。`applyKnockdown`（新ダウン）・`reset()` で `ukemiRecovery=false`。
- **無敵との整合**：ダウン中無敵（`isKnockedDown()`・Task 60 の 1F ラッチ）は短縮後も `knockdownFrames > 0` の間そのまま効くので、受け身で 20F に縮めれば**無敵もその分早く切れる**（トレードオフが自動的に成立）。専用の無敵調整は不要。
- **決定性**：判断は入力と経過フレームのみで**乱数なし**。受け身入力は通常の行動入力なので**リプレイ format は不変**（記録済みのボタン入力で再現）。ただしダウン直後に行動入力を含む既存リプレイは「フルダウン→受け身」へ結果が変わり得る（戦闘仕様変更）。
- **AI**：現状 AI（`AiController`）はダウン中に行動入力を出さない（`canStartAction()==false` で攻撃/ジャンプ分岐に入らない）ため受け身しない＝人間のみの択。AI の受け身は将来拡張。

---

## ダメージ数値ポップアップ（追加機能）

被弾 / ガード時に「実際に減った HP 量」を命中位置から数字で浮かび上がらせる演出（手応えの可視化 + chip 量の可読性向上）。**純粋な視覚演出で戦闘結果には影響しない**（HP 計算とは独立）。

- **生成**：`PhantomNexusGame.resolveHit` / 飛び道具命中時に、`applyHit` / `applyGuard` の適用前後の `getCurrentHp()` 差を量として `DamagePopup`（`GameRuntime/Battle`）を生成する。ダメージ式（chip = `max(1, damage/10)` 等）を複製せず HP 差で求めるため、残 HP より大きい一撃の 0 クランプも正確に表示できる。位置は通常技＝当たった hitbox 中心、飛び道具＝弾の X × 相手の胴中央。
- **種別 / 色**：通常ヒット（`HIT`）は黄、ガード成立時の chip（`CHIP`）は青で色分け（`GameRenderer`）。
- **アニメーション**：命中位置から `GameConstants.DAMAGE_POPUP_FRAMES`（既定 40f ≒ 0.67 秒）かけて上昇し、終盤（進捗 60% 以降）でフェードアウト。決着 / ラウンド間でも上昇・フェードを継続する（KO を決めた一撃の数字が止まらず最後まで浮かぶ）ため、`update()` 冒頭の凍結ガードより前で進める。
- **状態管理**：`PhantomNexusGame` が一覧を保持し（`Projectile` と同じパターン）、毎フレーム寿命を進めて期限切れを除去・ラウンドリセットでクリア。描画はテキストパス（`SpriteBatch`）で行う。

---

## ヒットスパーク（Task 38）

打撃 / 飛び道具 / 投げの**命中・ガード・投げ抜けの接触点**に放射状の火花を出す手応え演出。ダメージ数値ポップアップと同じく**純粋な視覚演出で戦闘結果には影響しない**（HP 計算とは独立）。同じ実装パターン（Battle POJO ＋ Core 所有リスト ＋ Renderer 描画）で足す。

- **生成**：`PhantomNexusGame.resolveHit`（通常ヒット / ガード / 投げ成立 / 投げ抜け）と `updateProjectiles`（飛び道具命中）で、ダメージポップアップと同じ命中位置に `HitSpark`（`GameRuntime/Battle`）を生成する。投げ抜けはノーダメージのためポップアップは出さないが火花は出す（接触の手応え）。
- **種別 / 色**：通常ヒット（`HIT`）は暖色（白寄りの黄）、ガード成立（`GUARD`）は寒色（青）で色分け（`GameRenderer.SPARK_HIT_COLOR`/`SPARK_GUARD_COLOR`）。
- **アニメーション**：`GameConstants.HIT_SPARK_FRAMES`（既定 12f ≒ 0.2 秒）かけて、放射スポーク（8 本の三角形）が外へ伸び・中心コアが縮み・全体が線形フェードする。決着 / ラウンド間でも aging を継続する（KO を決めた一撃の火花が最後まで弾ける）ため、`update()` 冒頭の凍結ガードより前で進める（ポップアップと同じ）。
- **状態管理**：`PhantomNexusGame` が一覧を保持し、毎フレーム寿命を進めて期限切れを除去・ラウンドリセットでクリア。描画はオーバーレイパス（`ShapeRenderer.Filled`、`triangle`/`circle`）で行う（ブレンドは有効化済み）。Task 12 の単フレーム接触マーカー（白矩形）とは別に、命中後も数フレーム残る火花として重ねる。

---

## コンボカウンター（Task 39）

被弾側が **hitstun 中にさらに被弾**したら連続ヒット（コンボ）として数え、相手の頭上に `N HITS!` を表示する。

- **計数（`Fighter`）**：`comboCount` フィールドを持ち、`applyHit` / `applyThrow` の冒頭で「適用前の `hitstunFrames > 0` なら継続（+1）、neutral からの被弾なら新規（=1）」と判定する。hitstun が **0 に戻った瞬間**（`update()` の hitstun 分岐で `hitstunFrames == 0`）に `comboCount = 0` へリセット。`applyThrowTech`（投げ抜け＝仕切り直し）と `reset()` でも 0。ガード（`applyGuard`）は被弾ではないので加算しない。
- **真のコンボのみ数える**：hitstun が切れてから当たった追撃は「リセット」であってコンボではない（`comboCount` は 1 に戻る）。本エンジンでは技の硬直（recovery）とのけぞり（`HITSTUN_FRAMES`=18）の関係で、**復帰が hitstun より遅い技の連打はコンボにならない**（例：Aoi の `light` は active4+recovery10+次 startup5 = 19 > 18 でリセット、Akane の `quick_jab` は 3+8+4 = 15 < 18 でコンボ成立）。
- **表示（`GameRenderer`）**：`comboCount >= 2` のとき、被弾側ファイターの頭上に `N HITS!`（オレンジ・拡大）を描く。テキストパス（`SpriteBatch`）でフォント色・倍率を使い、描画後に既定（白・等倍）へ戻す（ポップアップと同じ作法）。
- **決定性**：乱数なし（被弾と hitstun のみで決まる）＝入力リプレイと両立。`Fighter`/Core の戦闘ロジック自体（ダメージ・hitstun）は不変で、計数フィールドと表示を足しただけ。

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

## ラウンド開始イントロ（Task 42）

各ラウンドの開始時に **"ROUND N" → "FIGHT!" の開始演出**を再生し、その間はファイター操作・当たり判定・タイマーを停止する（格闘ゲーム定番の試合開始シーケンス）。

- **`RoundManager`**：
  - `introCountdown` を持ち、構築時とラウンド開始（`startNewRound()`）で `introFrames` にセットする。`update()` 冒頭で `matchOver` の次に `introCountdown > 0` を判定し、カウントを 1 減らして即 return（戦闘・タイマー・勝敗判定を凍結）。
  - `isRoundIntro()`（イントロ中）・`isFightFlash()`（残り `FIGHT_FLASH_FRAMES` 以下＝"FIGHT!" 表示区間）・`getIntroCountdown()` を公開。
  - `introFrames` はコンストラクタ引数（既定コンストラクタは `GameConstants.ROUND_INTRO_FRAMES`＝90f≒1.5 秒、末尾 `FIGHT_FLASH_FRAMES`＝30f≒0.5 秒が "FIGHT!"）。`0` を渡すとイントロ無し。
- **Core の `update()`**：戦闘ブロックのガードを `!isBetweenRounds() && !isRoundIntro()` に拡張し、イントロ中は操作・判定を止めて `round.update()` だけを進める。
- **撮影モードの後方互換**：撮影モードでは既定でイントロを**スキップ**（`screenshot.roundIntroEnabled(true)` が撮影時 false を返し `introFrames=0`）。既存スクショレシピ（frame1 から戦闘前提）を壊さないため。`-x intro=true` 指定時のみ有効化して開始演出コマを撮れる。通常起動・リプレイ（記録/再生とも同一のイントロ長で決定的）はイントロ ON。
- **描画**：イントロ中は中央に "ROUND N"（白）または "FIGHT!"（赤・拡大）バナーを表示。色・倍率は共有状態のため描画後に白・等倍へ戻す（コンボ表示と同様）。新規 `AnimationState` は追加せず、ファイターは idle のまま静止する。

---

## ガード（Task 27）

接地中に相手と逆方向（後退方向）を保持することでガード状態に入り、攻撃を受けると chip ダメージ（通常の 10%、最低 1）のみを受け、のけぞり（hitstun）が発生しない。

| 条件 | 値 |
|---|---|
| 発動条件 | 接地中 + 非のけぞり + 非攻撃中 + 後退方向入力 |
| 後退方向 | `facingRight=true` → LEFT（moveDir=-1）、`facingRight=false` → RIGHT（moveDir=+1） |
| chip ダメージ | `Math.max(1, attackDamage / 10)` |
| knockback | 通常の 30%（のけぞりなし、微小後退のみ） |
| 空中ガード | **可（Task 59。滞空中の後退方向保持で成立。立ち扱い＝`crouching=false`）** |
| しゃがみガード | 可（Task 30。しゃがみ中の後退方向保持で低姿勢ガード） |
| 中段 / 下段 | 中段（立ち攻撃）はどちらのガードでも防げる。下段（しゃがみ攻撃, Task 31）は**しゃがみガードでのみ**防げ、立ちガードでは通常ヒットになる |
| 視覚 | 半透明ブルーオーバーレイ（`GameRenderer.GUARD_COLOR`）。しゃがみ時は低姿勢（`height/3`）の高さで重なる |

- **`Fighter.guarding`** フィールドを毎 `update()` の先頭で算出する（非のけぞり + 非攻撃 + 後退方向入力。**接地 / 滞空いずれでも成立**＝空中ガード・Task 59）。滞空中の成立は `isAirGuarding()`（`guarding && !grounded`）で識別し、ラベルは `air_guard`（描画は JUMP ポーズ＋青オーバーレイを流用）。空中ガードは立ち扱い（`crouching` は接地時のみ true）なので、飛び道具（高さ判定なしで一律ガード可）と中段 / 上段を防ぎ、下段（しゃがみ攻撃）は防げない（が下段 hitbox は滞空中の hurtbox に通常届かない）。
- **`Fighter.applyGuard(attackDamage, knockbackDir)`** — chip ダメージ適用と微小 knockback を行う。のけぞりカウンタは変更しない。
- **`PhantomNexusGame.resolveHit()`** および **`updateProjectiles()`** で `defender.isGuarding()` を確認し、ガード中は `applyHit()` の代わりに `applyGuard()` を呼ぶ。下段に対する立ちガードの不成立は Task 31 で `resolveHit()` に追加（下段判定節を参照）。
- **`AnimationState.GUARD`** と **`FighterAnimator.resolve()`** への GUARD 優先度（のけぞり > しゃがみ攻撃 > 空中攻撃 > 攻撃 > 空中 > しゃがみガード > しゃがみ移動 > しゃがみ > ガード > 歩行 > 待機）を追加。

---

## ガードゲージ／ガードクラッシュ（Task 43）

ガードは無制限に安全ではない。各ファイターは**ガードゲージ**を持ち、ガード成立（chip 被弾）のたびに攻撃力に応じて減る。0 になると**ガードクラッシュ**＝一定フレームのガード不能・行動不能の隙が生じ、攻撃側のフル確定反撃を許す。「ガードで固める」択に対する崩しの読み合いを成立させる。

| 項目 | 仕様 |
|---|---|
| ゲージ最大 | `GameConstants.GUARD_GAUGE_MAX`（100） |
| 減少量／ガード | `Math.max(1, 攻撃力 / GUARD_DRAIN_DIVISOR)`（除数 4。例：中攻撃 80 で 20 減＝5 回で崩壊） |
| 回復 | 非ガード・非クラッシュ時に毎フレーム `GUARD_REGEN_PER_FRAME`（0.4）回復（約 250f≒4 秒で満タン）。ガード中は減る一方 |
| クラッシュ | ゲージ 0 で `GUARD_BREAK_FRAMES`（40f≒0.67 秒）の行動不能。崩した一撃自体は chip のみ（防御は成立）で、**続く攻撃がフル確定**になる |

- **実装（hitstun 流用 + 表示フラグ）**：投げ抜け（Task 36）と同じパターン。`Fighter.applyGuard()` でゲージを削り、0 以下なら `guardGauge` を満タンに戻して `guardBreakFrames` と `hitstunFrames` を `GUARD_BREAK_FRAMES` にセット（既存の行動拘束・knockback 減衰ロジックを流用・ダメージ無し）。`isGuardBroken()` は `guardBreakFrames > 0`。`getGuardGauge()` を HUD に公開。
- **回復**：`update()` 先頭で `guardBreakFrames` を減衰。`guarding` 算出後、非ガード・非クラッシュなら `guardGauge` を回復。`reset()` でゲージ満タン・クラッシュ解除。`applyHit()`/`applyThrow()` は `guardBreakFrames` をクリア（クラッシュ硬直中にフル被弾したらラベルをのけぞりへ更新・表示 desync 防止）。
- **ガード不能化**：クラッシュ中は `hitstunFrames > 0` のため次フレームの `guarding` 算出が false になり、`resolveHit()` は `applyHit()`（フル）を呼ぶ＝続く攻撃が確定する。
- **描画**：HP バー直下に細いガードゲージバー（残量わずか＝橙で警告）。崩された側の頭上に `"GUARD BREAK!"`（赤・画面端でも見切れないよう `drawCenteredClamped` でクランプ）。状態ラベルは `guard_break` を hitstun より先に評価（hitstun 流用のため順序を誤ると "hitstun" に化ける）。
- **データ**：現状はゲージ仕様を全キャラ共通の定数で持つ（JSON 変更なし）。キャラごとのガード耐久差が要れば将来 `Character` へ移す候補。

---

## 必殺技ゲージ／EX 必殺技（Task 44 → Task 54）

ガードゲージ（防御リソース）と対になる**攻撃リソース**。各ファイターは**必殺技ゲージ（スーパーメーター）**を持ち、攻撃を当てる / 受ける / ガードで貯まる。満タンで必殺技（飛び道具）を撃つと**ゲージを消費して EX 版**（ダメージ増・大型弾）になる。リスクを取って攻めた見返り（攻撃で多く貯まる）を強力な一撃に変換する読み合い。

| 項目 | 仕様 |
|---|---|
| メーター最大 | `GameConstants.SUPER_METER_MAX`（100） |
| 増加（攻撃側が当てる） | `METER_GAIN_ON_HIT`（14） |
| 増加（防御側が受ける） | `METER_GAIN_ON_TAKE`（8。攻めるより少ない） |
| 増加（ガード成立・攻防両者） | `METER_GAIN_ON_GUARD`（5） |
| EX ダメージ倍率 | `EX_DAMAGE_MULTIPLIER`（1.6×。例：波動拳 120 → 192） |
| EX 弾の拡大率 | `EX_PROJECTILE_SCALE`（1.5×。判定・描画とも大型化） |

- **メーター（`Fighter`）**：`superMeter`（float）に `gainMeter(amount)`（MAX で頭打ち）/ `hasFullMeter()` / `spendFullMeter()`（0 に）/ `setMeter(value)`（撮影・初期化用）/ `getSuperMeter()`。`reset()` で 0（ラウンドごとにリセット）。
- **メーター蓄積（Core）**：`resolveHit`（打撃ヒット / ガード / 投げ）・`updateProjectiles`（飛び道具ヒット / ガード）の決着点で `awardMeter(attacker, defender, blocked)` を呼ぶ。命中は攻撃側が多く・防御側が少なく、ガードは両者わずか。**固定値のみで乱数なし**（入力リプレイの決定性を保つ）。投げ抜けはノーダメージのため蓄積しない。
- **EX 発動（Core）**：必殺技の発動時に `f.hasFullMeter()` なら `ex=true` で `f.startSpecial(move, ex)` し `f.spendFullMeter()`。EX の効果は技種別で分かれる：
  - **飛び道具 EX**（Task 44）：`spawnProjectile(f, move, ex=true)`。`damage = round(damage × EX_DAMAGE_MULTIPLIER)`・判定/描画を `EX_PROJECTILE_SCALE` 倍。`Projectile.ex` で描画を金色グロー＋大型に。
  - **打撃必殺技 EX**（Task 54）：`Fighter` が `exAttack` を立て、`CollisionSystem.activeHitbox` が**与ダメージを `EX_DAMAGE_MULTIPLIER` 倍**にする（弾を生成しないので Core 側は飛び道具と同じ `startSpecial` 呼び出しのみ）。例：Aoi の無敵対空 `rising_dragon`(110) が満タンで EX 化＝`round(110×1.6)=176`。`isExAttack()` は攻撃中のみ true で、技が終われば自動解除（`beginAttack` が `exAttack=false` にリセット）。EX 化は乱数なし（メーター有無のみ＝決定的）。
- **描画**：画面下端に必殺技ゲージバー（蓄積中=青 / 満タン=金＝EX 可の合図）。EX 飛び道具は金色グロー＋大型。EX 打撃必殺技は **strike 矩形を金色**に描き、状態ラベルに `[EX]`（無敵対空なら `special:active [INV] [EX]`）を付す。
- **撮影**：`ScreenshotController.initialMeter(player, fallback)` で初期メーターをオーバーライド可能（`-x p1meter=100` / `p2meter=`）。EX の見え方を貯め直しなしで撮る用。
- **データ**：現状はメーター仕様を全キャラ共通の定数で持つ（JSON 変更なし）。EX をキャラ固有技（`specialMoves[]` の `ex` 変種）としてデータ化するのは将来候補。打撃 EX のさらなる強化（無敵延長・hitbox 拡大）も将来候補（現状はダメージ強化のみ）。

---

## チェーンコンボ（通常技キャンセル）（Task 45）

命中した通常技の硬直を待たず、**より強いボタンの通常技へキャンセル**して繋げる（弱→中→強の一方向ガトリング）。これによりコンボカウンター（Task 39）が実用化し、単発では届かない連続ヒットが成立する。

- **キャンセル条件（`Fighter.canChainInto(next)`）**：接地中／進行中が**通常技**（`currentMove.getButton() != null`＝必殺技・投げは不可）／その技が `ACTIVE` か `RECOVERY`／**接触済み**（`attackConnected`＝空振りキャンセル不可）／新ボタンの段位 `AttackButton.ordinal()` が現在より上（`LIGHT`<`MEDIUM`<`HEAVY` の一方向のみ）。
- **発動（`Fighter.update`）**：新規攻撃の開始ブロックに `else if (canChainInto(attackButton))` を追加し、`beginAttack(move)` で**進行中の技を即キャンセル**して上位通常技を開始する（`attackConnected`/`attackPhase` がリセットされ、新技が改めて命中判定される＝多段防止と両立）。チェーンは立ち通常技として開始（`crouchAttacking=false`/`aerialAttacking=false`）。
- **コンボ成立の仕組み**：キャンセルで硬直を飛ばすため、上位技の active が相手の `HITSTUN_FRAMES`（18）切れ前に届く。例：Aoi で `light`（5/4/10）命中 → 即 `medium`（8/6/16）へキャンセル → 即 `heavy`（14/5/28）→ `50 + 80 + 130 = 260` ダメージの **3 HITS** コンボ（コンボカウンターが表示）。
- **決定性**：入力（ボタン段位）と接触状態だけで決まり乱数なし（入力リプレイと両立）。攻撃ステート・ダメージ・hitstun のロジック自体は不変で、キャンセルの発動経路を 1 つ足しただけ。
- **データ**：チェーン順はボタン段位（`AttackButton` の `ordinal`）で決まる全キャラ共通ルール（JSON 変更なし）。技ごとのキャンセル可否ルート（特定技のみ繋がる等）をデータ化するのは将来候補。

---

## コンボダメージ補正（ダメージスケーリング）（Task 46）

コンボ（連続ヒット）が伸びるほど後続ヒットの**与ダメージを段階的に減衰**させる。チェーンコンボ（Task 45）と対になり、長いコンボのリターンを鈍らせて無限・即死コンボを抑える格闘ゲーム定番の補正。

| 項目 | 仕様 |
|---|---|
| 補正開始 | 2 ヒット目から（1 ヒット目＝単発は等倍） |
| 減衰量／ヒット | `GameConstants.COMBO_SCALE_STEP`（0.1＝1 ヒットごとに 10%減） |
| 下限倍率 | `GameConstants.COMBO_SCALE_MIN`（0.3＝これ以上は減らない） |
| 計算 | `scale(n) = n <= 1 ? 1.0 : max(MIN, 1 - (n-1)×STEP)`、与ダメージ = `max(1, round(base × scale))` |

- **実装（`Fighter`）**：`scaledComboDamage(base)` を新設し、`applyHit` / `applyThrow` で `comboCount` 加算**後**に `applyDamage(scaledComboDamage(damage))` で適用する（`comboCount` は加算済みなので 1 ヒット目は等倍、2 ヒット目以降が減衰）。ガード（`applyGuard` の chip）は加算対象外なので補正されない。最低 1 ダメージは保証。
- **表示との整合**：ダメージ数値ポップアップ（適用前後の HP 差・既出）が**そのまま補正後の値**を表示する（ポップアップ側の変更不要）。例：Aoi の弱→中→強チェーンが `50 / 72 / 104`（= 50, 80×0.9, 130×0.8）＝合計 226（補正前 260）。
- **決定性**：倍率計算のみで乱数なし（入力リプレイと両立）。攻撃ステート・hitstun・knockback は不変で、与ダメージ量だけを補正する。
- **データ**：補正パラメータは全キャラ共通の定数で持つ（JSON 変更なし）。技ごとの補正値（始動補正など）をデータ化するのは将来候補。

---

## 特殊キャンセル（通常技 → 必殺技）（Task 47）

命中した通常技を**必殺技でキャンセル**して繋ぐ（チェーンコンボ Task 45 の必殺技版）。通常技 → 飛び道具などへ硬直を待たず繋ぎ、コンボの締め・運び・ダメージ上乗せを成立させる格闘ゲームの根幹機構。

- **キャンセル前提（`Fighter.isCancelableNormal()`）**：Task 45 の `canChainInto` と共通化した private ヘルパー。接地中・進行中が**通常技**（`currentMove.getButton() != null`＝必殺技/投げ不可）・`ACTIVE` か `RECOVERY`・**接触済み**（`attackConnected`＝空振りキャンセル不可）。`canChainInto`（＋段位上昇）と `canSpecialCancel`（追加条件なし）がこれを共有する。
- **発動（`Fighter.startSpecial`）**：開始ガードを `canStartAction() || canSpecialCancel()` に拡張。新規発動（非攻撃中）に加え、命中した通常技の active/recovery 中でも必殺技を開始（`beginAttack` が `attackConnected`/`attackPhase` をリセット＝多段防止と両立）。**Core 側（`updateFighterInput` の必殺技ブロック）は `attackPhase` を見ずに `startSpecial` を呼ぶため変更不要**（コマンド入力は攻撃中も `InputHistory` に記録される）。
- **コンボ成立**：キャンセルで通常技の硬直を飛ばし、必殺技（飛び道具）の active / 弾が相手の hitstun 切れ前に届く。例：Aoi の `light`（50）→ 波動拳キャンセル（`fireball` 120）で **2 HITS**。飛び道具にもコンボダメージ補正（Task 46）が乗り、2 段目は `120×0.9=108`（合計 158）。EX 必殺技（Task 44・メーター満タン）でのキャンセルも成立する。
- **優先順**：Core は throwReq > 必殺技（コマンド成立＋攻撃）> 通常技/チェーンの順。必殺キャンセルが成立すると `attackButton=null` にして通常技/チェーンを抑止する。
- **決定性**：入力（コマンド＋攻撃）と接触状態だけで決まり乱数なし（入力リプレイと両立）。攻撃ステート・ダメージ・hitstun は不変で、`startSpecial` の開始条件を 1 つ緩めただけ。
- **データ**：キャンセル可否は「通常技 → 任意の必殺技」の全キャラ共通ルール（JSON 変更なし）。技ごとのキャンセル可否ルートのデータ化は将来候補。

---

## 多段ヒット技（Task 74）

1 つの技が active 区間中に複数回ヒットする多段技。**データ駆動**（`Move.hits`／`hitGap`）で、既存の通常技 / 必殺技にフィールドを足すだけで多段化できる。各サブヒットはのけぞり中の相手にコンボとして加算され、コンボカウンター（Task 39）・コンボダメージ補正（Task 46）がそのまま乗る。

| 項目 | 仕様 |
|---|---|
| ヒット数 | `Move.hits`（既定 1＝単発）。2 以上で多段 |
| 間隔 | `Move.hitGap`（既定 4f）。1 ヒットしてから次ヒットを許可するまでの待機 |
| 条件 | `active` が `hitGap × (hits-1)` 以上ないと後段が active 終了で空振る |

- **実装（`Fighter`）**：単発前提の `attackConnected`（boolean）を**命中回数カウンタ** `attackHits`＋待機フレーム `attackHitGap` に一般化した。`canHitNow()`＝`attackHits < move.getHits() && attackHitGap <= 0`、`markAttackConnected()` がヒットごとに `attackHits++` ＋ `attackHitGap = hitGap` を立てる。`hasAttackConnected()`（チェーン/特殊キャンセルの「接触済み」）は `attackHits > 0` に置き換え＝単発技の挙動は完全に不変（`hits=1` なら 1 回で `canHitNow` が false）。
- **判定（`resolveHit`）**：従来の「命中済みなら return」を `!canHitNow()` に置き換えただけ。多段は active 中に `hitGap` 間隔で `markAttackConnected` を繰り返す。
- **コンボ整合**：サブヒットのたびに `applyHit` が呼ばれ、相手は hitstun 継続中なので `comboCount` が加算され `N HITS!` 表示＋ダメージ補正が乗る（例：fighter009 の `twin_thrust`＝`hits:2` で **2 HITS**）。
- **決定性**：命中回数とフレームカウンタのみで決まり乱数なし（入力リプレイと両立）。投げ・飛び道具は対象外（body hitbox の打撃のみ。`hits` は body hitbox のヒット解決で参照）。

---

## カウンターヒット（Task 71）

相手の攻撃 **startup 中**（技を出しきる前）に打撃を当てると「差し返し（counter hit）」として与ダメージが増え、のけぞりが延長される。攻撃を振る側のリスクを表現し、置き技・差し込み・暴れ潰しの読み合いに価値を与える格闘ゲーム定番の機構。

| 項目 | 仕様 |
|---|---|
| 成立条件 | 非ガードの打撃ヒットで、被弾側が自分の攻撃 `STARTUP` 区間にいる（`defender.getAttackPhase() == STARTUP`） |
| ダメージ | `damage × COUNTER_HIT_DAMAGE_SCALE`（1.3 倍・最低 1） |
| のけぞり | 通常 `HITSTUN_FRAMES` に `COUNTER_HIT_BONUS_HITSTUN`（+8f）を上乗せ（カウンターからの追撃が繋がりやすい） |
| ダウン技 | ダウン（Task 60）は既に長い拘束のためダメージ倍率のみ適用し、hitstun ボーナスは加えない |
| 表示 | 被弾ラベルに `(CH)` を付して識別（`Fighter.isCounterHit()`・表示専用カウンタ `COUNTER_HIT_LABEL_FRAMES`） |

- **判定（`PhantomNexusGame.resolveHit`）**：ガード成立時は対象外（防御を崩したわけではない）。`active`/`recovery` は対象外で、**startup を潰した時のみ**カウンター扱いにして読み合いを明快にする（出し負け＝差し返し）。投げ・飛び道具のカウンターは現状なし（打撃ヒットのみ）。
- **適用**：スケール後のダメージと延長 hitstun を `applyHit`（ダウン技は `applyKnockdown`）へ渡し、直後に `defender.markCounterHit()` で表示カウンタを立てる。
- **決定性**：被弾側の攻撃区間という観測状態のみで決まり**乱数なし**（入力リプレイと両立）。倍率・ボーナスは全キャラ共通の定数（JSON 変更なし）。

---

## ダッシュ（二度押しステップ）（Task 49）

同じ方向を素早く**二度押し**すると、通常歩行より速い短いダッシュ（前ステップ／バックステップ）に入る。歩き・ジャンプに次ぐ第3の移動手段で、間合いの素早い詰め／離脱という択を足す。

| 項目 | 仕様 |
|---|---|
| 受付窓 | `GameConstants.DASH_TAP_WINDOW`（12f≒0.2 秒）以内の同方向再押下で成立 |
| 継続 | `GameConstants.DASH_FRAMES`（12f）。方向を離しても継続する確定移動 |
| 速度 | `walkSpeed × DASH_SPEED_MULTIPLIER`（2.4 倍） |
| キャンセル | 攻撃・必殺技・投げ・ジャンプ・しゃがみ・被弾でキャンセル（中断） |

- **検出（`Fighter.update`・入力系は不変）**：`moveDir` の**立ち上がりエッジ**（前フレームと方向が変わって非ゼロ）を見て、直近の同方向タップが受付窓内なら `dashFrames` を立てる。1 度目の押下では受付窓 `dashTapWindow` をアームするだけ。**入力履歴/コマンド検出（`CommandDetector`）は流用せず Fighter 内で完結**（接地・非攻撃・非しゃがみのみ発動）。
- **移動**：歩行分岐の前に `dashFrames > 0` を分岐させ、`dashDir × walkSpeed × DASH_SPEED_MULTIPLIER` で確定移動（方向入力に依らず継続）。ジャンプで `dashFrames=0` にしてキャンセル（飛び込みへ）。しゃがみ移行（`crouchHeld` の分岐）でも `dashFrames=0`（凍結回避）。`beginAttack`（攻撃/必殺技/投げ）/`applyHit`/`applyThrow`/`applyThrowTech`/`reset()` で `dashFrames=0`。
- **ガードとの両立**：バックステップ（後退方向の二度押し）は後退方向保持＝ガードと被るため、ダッシュ中は `guarding=false` で抑止する（成立した二度押しが優先）。通常の「後退を押しっぱなし」はエッジが 1 回しか立たず二度押し不成立＝ガードのまま（暴発しない）。
- **描画**：新規 `AnimationState` は足さず歩行アニメを流用し、名前ラベルを `dash` にして識別する（`isDashing()`）。
- **決定性**：入力（方向エッジ）とフレームカウンタのみで決まり乱数なし（入力リプレイと両立）。速度・受付窓は全キャラ共通の定数（JSON 変更なし）。

---

## ダッシュ攻撃（Task 65）

ダッシュ（Task 49）中に攻撃ボタンを押すと、キャラが `dashAttack` を持つ場合は通常技でなく**突進打撃**が出る。ダッシュの勢いを引き継いで前方へ踏み込みながら攻撃する、接近からの攻めの起点。**データ駆動**（`Character.dashAttack` を持つキャラだけが使える・任意フィールド）。

| 項目 | 仕様 |
|---|---|
| 発動条件 | **接地ダッシュ中**（`dashFrames > 0`）＋ 非しゃがみ ＋ 攻撃ボタン ＋ キャラが `dashAttack` を所持 |
| 技 | `Character.getDashAttack()`（通常技と同じ `Move`・`guardHeight` 有効・`button` は不要） |
| 突進 | 発動時に `velocityX = dashDir × DASH_ATTACK_LUNGE_SPEED`（14px/frame）を与え、`KNOCKBACK_FRICTION` で減衰しながら startup〜active 間に前方へスライドする（既存の `velocityX` 適用経路を流用） |
| 判定 | 通常の打撃として解決（`guardHeight` に従いガード可・`applyHit`／のけぞり・`knockdown` 等もそのまま） |

- **発動（`Fighter.update`）**：攻撃開始ブロック（`attackPhase == NONE`）で `dashAtk = !throwReq && grounded && !crouchHeld && dashFrames > 0 && def.getDashAttack() != null` を判定し、true なら `selectNormalMove` でなく `def.getDashAttack()` を選んで `beginAttack`。突進の向きは `beginAttack` が `dashFrames` を 0 にする前に `dashDir` を退避して使う。`dashAttacking` フラグを立て、攻撃終了・チェーン・`startSpecial`・他攻撃開始でクリアする（`crouchAttacking`/`aerialAttacking`/`throwing` と同じ作法）。
- **通常技との関係**：`dashAttack` は `button` を持たない（ダッシュ＋攻撃で発動）ため、チェーンコンボ（Task 45）／特殊キャンセル（Task 47）の起点（`isCancelableNormal()` は `getButton() != null` を要求）にはならない＝committal な踏み込み技。`dashAttack` を持たないキャラは従来どおりダッシュ中の攻撃が通常技へキャンセルされる（後方互換・no-op）。
- **描画**：新規 `AnimationState` は足さず ATTACK ポーズを流用し、名前ラベルを `dash_attack:<phase>` にして識別する（`isDashAttacking()`・`throw`/`special`/`crouch_attack` と同じ prefix 分岐）。
- **決定性**：入力（ダッシュ＋攻撃）とフレーム・キャラ所持技のみで決まり乱数なし（突進初速・減衰も固定値＝入力リプレイと両立）。突進初速は全キャラ共通定数 `DASH_ATTACK_LUNGE_SPEED`、技の性能は JSON（`dashAttack`）。

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
- **打撃必殺技（projectile=false）**：`Move.projectile` が false（既定）の必殺技は飛び道具を出さず、`startSpecial` → `beginAttack` で**通常技と同じ active hitbox 経路**を通る近接攻撃になる（昇龍拳タイプ。Core は projectile のときだけ弾を生成するので、無指定なら自動的に打撃）。技選択は同じく `command` 照合なので、1 キャラが**コマンド別に飛び道具と打撃の必殺技を併存**できる（例：Aoi は `fireball`=HADOUKEN と `rising_dragon`=CHARGE_SHOT）。
- **可視化**：`GameRenderer` が弾を二重円（グロー＋コア）で描画。状態ラベルは `special:<区間>`。

---

## 無敵リバーサル必殺技（Task 53）

打撃必殺技に**無敵フレーム**を付与し、相手の攻撃を抜いて切り返す**リバーサル / 対空**を成立させる。`Move` の任意フィールド `invincibleFrames`（int・既定 0）で、技の発生からそのフレーム数だけ食らい判定を失う。データ駆動（JSON 値）で無敵の長さを技ごとに設定でき、コードは無敵窓の判定だけを持つ。

- **無敵窓（`Fighter.isInvincible()`）**：進行中の技に `invincibleFrames > 0` があり、発生からの経過 `attackFrame` が `1..invincibleFrames` の間だけ無敵。攻撃中（`attackPhase != NONE`）のみ有効で、技が終われば自動的に解除。**乱数なし＝経過フレームのみで決定的**（入力リプレイと両立）。
- **当たり判定の無効化**：`CollisionSystem.isHitting`（打撃）・`hits`（飛び道具）の冒頭で `defender.isInvincible()` なら `false` を返す。これにより無敵中のファイターは打撃も飛び道具も食らわない（リバーサルが弾を抜ける）。一方、無敵技自身の active hitbox は通常どおり相手に当たる（自分が defender のときだけ無敵が効くため、攻撃判定は無効化されない）＝**無敵で抜けつつ反撃が刺さる**。
- **リスク / リターン**：無敵は発生〜早い active までで、`recovery` は長い（例 `rising_dragon` は 4/6/**30**）。空振り・ガードされると大きな隙＝撃ち得にはならない（読み合いが成立）。
- **可視化**：無敵中は状態ラベルに `[INV]` を付す（`GameRenderer.drawNameLabel`・フレームデータ依存の無敵をスクショで確認できるように）。例：`special:active [INV]`。
- **実例（fighter001 Aoi）**：`rising_dragon`（command `CHARGE_SHOT`・打撃・`invincibleFrames: 9`・dmg110）。後溜め → 前+攻撃で発動する無敵対空。証跡は「相手の中攻撃に対し、通常技なら被弾（hitstun・HP −65）／`rising_dragon` なら無敵（`[INV]`・HP 満タン）で抜けて 110 反撃」の 1 変数対比で示す。

---

## 簡易 AI（Task 21 → Task 37 → Task 50 → Task 51 → Task 55 → Task 56 → Task 57 → Task 63 → Task 64 → Task 75）

- `GameRuntime/Battle/AiController` が 1 体を状態ベースで操作する（人間の `PlayerInput` の差し替え）。Task 21 の方針は「近づいて、間合い（中心間 ≤ 150px）に入ったら通常攻撃」。攻撃後はクールダウン（45F）で連打を防ぐ。
- Core は P2 を既定で AI 制御（**F2** でトグル、撮影は `ai=false` で無効化）。AI は `Fighter.update` を人間と同じ経路で呼ぶため、移動・攻撃・押し合い・被弾はすべて共通ロジックを通る。

### AI 受け身（ukemi・Task 75）

ダウン（Task 60）させられたとき、HARD の AI は**受け身（クイック起き上がり・Task 66）**で早く立ち上がり、起き攻めへの対抗択を取る。

- **仕組み**：`control()` の最優先で、`self.isKnockedDown() && !self.canStartAction()`（＝行動不能のダウン中）の間は毎フレーム行動入力（`AttackButton.LIGHT`）を `self.update` へ渡して return する。受け身の受付窓（`UKEMI_WINDOW`・Fighter 側が持つ）内なら最早フレームで成立し、残りダウンが `UKEMI_RISE_FRAMES`(20) に短縮される。窓外の入力は Fighter が無視するので「ダウン中は入力し続ける」だけで成立する（AI は窓を知らなくてよい）。
- **暴発防止**：起き上がり確定フレーム（無敵ラッチで `isKnockedDown()` が true でも `canStartAction()` が回復するフレーム）は `!canStartAction()` の条件で除外し、そこで通常技が暴発しないようにする。
- **難易度差**：HARD のみ受け身する（NORMAL/EASY はフル `KNOCKDOWN_FRAMES`(60) ダウン＝起き攻めを受けやすい）。受け身は早く起きる代わりにダウン中無敵も早く切れる（Task 66）ためノーリスクではない。
- **決定的**：自分のダウン状態のみで判断し**乱数なし**（入力リプレイと両立）。Fighter / Core / `GameConstants` は無改修で、`AiController` に早期分岐を 1 つ足しただけ（Task 66 のクイック起き上がりをそのまま流用）。

### ダッシュ接近（Task 50）

遠距離からの接近は歩きだけだと遅いため、Task 50 で AI に**ダッシュ接近**（Task 49 の二度押し前ステップを利用）を追加した。中心間距離が `DASH_APPROACH_RANGE`(260px) より遠ければ歩行でなくダッシュで素早く間合いを詰め、それより内（≤ 260px）は従来どおり歩いて間合いを調整する。

- **仕組み**：`Fighter` のダッシュ検出は「同方向の押下エッジが受付窓内に 2 回」で成立するため、AI 側で `dashTapStep`（0=1 度目押下 → 1=ニュートラル＝離す → 2=2 度目押下＝発動）の 3 フレームを生成して人間の二度押しを模す。ダッシュ発動中（`isDashing()`）は方向を維持しパターンを 0 に戻して次の二度押しに備える。
- **状態の持ち越し防止**：ダッシュ以外の分岐（ガード反応・投げ崩し・歩き接近・通常攻撃）に入ったら `dashTapStep=0` にリセットし、間合いが変わって再びダッシュへ戻るときに中途半端なエッジ（いきなりニュートラルや 2 度目）から始まらないようにする。`reset()`（ラウンド間）でも 0 に戻す。
- **ダッシュ中の被弾防止（ガード優先）**：`Fighter` はダッシュ中（`dashFrames>0`）に `guarding=false` を強制する（Task 49・確定移動を優先）。そのままだとダッシュ接近中だけ「打撃にはガード」(Task 37) が機能せず、歩き接近なら防げた攻撃を被弾してしまう。これを防ぐため、ガード反応分岐に入ったとき自分がダッシュ中なら `self.cancelDash()`（`dashFrames=0`）でダッシュを中断してからガードする。ダッシュは AI 自身の選択なので防御のために中断してよく、`GUARD_RANGE` 内で相手の打撃を検知した瞬間にガード可能な状態へ戻る。`cancelDash()` は `Fighter` に追加した小さなフック（残りダッシュフレームを 0 にするだけ・攻撃/被弾による既存のダッシュキャンセルと同じ作法）で、呼ばれない限り挙動は不変。
- **決定的**：判断は距離と観測状態のみで**乱数を使わない**（入力リプレイと両立）。Task 49 のダッシュ機構（`Fighter`）はそのまま流用し、`AiController` の接近分岐を歩き／ダッシュに分け、ガード優先のための `cancelDash()` フックを足しただけ（Core の戦闘ロジック・`GameConstants`・JSON は不変）。

### 読み合い反応（Task 37）

Task 37 で「相手の現在状態に反応する」2 つの行動を追加した。判断は相手の観測状態（`isAttacking()` / `isThrowing()` / `isGuarding()`）と距離のみに基づき、**乱数を使わない（決定的＝入力リプレイと両立）**。

| 反応 | 条件 | 行動 |
|---|---|---|
| ガード反応 | 相手が**打撃中**（`isAttacking() && !isThrowing()`）＋ 中心間 ≤ `GUARD_RANGE`(200px) ＋ 自分が行動可能 | 後退方向を保持して**ガード**（chip のみで凌ぐ）。投げはガード不能なので対象外 |
| 投げ崩し | 相手が**ガード中**（`isGuarding()`）＋ 中心間 ≤ `THROW_RANGE`(130px) ＋ `throwMove` あり ＋ クールダウン明け | ガード不能の**投げ**で崩す（打撃は防がれるため。`throwReq=true`） |

- **優先順**：ガード反応 ＞ 投げ崩し ＞ 接近 ＞ 通常攻撃。状態反応（ガード/投げ）を距離ベース行動より優先する。
- これにより「打撃＝ガードされる → 投げで崩す／投げ＝ガード不能だがジャンプ・投げ抜けで対応」という読み合いを CPU 戦でも体験できる。

### 投げ抜け反応（Task 51）

Task 51 で 3 つ目の反応「投げ抜け」を追加した。投げはガード不能なので、ガード反応では防げない。相手が**投げ中**（`isThrowing()`）で近接なら、AI は投げ抜け猶予窓をアームして掴みを抜ける（ノーダメージ）。これで Task 37 と合わせて「**打撃＝ガード／ガード＝投げで崩す／投げ＝投げ抜け**」の三すくみが CPU 戦でも完成する。

| 反応 | 条件 | 行動 |
|---|---|---|
| 投げ抜け反応 | 相手が**投げ中**（`isThrowing()`）＋ 中心間 ≤ `THROW_TECH_RANGE`(160px) ＋ 自分が**接地**＋行動可能 | 毎フレーム `self.armThrowTech()` で投げ抜け窓をアームし、ニュートラルで抜けに専念。掴みの active で掴まれた瞬間に `canTechThrow()` が成立し**投げ抜け**（相互に弾かれ・ノーダメージ）になる |

- **優先順**（更新）：**投げ抜け反応 ＞ ガード反応 ＞ 投げ崩し ＞ 接近 ＞ 通常攻撃**。ガード不能の投げへの反応を最優先に置く。
- **仕組み**：人間が投げボタン押下でアームする投げ抜け窓（Task 36）を、AI は「相手が投げ中」を観測してアームする（掴みには startup があるので、その間に窓を立て active で抜ける）。空中の相手は掴めない（Task 35）ので AI も接地時のみ反応。`armThrowTech()` を呼ぶだけで `Fighter`/Core は無改修。
- **残る崩し（counterplay）**：AI が自分の攻撃硬直・のけぞり中（`canStartAction()==false`）は窓を立てられず掴まれる。よって「AI の技の硬直を投げで狩る」択は残り、投げが完全に死ににはならない（決定的なので人間側はタイミングを覚えれば崩せる）。
- **決定的**：判断は相手の状態（`isThrowing()`）＋距離＋自分の接地/行動可否のみで**乱数なし**（入力リプレイと両立）。

### 無敵対空（Task 55）

Task 55 で AI が初めて**必殺技**を使う反応「無敵対空」を追加した。**無敵打撃必殺技（リバーサル・Task 53）を持つキャラ**の AI は、飛び込んでくる相手（空中＋下降中）をその無敵技で迎撃する。AI は人間の入力（コマンド検出）を経由しないため、`AiController` が `findAntiAirMove()` で `specialMoves[]` から無敵打撃技を探し、`self.startSpecial(move)` を**直接**呼ぶ（直後の `self.update` が技を進める）。打撃必殺技なので飛び道具生成・メーター消費は不要＝`Fighter`/Core は無改修。

| 反応 | 条件 | 行動 |
|---|---|---|
| 無敵対空 | 相手が**空中**（`!isGrounded()`）＋**下降中**（`getVelocityY() <= 0`）＋ 中心間 ≤ `ANTI_AIR_RANGE`(170px) ＋ 自分が接地・行動可能 ＋ クールダウン明け ＋ **無敵打撃必殺技を所持** | `self.startSpecial(antiAir)` で無敵リバーサルを出し、落ちてくる相手を縦長の無敵 hitbox で迎撃する |

- **優先順**（更新）：**無敵対空 ＞ 投げ抜け反応 ＞ ガード反応 ＞ 投げ崩し ＞ 接近 ＞ 通常攻撃**。落ちてくる相手への迎撃を最優先に置く。
- **通常攻撃の地上限定化**：あわせて AI の通常攻撃分岐に `opponent.isGrounded()` 条件を追加。**空中の相手に地上通常技を振らない**（空振りするうえ、クールダウンを浪費して無敵対空の機会を潰すため）。
- **データ駆動**：対空は「キャラ JSON に無敵打撃必殺技があるか」で決まる（例：fighter002 Akane に `rising_talon`＝`CHARGE_SHOT`・打撃・`invincibleFrames:8` を追加）。該当技を持たないキャラの AI は対空しない（接近して通常戦）。新キャラに無敵技を足すだけで AI もそれで対空する。
- **決定的**：判断は相手の空中状態・下降・距離・所持技のみで**乱数なし**（入力リプレイと両立）。

### 難易度（EASY / NORMAL / HARD）（Task 56）

これまでに実装した反応群を**段階的に解放**してプレイ感を変える難易度を追加した。判断ロジック自体は同じで、**どの反応を有効にするか**だけが変わる（乱数は増やさない＝決定的・入力リプレイと両立）。`AiController.Difficulty` enum と `setDifficulty()` で持ち、各反応分岐の条件に難易度フラグ（`defends`＝NORMAL 以上 / `advanced`＝HARD のみ）を足しただけ。解放されない反応は分岐をスキップし、下位の接近 / 通常攻撃へ自然にフォールスルーする。

| 難易度 | 解放される反応 | 体感 |
|---|---|---|
| `EASY` | なし（接近＋間合いで通常攻撃のみ＝Task 21 の素の AI） | 守らず・崩さず、ひたすら接近して殴る。読み合いが無く倒しやすい |
| `NORMAL` | ＋ ガード反応 / 投げ崩し（Task 37） | 打撃はガードし、ガード偏重には投げ。基本の三すくみの片側 |
| `HARD` | ＋ 投げ抜け（Task 51）/ ダッシュ接近（Task 50）/ 無敵対空（Task 55）/ 飛び込み（Task 57）/ 下段読みしゃがみガード（Task 63）/ 飛び道具牽制（Task 64）＝**全反応** | 三すくみ完備＋素早い接近＋自分から飛び込み＋飛び込み迎撃＋下段をしゃがみガード＋遠距離は飛び道具で牽制 |

- **既定は `HARD`**：全反応有効で、**Task 56 時点では Task 55 までの従来挙動と同一**。これにより難易度導入それ自体は**既存の入力リプレイの決定性・既存スクショレシピを変えない**（難易度を試合中に変えない＝per-frame リプレイログに難易度を持たせず、起動時固定でリプレイ format 不変）。※「HARD＝従来挙動」の同一性は**その時点まで**の意味で、以降 AI に新反応を足せば（例：Task 57 の飛び込み）HARD の試合展開は変わる（難易度ゲートの仕組み＝どの反応を解放するかは不変だが、HARD の中身は反応追加ごとに更新される）。AI-on リプレイの再現範囲は「入力リプレイ（記録 / 再生）」節を参照。
- **設定**：撮影 / 起動時に `phantom.screenshot.aidiff=easy|normal|hard`（`-x aidiff=easy`）で差し替え（未指定は HARD）。HUD の操作ヒントに現在の難易度を表示（`[F2] P2 AI(hard)`）。メニュー / キーでの実行時切替は将来拡張（リプレイ format を変えずに済む範囲で）。
- **データ駆動との両立**：難易度は「どの反応を使うか」のみを切り替え、各反応の中身（対空技の有無など）は引き続きキャラ JSON 依存。例：HARD でも無敵打撃技を持たないキャラは対空しない。

### 飛び込み（ジャンプ攻撃・Task 57）

Task 57 で AI が自分から**前方ジャンプして空中攻撃を重ねる「飛び込み」**を追加した。これまでの AI は地上戦のみ（無敵対空 Task 55 は相手の飛び込みへの迎撃）で空中に行かなかったが、本タスクで攻め手として飛び込みを持つ。中距離（`ATTACK_RANGE`(150) < 中心間 ≤ `DASH_APPROACH_RANGE`(260)）でクールダウン明けに踏み切り、空中で相手へドリフトし、下降中に間合いへ入ったら空中攻撃（Task 32）を出す。**HARD のみ**（`advanced`）。

| 反応 | 条件 | 行動 |
|---|---|---|
| 飛び込み開始 | 自分・相手とも**接地**＋ `ATTACK_RANGE`(150) < 中心間 ≤ `DASH_APPROACH_RANGE`(260) ＋ クールダウン明け ＋ 行動可能 | 前方ジャンプ（`jumpReq=true`＋`moveDir=towardDir`）で踏み切り `jumpingIn` を立てる |
| 飛び込み中（空中） | `!isGrounded() && jumpingIn` | 相手へドリフト（`moveDir=towardDir`）。**下降中**（`getVelocityY() <= 0`）＋ 中心間 ≤ `JUMP_IN_ATTACK_RANGE`(130) ＋ 非攻撃中・非のけぞりなら空中攻撃（`attack=true`） |

- **空中の振る舞いは専用分岐が一手に引き受ける**：`control()` 先頭に「飛び込み中（`!isGrounded() && jumpingIn`）」分岐を置き、ドリフトと空中攻撃の発火をここで担う。地上反応（対空・投げ抜け・ガード等）はすべて空中では `canStartAction()==false` 等で自然に無効化されるため、空中の判断が他分岐に混ざらない。着地（`isGrounded()`）で `jumpingIn=false` に戻す。
- **空中攻撃の発火**：空中攻撃（Task 32）は `attackPhase==NONE` のとき `attackButton` で発動するので、既に攻撃中（`isAttacking()`）なら再発火しない（降り際に 1 回だけ）。下降中（`getVelocityY() <= 0`）に限定して「昇り際の空振り」を避け、降りながら攻撃を重ねる。
- **一辺倒の防止**：飛び込み開始はクールダウン（`ATTACK_COOLDOWN`=45F）明けのみ＝歩き接近と交互になり、毎回飛ぶわけではない（読まれにくくする）。
- **対の択**：飛び込みは無敵対空（Task 55）を持つ相手には落とされる。AI 同士（双方 HARD で一方が対空技持ち）でも「飛び込み ↔ 無敵対空」の攻防が成立する。
- **決定的**：判断は距離・接地状態・速度（`getVelocityY()`）・クールダウンのみで**乱数なし**（入力リプレイと両立）。ジャンプ機構（`Fighter` の `jumpPressed`）と空中攻撃（Task 32）はそのまま流用し、`AiController` に飛び込み分岐を足しただけ（`Fighter`/Core・`GameConstants`・JSON は不変）。`JUMP_IN_ATTACK_RANGE` 定数を `AiController` に追加。
- **優先順**（空中時）：**飛び込み中（空中専用）が最優先**。地上時は従来どおり「無敵対空 ＞ 投げ抜け ＞ ガード ＞ 投げ崩し ＞ ダッシュ接近 ＞ 飛び込み開始 ＞ 歩き接近 ＞ 通常攻撃」。
- **決定的**：難易度は起動時に固定。判断は従来どおり相手の観測状態・距離・所持技と難易度フラグのみで**乱数なし**。

### 下段読みのしゃがみガード（Task 63）

Task 63 で AI のガード反応（Task 37）に**高さ読み**を追加した。これまでの AI は打撃に対し**立ちガード一辺倒**で、下段（しゃがみ攻撃・`guardHeight:low` 技）には立ちガードが貫通されて被弾していた。本タスクで、相手の打撃が**下段**ならしゃがみガードで対応する。**HARD のみ**（`advanced`）＝NORMAL は従来どおり立ちガード（下段に弱い）が残り、難易度差になる。

- **下段の判定**：相手の攻撃が下段かは (a) `opponent.isCrouchAttacking()`（しゃがみ中に出した通常技＝実行時の下段・Task 31）か、(b) `opponent.getCurrentMove().getGuardHeight() == GuardHeight.LOW`（立ち下段＝`guardHeight:low` の技・例 Tetsu の `low_sweep`・Task 33）。どちらかなら下段。
- **配線**：ガード反応分岐（`opponentStriking && 中心間 ≤ GUARD_RANGE`）で `crouchGuard = advanced && opponentLow` を立て、`self.update(...)` の `crouchHeld` 引数として渡す（従来 false 固定）。`Fighter` 側は `crouchHeld + 後退方向保持`＝しゃがみガード（`isCrouchGuarding()`・Task 30）として既存処理がそのまま成立し、`Fighter`/Core・`GameConstants`・JSON は不変（AI に下段判定と配線を足しただけ）。
- **効果**：下段はしゃがみガードのみ成立（立ちガード貫通）・上段（overhead）は立ちガードのみ成立（しゃがみガード貫通）という既存のガード高さ属性（Task 33）に AI が乗る。overhead/mid は従来どおり立ちガード（`crouchGuard=false`）。
- **決定的**：判断は相手の観測状態（`isCrouchAttacking()` / 進行中の技の `guardHeight`）のみで**乱数なし**（入力リプレイと両立）。HARD の挙動が Task 62 までと変わる（下段をしゃがみガードするようになる）。

### 飛び道具牽制（zoner・Task 64）

Task 64 で AI が**遠距離で飛び道具を撃って牽制する**反応を追加した。これまでの AI は必殺技として**打撃必殺技（無敵対空・Task 55）だけ**を使い、飛び道具は撃たなかった（AI が `updateFighterInput` を通らず、飛び道具は弾生成に Core 連携が要るため）。本タスクでその連携を足し、**飛び道具を持つキャラ**の AI が遠距離で弾を撃つようになる。**HARD のみ**（`advanced`）。

| 反応 | 条件 | 行動 |
|---|---|---|
| 飛び道具牽制 | 中心間 > `DASH_APPROACH_RANGE`(260px)（遠距離）＋ 自分・相手とも**接地**＋ クールダウン明け ＋ 行動可能 ＋ **飛び道具技を所持** | `self.startSpecial(projectile)` で飛び道具必殺技を発動。Core が `control()` 直後に `consumePendingProjectile()` を読んで弾を生成する |

- **Core 連携（飛び道具だけの特別扱い）**：AI はコマンド検出（`updateFighterInput`）を経由しないため、打撃必殺技（対空）は `startSpecial` 直呼びだけで成立したが、**飛び道具は弾の生成だけ Core が担う**必要がある。`AiController` は発射した技を `pendingProjectile` に保持し、`consumePendingProjectile()` で 1 フレーム 1 発だけ返す。Core は `p2Ai.control(...)` の直後にこれを読み、非 `null` なら `spawnProjectile(fighter2, move, false)` する。これが**飛び道具のみ Core 連携が要る唯一の必殺技**である理由（弾という別オブジェクトの生成は `Fighter` の状態遷移では完結しない）。
- **撃ちつ詰めつ**：遠距離（>260px）で飛び道具を最優先に評価し、クールダウン中（`ATTACK_COOLDOWN`=45F）は下のダッシュ接近（Task 50）へフォールスルーする。よって「弾を撃つ → クールダウン中はダッシュで詰める → また弾」という zoner 的な動きになる。中距離（150–260px）の飛び込み（Task 57）・近接のガード/投げ等は従来どおり。
- **データ駆動**：飛び道具を撃つかは「キャラ JSON に `projectile` な必殺技があるか」で決まる（`findProjectileMove()` が `specialMoves[]` を走査）。飛び道具を持たないキャラ（純グラップラー fighter006 Iwao 等）の AI はこの分岐をスキップし、従来どおりダッシュで詰める。新キャラに飛び道具を足すだけで AI もそれで牽制する。
- **決定的**：判断は距離・接地状態・所持技・クールダウンのみで**乱数なし**（入力リプレイと両立。弾生成位置・速度も `spawnProjectile` の固定計算）。HARD の挙動が Task 63 までと変わる（遠距離で飛び道具を撃つようになる＝飛び道具持ちキャラの AI が遠距離でダッシュ一辺倒でなくなる）。
- **優先順**（更新・地上時）：**無敵対空 ＞ 投げ抜け ＞ ガード ＞ 投げ崩し ＞ 飛び道具牽制（遠距離・飛び道具持ち）＞ ダッシュ接近 ＞ 飛び込み開始 ＞ 歩き接近 ＞ 通常攻撃**。

## しゃがみ（Task 25）

- **入力**：DOWN キー押し続け（P1: S、P2: ↓）で発動。**接地中・非攻撃中・非のけぞり中**のみ遷移する。
- **行動拘束**：しゃがみ中はジャンプを受け付けない。通常技は入力可（しゃがみ攻撃として発動 → Task 28 参照）。横移動は **Task 29 で低速クロールとして許可**。DOWN を離すと立ち上がる。ただし DOWN 押下と同フレームの攻撃入力は無視（遷移フレームの誤入力防止）。
- **食らい判定**：しゃがみ中は hurtbox の高さを 1/3（`def.getHeight() / 3`、height=240 なら 80px）にする。これにより `hitboxOffsetY ≥ 100px` の飛び道具や高めの攻撃をかわせる（`CollisionSystem.hurtbox()` が `Fighter.isCrouching()` を参照）。
- **攻撃・被弾による解除**：攻撃開始（立ち攻撃のみ）または `applyHit()` で `crouching = false` にリセットする。しゃがみ攻撃中は低姿勢を維持する（Task 28）。
- **アニメーション**：`AnimationState.CROUCH`（2 フレームループ）、移動中は `CROUCH_WALK`（Task 29）、攻撃中は `CROUCH_ATTACK`（Task 28）。優先順は **のけぞり > しゃがみ攻撃 > 空中攻撃 > 攻撃 > 空中 > しゃがみガード > しゃがみ移動 > しゃがみ > ガード > 歩行 > 待機**。
- **プレースホルダ描画**：`GameRenderer` がしゃがみ中のキャラを `height / 3` の矩形で描く（スプライト導入後は専用コマに差し替え）。
- **AI**：`AiController` は通常 `crouchHeld=false` を渡す。**例外として Task 63（HARD のみ）はガード反応時に下段読みで `crouchHeld=true` を渡す**が、これはしゃがみ**ガード**であり、しゃがみ**攻撃**は依然として発動しない（AI は攻撃ボタンを伴うしゃがみ入力をしない）。

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
- **AI**：`AiController` は通常 `crouchHeld=false` でクロールしない。Task 63（HARD のみ）でガード反応時に `crouchHeld=true` を渡すが、同時に**後退方向**を保持するためしゃがみ**ガード**（`CROUCH_GUARD`）になり、前進クロール（`CROUCH_WALK`）には解決されない。

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
| AI | `AiController` は通常 `crouchHeld=false`。**Task 63（HARD のみ）はガード反応で相手の打撃が下段のとき `crouchHeld=true` を渡し、このしゃがみガードを能動的に使う**（NORMAL/EASY は立ちガードのまま＝下段に弱い） |

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
| AI | `AiController` は下段攻撃を出さない（しゃがみ攻撃をしない）。Task 63（HARD のみ）で `crouchHeld=true` を渡すのは防御（しゃがみガード）目的であり、下段の**攻撃**側は依然 AI から発生しない |

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
| AI | `AiController` は通常 `crouchHeld=false`。Task 63（HARD のみ）の下段読みはガード反応で `crouchHeld=true` を渡すが、相手が下段のときに限る。overhead に対しては下段読みが成立しないため立ちガードのままで、overhead は正しく防げる（しゃがみガードで貫通される不成立分岐には自分から入らない） |

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

## 空中投げ（air throw・Task 70）

`Character.airThrowMove`（任意 `Move`）を持つキャラは、**滞空中に投げボタン**を押すと**空中の相手専用のガード不能掴み**を発動する。地上投げ（Task 35）が地上の相手のみ掴めるのに対し、空中投げは滞空中の相手のみ掴める＝空対空の択（飛び込み合い・対空の的に対する掴み）。データ駆動で、キャラ JSON に `airThrowMove` を足すだけで増やせる（持たないキャラは従来どおり空中投げなし・後方互換）。

| 項目 | 仕様 |
|---|---|
| データ | `Shared/Types.Character.airThrowMove`（任意の `Move`・`throwMove` と同型の grab box）。未指定なら空中投げを持たない（後方互換）。検証は `validateThrowMove` を流用 |
| 発動条件 | **滞空中**（`!grounded`）+ 非攻撃中 + 投げボタンの立ち上がり + キャラに `airThrowMove` あり。Core が `throwReq=true` を渡し、`Fighter` が滞空中は `airThrowMove` を選んで `throwing=true` で起動する（地上投げと同じ専用経路・接地状態で種別を区別） |
| 成立範囲（接地状態一致） | 投げは「**掴む側と掴まれる側の接地状態が一致**」したときのみ成立する。`resolveHit` の whiff 判定を `attacker.isGrounded() != defender.isGrounded()` に一般化（地上投げ＝両者接地／空中投げ＝両者滞空）。不一致なら grab box が重なっても whiff として消費（`markAttackConnected`）し掴み直さない＝**地上投げはジャンプで、空中投げは着地で確実に回避できる** |
| ガード不能 | 地上投げと同じく `isThrowing()` 時は `blocked` 判定をスキップ（空中ガード中でも掴む） |
| 被弾 | `applyThrow` を流用（フルダメージ + 長い hitstun + 強 knockback）。被弾側は黄色のダメージ数値ポップアップ |
| 投げ抜け | 空中投げは**抜けられない**（投げ抜け窓のアームは接地時のみ・Task 36）＝committal な空対空択。地上投げのみ techable |
| 視覚 | grab box は地上投げと同じ紫（`THROW_COLOR`）、状態ラベルは `throw:<区間>`（滞空中なので JUMP ポーズ） |
| AI | `AiController` は空中で投げボタンを送らないため空中投げを出さない（影響なし） |

- **実装の要点**：(1) `Fighter.update` の投げ選択を `grounded ? throwMove : airThrowMove` に拡張、(2) Core の `throwReq` を「接地＝地上投げ／滞空＝空中投げ」の論理和に拡張、(3) `resolveHit` の whiff 判定を接地状態一致へ一般化——の 3 点。`applyThrow`・ガード不能・紫描画・ラベルはすべて地上投げの実装を流用（新ステート/新描画なし）。
- **決定性**：投げボタン入力と接地状態・grab box の重なりのみで決まり**乱数なし**（入力リプレイと両立）。`airThrowMove` はキャラ JSON のデータ。**リプレイ format 不変**だが、滞空中に投げボタンを押す既存リプレイで `airThrowMove` を持つキャラは「空振り→空中投げ」へ結果が変わり得る（戦闘仕様変更）。
- **データ例**：fighter004 Rai に `airThrowMove`（`sky_grab`・dmg110）。二段ジャンプ・空中ダッシュ（Task 68/69）と合わせて高機動な空対空キャラに。

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
| AI | **Task 51 で対応**：`AiController` は投げボタン入力の代わりに、相手が投げ中（`isThrowing()`）で近接（≤ `THROW_TECH_RANGE`）かつ自分が接地・行動可能なら毎フレーム `armThrowTech()` を直接呼んで投げ抜けする（詳細は「簡易 AI」節の「投げ抜け反応（Task 51）」サブ節） |
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
- **再現範囲は「同一ビルド（同一 AI ロジック版）内」**：再生時、人間入力は記録した押下を注入し直すが、**AI フレームは保存した決定結果ではなく `AiController` がその場で再計算する**（記録形式は決定結果でなく「そのフレームが AI 制御だったか」の 1 bit だけを持つ）。よって AI(P2) を含む試合は **AI ロジックを変えると再生結果が変わり得る**——AI に新反応を足すたび（Task 37/50/51/55/57…）、その反応に入る局面で旧 AI-on ログは別試合になる。これは入力（人間）リプレイの決定性とは別の話で、**AI-on リプレイはクロス AI 版の保存物ではなく同一ビルド内の再現ツール**という割り切り（記録形式 `v1` は据え置き・難易度や AI revision は保存しない）。人間どうし（記録時 `ai=false`）のリプレイはこの影響を受けず AI 版に依らず完全再現する。

---

## やらないこと（MVP）

コンボ補正／高度な物理／オンライン対戦（第一設計書「MVP でやらないこと」）。
※当初 MVP 対象外だったガード・投げ抜け・高度な AI は順次実装済み：ガードは Task 27（立ちガード）・Task 30（しゃがみガード）・Task 31/33（下段・ガード高さ属性）、投げ抜け（throw tech）は Task 36、AI の読み合い反応（ガード/投げ崩し）は Task 37、AI の投げ抜け反応は Task 51（→ 三すくみ完成）、AI のダッシュ接近は Task 50。AI のさらなる拡張（ジャンプ・しゃがみ系・難易度）は将来拡張。

## 変更履歴

- (Task 75) AI 受け身（ukemi）を追記。`AiController.control()` の最優先に「HARD かつ `self.isKnockedDown() && !self.canStartAction()`（行動不能のダウン中）の間は毎フレーム行動入力を出して return」する早期分岐を追加。受け身の受付窓（Task 66・Fighter 側）内なら最早フレームでクイック起き上がりが成立する＝起き攻めへの対抗。`!canStartAction()` で起き上がり確定フレームを除外し通常技の暴発を防ぐ。HARD のみ（NORMAL/EASY はフルダウン）。Fighter/Core/GameConstants は無改修（Task 66 を流用）。乱数なし＝決定的（自分のダウン状態のみ・入力リプレイと両立）。「AI 受け身（ukemi・Task 75）」節・AI 節見出し・冒頭サマリを追加。
- (Task 71) カウンターヒットを追記。`GameConstants` に `COUNTER_HIT_DAMAGE_SCALE`(1.3)・`COUNTER_HIT_BONUS_HITSTUN`(8)・`COUNTER_HIT_LABEL_FRAMES` を追加。`Fighter` に表示専用 `counterHitFrames`＋`markCounterHit()`/`isCounterHit()` を追加（`update()` 冒頭で減衰・`reset()` でクリア）。`PhantomNexusGame.resolveHit` の非ガード打撃分岐で被弾側が `STARTUP` 区間なら counter とし、ダメージを 1.3 倍・通常ヒットは hitstun を +8 して適用、`markCounterHit()` を呼ぶ（ダウン技は倍率のみ）。`GameRenderer.drawNameLabel` が被弾ラベルに `(CH)` を付す。グローバル機構（JSON 変更なし）・乱数なし・リプレイ format 不変だが、相手の startup を潰す既存リプレイはダメージ/hitstun が変わり得る（戦闘仕様変更）。「カウンターヒット（Task 71）」節・冒頭サマリを追加。
- (Task 70) 空中投げ（air throw）を追記。`Shared/Types/Character` に任意 `airThrowMove`（Move・省略可・後方互換）を追加（検証は `validateThrowMove` 流用）。`Fighter.update` の投げ選択を `grounded ? throwMove : airThrowMove` に拡張。Core の `throwReq` を「接地＝地上投げ／滞空＝空中投げ（airThrowMove 所持）」の論理和に拡張。`resolveHit` の投げ whiff 判定を `attacker.isGrounded() != defender.isGrounded()`（接地状態一致）に一般化＝地上投げはジャンプで、空中投げは着地で回避可。`applyThrow`・ガード不能・紫 grab box・`throw` ラベルは地上投げの実装を流用（新ステート/新描画なし）。空中投げは投げ抜け不可（tech 窓のアームは接地時のみ）＝committal な空対空択。例示として fighter004 Rai に `airThrowMove`（`sky_grab`・dmg110）。乱数なし・リプレイ format 不変だが、滞空中に投げボタンを押す既存リプレイは `airThrowMove` 持ちキャラで結果が変わり得る（戦闘仕様変更）。「空中投げ（air throw・Task 70）」節・冒頭サマリを追加（DataFormat.md にもフィールド/変更履歴を追加）。
- (Task 69) 空中ダッシュ（air dash）を追記。`Shared/Types/Character` に任意 int `airDashes`（既定 0・後方互換・`getAirDashes()` が負値→0）を追加。`Fighter` に `airDashesRemaining` フィールドを追加し、ダッシュ二度押し検出に `canAirDash`（`!grounded && airDashesRemaining>0 && attackPhase==NONE && dashFrames<=0`）を併設、成立で既存 `dashFrames`／`dashDir` を起動＋`airDashesRemaining--`。既存の `dashFrames>0` 滑空分岐をそのまま流用（重力継続＝弧を描く）。接地で `airDashesRemaining` 回復＋着地で `dashFrames` クリア（地上ダッシュへ持ち越さない）。`reset()`・コンストラクタで満タン。ダッシュ攻撃（Task 65・`grounded` 条件）は空中ダッシュ中非発動・空中ガード（Task 59）・二段ジャンプ（Task 68）は不変。データ駆動（`airDashes>0` のキャラのみ）・後方互換（既定 0）。例示として fighter004 Rai に `airDashes:1`。乱数なし・リプレイ format 不変だが、滞空中の方向二度押しを含む既存リプレイは `airDashes>0` キャラで結果が変わり得る（戦闘仕様変更）。「空中ダッシュ（air dash）（Task 69）」節・冒頭サマリを追加（DataFormat.md にもフィールド/変更履歴を追加）。
- (Task 68) 二段ジャンプ（air jump）を追記。`Shared/Types/Character` に任意 int `airJumps`（既定 0・後方互換・`getAirJumps()` が負値→0）を追加。`Fighter` に `airJumpsRemaining` フィールドを追加し、`update()` の空中分岐に `else if (jumpPressed && !grounded && airJumpsRemaining > 0)`（`velocityY=jumpPower` 上書き＋`airJumpsRemaining--`）を追加。着地ブロック・`reset()`・コンストラクタで `airJumpsRemaining = def.getAirJumps()` に回復。地上ジャンプは回数非消費。空中攻撃（Task 32）・空中ガード（Task 59）は不変。データ駆動（`airJumps>0` のキャラのみ）・後方互換（既定 0 は従来どおり地上ジャンプのみ）。例示として fighter004 Rai に `airJumps:1`。乱数なし・リプレイ format 不変だが、滞空中ジャンプ入力を含む既存リプレイは `airJumps>0` キャラで結果が変わり得る（戦闘仕様変更）。「二段ジャンプ（air jump）（Task 68）」節・ジャンプ節・冒頭サマリを追加（DataFormat.md にもフィールド/変更履歴を追加）。
- (Task 66) 受け身（ukemi・クイック起き上がり）を追記。`GameConstants` に `UKEMI_WINDOW`(12)・`UKEMI_RISE_FRAMES`(20) を追加。`Fighter` のダウン inert 分岐（Task 60）で `knockdownFrames` 減算前に経過フレームを算出し、`ukemiInput`（攻撃/ジャンプ/投げ）が `UKEMI_WINDOW` 以内かつ残りが `UKEMI_RISE_FRAMES` 超なら `knockdownFrames` を `UKEMI_RISE_FRAMES` に短縮して `ukemiRecovery` フラグを立てる。`isUkemiRecovering()` を追加し、`GameRenderer.drawNameLabel` がダウンラベルを `knockdown(ukemi)` に切替。`applyKnockdown`・`reset()` で `ukemiRecovery=false`。ダウン中無敵（Task 60）は短縮後も `knockdownFrames>0` の間そのまま効くので、受け身で縮めれば無敵も早く切れる＝トレードオフが自動成立（専用調整不要）。JSON 変更なし（グローバル機構）。乱数なし・リプレイ format 不変（受け身は通常の行動入力で記録済み）だが、ダウン直後に行動入力を含む既存リプレイは結果が変わり得る（戦闘仕様変更）。AI は現状ダウン中に入力しないため受け身しない（人間のみ・将来拡張）。「受け身（ukemi・クイック起き上がり）（Task 66）」節・ダウン節の起き上がり行・ステート一覧・冒頭サマリを追加。
- (Task 65) ダッシュ攻撃を追記。`Shared/Types/Character` に任意 `dashAttack`（`Move`）を追加し、`GameConstants` に `DASH_ATTACK_LUNGE_SPEED`(14) を追加。`Fighter` の攻撃開始ブロックで「接地ダッシュ中（`dashFrames>0`）＋非しゃがみ＋攻撃＋`dashAttack` 所持」なら通常技でなく `def.getDashAttack()` を `beginAttack` し、`dashAttacking` フラグを立てて `velocityX = dashDir × DASH_ATTACK_LUNGE_SPEED`（既存 velocityX 適用＋減衰経路を流用＝前方突進）を与える（`beginAttack` が dashFrames を 0 にする前に `dashDir` を退避）。`isDashAttacking()` を追加し、攻撃終了・チェーン・`startSpecial` でフラグをクリア。`GameRenderer.drawNameLabel` に `dash_attack:<phase>` prefix を追加（ATTACK ポーズ流用）。`CharacterLoader.validateDashAttack`（任意・null 許可・フレーム/hitbox/guardHeight 検証）を追加。`dashAttack` は `button` を持たないためチェーン/特殊キャンセルの起点にはならない（committal）。例示として fighter004 Rai に `dash_shoulder`（dmg80）を追加。データ駆動（持つキャラだけ使える）・後方互換（持たないキャラはダッシュ中の攻撃が従来どおり通常技へキャンセル＝no-op）・乱数なし（突進初速/減衰も固定＝リプレイ format 不変）。「ダッシュ攻撃（Task 65）」節・ステート一覧・冒頭サマリを追加（DataFormat.md にもフィールド/Move 節を追加）。
- (Task 64) AI の飛び道具牽制（zoner）を追記。`AiController` に `findProjectileMove()`（`specialMoves[]` から `projectile` な技を探す）・`pendingProjectile` フィールド・`consumePendingProjectile()` を追加し、遠距離（中心間 > `DASH_APPROACH_RANGE`(260px)）で自分・相手とも接地・クールダウン明け・飛び道具持ちなら `self.startSpecial(projectile)` で発射する分岐を**ダッシュ接近の前**に追加（**HARD のみ**）。`PhantomNexusGame` は `p2Ai.control(...)` の直後に `consumePendingProjectile()` を読み、非 null なら `spawnProjectile(fighter2, move, false)` で弾を生成する（**飛び道具のみ Core 連携が要る唯一の必殺技**＝弾という別オブジェクト生成は `Fighter` の状態遷移で完結しないため。打撃必殺技＝対空 Task 55 は Core 無改修だった）。クールダウン中はダッシュ接近へフォールスルー＝撃ちつ詰めつの zoner 行動。`Fighter`/`CollisionSystem`・`GameConstants`・JSON は不変。データ駆動（飛び道具を持たないキャラの AI はこの分岐をスキップ）。乱数なし（決定的・入力リプレイと両立／弾生成位置・速度も固定計算）だが HARD の挙動が Task 63 までと変わる（飛び道具持ちキャラの AI が遠距離で弾を撃つ）。「簡易 AI」節に「飛び道具牽制（zoner・Task 64）」サブ節を追加し、難易度 HARD 行・優先順を更新。
- (Task 63) AI の下段読みしゃがみガードを追記。`AiController` のガード反応分岐（Task 37）に高さ読みを足し、相手の打撃が下段（`opponent.isCrouchAttacking()` か進行中の技の `getGuardHeight()==GuardHeight.LOW`）なら `crouchGuard = advanced && opponentLow` を立てて `self.update` の `crouchHeld`（従来 false 固定）として渡す。これで `Fighter` 既存のしゃがみガード（後退方向保持＋crouchHeld・Task 30）が成立し、下段を立ちガード貫通させず防げる。**HARD のみ**（NORMAL は従来どおり立ちガードで下段に弱い＝難易度差）。`AiController` に `GuardHeight` import と `crouchGuard` ローカル・下段判定を追加しただけで `Fighter`/Core・`GameConstants`・JSON は不変。乱数なし（決定的・入力リプレイと両立）だが HARD の挙動が Task 62 までと変わる（下段に対ししゃがみガードする）。「簡易 AI」節に「下段読みのしゃがみガード（Task 63）」サブ節を追加し、難易度 HARD 行を更新。
- (Task 60) ダウン（knockdown）を追記。`Shared/Types/Move` に任意 boolean `knockdown`（既定 false・後方互換）を追加し、`GameConstants` に `KNOCKDOWN_FRAMES`(60)・`KNOCKDOWN_KNOCKBACK_SCALE`(1.4) を追加。`Fighter` に `knockdownFrames` フィールド・`applyKnockdown()`・`isKnockedDown()` を追加し、`update()` 冒頭に hitstun と並ぶ inert 分岐（ダウン優先・行動不能・knockback 減衰・起き上がり）を実装。`guarding` 算出に `knockdownFrames <= 0` を追加（ダウン中はガード不可）。`reset()` で `knockdownFrames=0`。`CollisionSystem.isHitting`/`hits` の無敵ゲートに `|| defender.isKnockedDown()` を追加（ダウン中は打撃・飛び道具とも被弾無敵＝起き攻め/OTG なし）。`PhantomNexusGame.resolveHit` が非ガード・`attacker.getCurrentMove().isKnockdown()` のとき `applyHit` の代わりに `applyKnockdown` を呼ぶ（投げ・飛び道具は対象外）。`FighterAnimator.resolve` がダウンを HITSTUN ポーズへ流用し、`GameRenderer.drawNameLabel` が `knockdown` ラベルを最優先で表示。`fighter001` の `heavy_slam` に `knockdown: true` を付与（データ駆動の実例）。乱数なし・リプレイ format 不変だが、ダウン技ヒットを含む既存リプレイは「のけぞり→ダウン」へ結果が変わり得る（戦闘仕様変更）。「ダウン（knockdown）（Task 60）」節・ステート一覧・冒頭サマリを追加。
- (Task 59) 空中ガードを追記。`Fighter.guarding` の算出条件から `grounded` を外し、滞空中でも後退方向保持でガードが成立するようにした（接地ガード Task 27／しゃがみガード Task 30／ガードクラッシュ Task 43 の既存ロジックは不変・空中ガードもゲージ消費とクラッシュを共有）。ジャンプ成立フレームの `guarding=false`（旧「空中ガード不可」）を 2 箇所（通常ジャンプ・ダッシュジャンプ）から除去。`isAirGuarding()`（`guarding && !grounded`）を追加し、`GameRenderer.drawNameLabel` に `air_guard` ラベルを追加（描画は JUMP ポーズ＋既存の青オーバーレイを流用＝専用 `AnimationState` は追加しない）。空中ガードは立ち扱い（`crouching` は接地時のみ true）で、飛び道具（`updateProjectiles` は高さ判定なしで一律ガード可）と中段 / 上段を chip で凌ぎ、下段は防げない（下段 hitbox は滞空 hurtbox に通常届かない）。あわせて `AiController` の投げ崩し分岐に `opponent.isGrounded()` を追加（空中ガード相手は掴めない＝Task 35 の地上限定投げ・空中ガード導入前は `isGuarding()` が接地を含意したため**従来挙動に対し no-op**）。`resolveHit`/`applyGuard`・`CollisionSystem`・`GameConstants`・JSON スキーマは不変。判断に乱数を増やさず**リプレイ format も不変**だが、滞空中に後退を保持した展開を含む既存リプレイは「被弾→空中ガード」へ結果が変わり得る（戦闘仕様変更のため。AI-on リプレイの再現範囲は「入力リプレイ（記録 / 再生）」節を参照）。ガード節の表（空中ガード＝可）・`Fighter.guarding` 算出の記述・ステート一覧を更新。
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
- (Task 57) AI のジャンプ攻撃（飛び込み）を追記。`AiController` に飛び込みを追加：`control()` 先頭に「飛び込み中（`!isGrounded() && jumpingIn`）」専用分岐を置き、空中で相手へドリフトしつつ下降中（`getVelocityY() <= 0`）に中心間 ≤ `JUMP_IN_ATTACK_RANGE`(130) で空中攻撃（Task 32）を出す。地上では `ATTACK_RANGE`(150) < 中心間 ≤ `DASH_APPROACH_RANGE`(260)・クールダウン明け・行動可能・両者接地で飛び込みを開始（`jumpReq=true`＋`moveDir=towardDir`＋`jumpingIn=true`）。**HARD のみ**（`advanced`）。着地で `jumpingIn=false` に戻す。`self.update` の第 2 引数（`jumpPressed`）にジャンプ要求を渡す形に変更（従来 false 固定）。`jumpingIn` フィールド・`JUMP_IN_ATTACK_RANGE` 定数を追加し `reset()` で `jumpingIn=false`。クールダウン明けのみ＝歩き接近と交互で一辺倒にならず、無敵対空（Task 55）持ちの相手には落とされる＝対の択。判断は距離・接地・速度・クールダウンのみ＝**乱数なし（決定的・入力リプレイと両立）**。`Fighter`/Core・`GameConstants`・JSON は不変（AI の分岐＋ジャンプ要求の配線のみ・空中攻撃 Task 32／ジャンプ機構は流用）。「簡易 AI」節に「飛び込み（ジャンプ攻撃・Task 57）」サブ節を追加し、難易度（Task 56）の HARD 行を更新。
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
- (Task 38) ヒットスパークを追記。`GameRuntime/Battle/HitSpark`（種別・原点・寿命を持つ POJO）を新設し、`resolveHit`（ヒット/ガード/投げ/投げ抜け）・`updateProjectiles` でダメージポップアップと同位置に生成（通常ヒット=黄/白・ガード=青）。`GameConstants.HIT_SPARK_FRAMES`（12）を追加。`GameRenderer.renderScene` に `sparks` 引数を追加しオーバーレイパス（`ShapeRenderer.Filled`）で放射スポーク＋縮小コアを拡大＋フェード描画。`PhantomNexusGame` が一覧を保持・毎フレーム更新（凍結ガード前）・ラウンドリセットでクリア。純粋な演出で戦闘結果には影響しない（`DamagePopup` と同じパターン）。「ヒットスパーク（Task 38）」節を追加。
- (Task 39) コンボカウンターを追記。`Fighter` に `comboCount` フィールド・`getComboCount()` を追加し、`applyHit`/`applyThrow` の冒頭で hitstun 継続なら +1・neutral からは =1 と計数。`update()` の hitstun 復帰（`hitstunFrames == 0`）・`applyThrowTech`・`reset()` で 0 にリセット。`GameRenderer` は `comboCount >= 2` の被弾側頭上に `N HITS!`（オレンジ・拡大）を描画（描画後に色/倍率を既定へ復帰）。戦闘ロジック（ダメージ・hitstun）は不変で計数フィールド＋表示のみ追加。乱数なし＝決定的。「コンボカウンター（Task 39）」節を追加。
- (Task 42) ラウンド開始イントロを追記。`RoundManager` に `introCountdown`/`introFrames`（コンストラクタ引数・既定 `GameConstants.ROUND_INTRO_FRAMES`＝90f、`0` で無効）を追加し、`update()` 冒頭で `introCountdown > 0` の間は戦闘・タイマー・勝敗判定を凍結してカウントのみ進める。`isRoundIntro()`/`isFightFlash()`（末尾 `FIGHT_FLASH_FRAMES`＝30f）/`getIntroCountdown()` を公開。Core の戦闘ガードを `!isBetweenRounds() && !isRoundIntro()` に拡張。`GameRenderer.drawRoundIntroBanner`（"ROUND N"=白 / "FIGHT!"=赤・拡大、描画後に既定へ復帰）を追加。撮影モードは `ScreenshotController.roundIntroEnabled()` で既定スキップ（`intro=true` で有効化）＝既存スクショレシピの後方互換。リプレイは記録/再生とも同一イントロ長で決定的。「ラウンド開始イントロ（Task 42）」節を追加。
- (Task 43) ガードゲージ／ガードクラッシュを追記。`Fighter` に `guardGauge`（float・既定 `GUARD_GAUGE_MAX`）/`guardBreakFrames` を追加。`applyGuard()` で攻撃力に応じてゲージを削り（`max(1, 攻撃力/GUARD_DRAIN_DIVISOR)`）、0 以下でガードクラッシュ（ゲージ満タン復帰＋`hitstunFrames`/`guardBreakFrames` を `GUARD_BREAK_FRAMES`＝40f にセット＝行動不能・ガード不能）。`update()` 先頭で `guardBreakFrames` 減衰、`guarding` 算出後に非ガード・非クラッシュなら回復（`GUARD_REGEN_PER_FRAME`）。`reset()`/`applyHit()`/`applyThrow()` でクリア。`isGuardBroken()`/`getGuardGauge()` を公開。クラッシュ中は `hitstunFrames > 0` で `guarding` が false になり `resolveHit` がフル `applyHit` を呼ぶ（専用の貫通分岐なし）。`GameRenderer` は HP バー直下にガードゲージバー（残量わずか＝橙警告）、崩された側頭上に `GUARD BREAK!`（画面端でも見切れない `drawCenteredClamped`）。状態ラベルは `guard_break` を hitstun より先に評価。ハードコード回避のため `GameRenderer` に `STATE_LABEL_GUARD_BREAK`/`TEXT_GUARD_BREAK` 定数を追加。`GameConstants` に `GUARD_GAUGE_MAX`/`GUARD_DRAIN_DIVISOR`/`GUARD_REGEN_PER_FRAME`/`GUARD_BREAK_FRAMES` を追加。乱数なし＝決定的。全キャラ共通の定数で持つ（JSON 変更なし）。「ガードゲージ／ガードクラッシュ（Task 43）」節を追加。
- (Task 44) 必殺技ゲージ／EX 必殺技を追記。`Fighter` に `superMeter`（float）と `gainMeter`/`hasFullMeter`/`spendFullMeter`/`setMeter`/`getSuperMeter` を追加（`reset()` で 0）。Core は `resolveHit`/`updateProjectiles` の決着点で `awardMeter(attacker, defender, blocked)` を呼び、命中=攻撃側多め(`METER_GAIN_ON_HIT`)・防御側少なめ(`METER_GAIN_ON_TAKE`)・ガード=両者わずか(`METER_GAIN_ON_GUARD`)を**固定値（乱数なし）**で加算。必殺技（飛び道具）発動時に満タンなら `spendFullMeter()` して EX 発射（`spawnProjectile(f, move, ex=true)`：ダメージ `EX_DAMAGE_MULTIPLIER`(1.6)倍・判定/描画 `EX_PROJECTILE_SCALE`(1.5)倍）。`Projectile` に `ex` フラグを追加し描画で金色グロー＋大型に。`GameRenderer` は画面下端に必殺技ゲージバー（青／満タン金）を追加。`ScreenshotController.initialMeter()`（`p1meter`/`p2meter` オーバーライド）を追加し `build.gradle` 転送リストへ反映。`GameConstants` に `SUPER_METER_MAX`/`METER_GAIN_ON_HIT`/`METER_GAIN_ON_TAKE`/`METER_GAIN_ON_GUARD`/`EX_DAMAGE_MULTIPLIER`/`EX_PROJECTILE_SCALE` を追加。全キャラ共通の定数で持つ（JSON 変更なし）。「必殺技ゲージ／EX 必殺技（Task 44）」節を追加。
- (Task 45) チェーンコンボ（通常技キャンセル）を追記。`Fighter.canChainInto(AttackButton)` を新設（接地・進行中が通常技・`ACTIVE`/`RECOVERY`・`attackConnected`・新ボタン段位 `ordinal` が現在より上）。`update()` の攻撃開始ブロックに `else if (canChainInto(...))` を足し、命中した通常技を上位ボタンの通常技へ `beginAttack` で即キャンセル（`attackConnected`/`attackPhase` リセット＝多段防止と両立）。硬直を飛ばすため上位技の active が hitstun 切れ前に届き、弱→中→強の 3 連ヒット（コンボカウンターが `3 HITS!`・例 Aoi で 50+80+130=260）が成立。チェーン順はボタン段位の全キャラ共通ルールで JSON 変更なし。乱数なし＝決定的（攻撃ステート/ダメージ/hitstun のロジックは不変で発動経路を 1 つ追加しただけ）。「チェーンコンボ（通常技キャンセル）（Task 45）」節を追加。
- (Task 46) コンボダメージ補正（ダメージスケーリング）を追記。`Fighter.scaledComboDamage(base)` を新設し、`applyHit`/`applyThrow` で `comboCount` 加算後に `applyDamage(scaledComboDamage(damage))` で適用（1 ヒット目は等倍、2 ヒット目以降は `1 - (n-1)×COMBO_SCALE_STEP` を `COMBO_SCALE_MIN` で下限を打って乗算・最低 1 ダメージ保証）。ガードの chip は非対象。ダメージ数値ポップアップ（HP 差）が補正後の値を自動表示（例：Aoi チェーン 50/72/104＝合計 226、補正前 260）。`GameConstants` に `COMBO_SCALE_STEP`(0.1)/`COMBO_SCALE_MIN`(0.3) を追加。乱数なし＝決定的（攻撃ステート/hitstun/knockback は不変で与ダメージ量のみ補正）。全キャラ共通の定数で持つ（JSON 変更なし）。「コンボダメージ補正（ダメージスケーリング）（Task 46）」節を追加。
- (Task 47) 特殊キャンセル（通常技→必殺技）を追記。Task 45 の `canChainInto` を private ヘルパー `Fighter.isCancelableNormal()`（接地・通常技・`ACTIVE`/`RECOVERY`・`attackConnected`）へ共通化し、`canSpecialCancel()` を新設（追加条件なし）。`startSpecial` の開始ガードを `canStartAction() || canSpecialCancel()` に拡張し、命中した通常技を必殺技でキャンセルできるようにした（`beginAttack` リセットで多段防止と両立）。Core の必殺技ブロックは `attackPhase` 非依存で `startSpecial` を呼ぶため変更不要。例：Aoi `light`(50) → 波動拳キャンセル → 2 HITS（飛び道具にもコンボ補正が乗り 120×0.9=108・合計 158）。乱数なし＝決定的（`startSpecial` の開始条件を 1 つ緩めただけで攻撃ステート/ダメージ/hitstun は不変）。全キャラ共通ルール（JSON 変更なし）。「特殊キャンセル（通常技 → 必殺技）（Task 47）」節を追加。
- (Task 56) AI 難易度（EASY/NORMAL/HARD）を追記。`AiController` に `Difficulty` enum（＋`fromToken`）・`difficulty` フィールド（既定 HARD）・`setDifficulty`/`getDifficulty` を追加。`control()` の各反応分岐の条件に難易度フラグを付加：`defends`（`!= EASY`）でガード反応 / 投げ崩し、`advanced`（`== HARD`）で投げ抜け / ダッシュ接近 / 無敵対空をゲート。解放されない反応は分岐スキップで接近 / 通常攻撃へフォールスルー。`ScreenshotController.aiDifficulty(fallback)`（生トークンを返し Debug→Battle 依存を作らない）を追加し Core が `Difficulty.fromToken` で解決して `setDifficulty`。HUD の操作ヒントに難易度を表示（`aiDifficultyLabel()`）。既定 HARD＝従来挙動＝既存リプレイ/レシピ不変（難易度は起動時固定でリプレイ format 不変）。`Infra/Build/build.gradle` の転送リストへ `phantom.screenshot.aidiff` を追加。乱数は増やさない＝決定的。`GameConstants`/JSON スキーマ・`Fighter`/Core の戦闘ロジックは不変（AI の分岐ゲートのみ）。「簡易 AI」節に「難易度（Task 56）」サブ節を追加。
- (Task 55) AI の無敵対空を追記。`AiController` に最優先の反応分岐を追加：相手が空中（`!isGrounded()`）＋下降中（`getVelocityY() <= 0`）＋中心間 ≤ `ANTI_AIR_RANGE`(170px)＋自分が接地・行動可能・クールダウン明けで、`findAntiAirMove()`（`specialMoves[]` から `!isProjectile() && invincibleFrames>0` を探す）が見つかれば `self.startSpecial(antiAir)` を直接呼ぶ（AI はコマンド検出を経由しないため）。打撃必殺技なので飛び道具/メーターの Core 処理は不要＝`Fighter`/Core 無改修（`Fighter.getVelocityY()` getter のみ追加）。あわせて通常攻撃分岐に `opponent.isGrounded()` を追加し空中の相手に地上技を振らない（空振り・クールダウン浪費の回避）。データ駆動：`fighter002.json`（Akane）に無敵打撃必殺技 `rising_talon`（`CHARGE_SHOT`・`invincibleFrames:8`・dmg95）を追加し、AI がこれで飛び込みを迎撃。優先順を「無敵対空 ＞ 投げ抜け ＞ ガード ＞ 投げ崩し ＞ 接近 ＞ 通常攻撃」に更新。判断は相手の空中状態・下降・距離・所持技のみ＝**乱数なし（決定的・入力リプレイと両立）**。`ANTI_AIR_RANGE` 定数を `AiController` に追加。`GameConstants`/JSON スキーマは不変。「簡易 AI」節に「無敵対空（Task 55）」サブ節を追加。
- (Task 54) EX 打撃必殺技を追記。EX（メーター満タンで消費して強化）を飛び道具だけでなく**打撃必殺技**にも拡張。`Fighter` に `exAttack`（boolean）と `startSpecial(Move, boolean ex)` オーバーロード・`isExAttack()` を追加（`beginAttack` が `exAttack=false` にリセット、EX 必殺技開始時のみ true）。`CollisionSystem.activeHitbox` が `f.isExAttack()` のとき与ダメージを `EX_DAMAGE_MULTIPLIER`(1.6) 倍にする。Core の必殺技発動を `boolean ex = special!=null && f.hasFullMeter()` に変更し `startSpecial(special, ex)`＋満タンなら `spendFullMeter()`（飛び道具/打撃の両方で消費）。`GameRenderer` は EX 打撃の strike 矩形を金色（`EX_PROJECTILE_GLOW`）に描き、状態ラベルに `[EX]`（定数 `STATE_LABEL_EX_SUFFIX`）を付す。例：Aoi の `rising_dragon`(110・無敵対空) が満タンで `round(110×1.6)=176` の EX 無敵対空に（`special:active [INV] [EX]`・金色 strike）。`GameConstants`/JSON スキーマは不変（既存 `EX_DAMAGE_MULTIPLIER` を流用・データ追加なし）。乱数なし＝決定的。「必殺技ゲージ／EX 必殺技」節の EX 発動・描画を打撃対応に更新。
- (Task 53) 打撃必殺技 / 無敵リバーサルを追記。`Shared/Types/Move` に任意 `invincibleFrames`（int・既定 0・getter は負値を 0 に丸め）を追加。`Fighter.isInvincible()`（進行中の技に `invincibleFrames>0` があり `attackFrame<=invincibleFrames` の間 true・`attackPhase!=NONE` 限定）を新設。`CollisionSystem.isHitting`/`hits` の冒頭で `defender.isInvincible()` なら被弾しない（打撃・飛び道具とも）。`GameRenderer.drawNameLabel` は無敵中に状態ラベルへ `[INV]` を付す（定数 `STATE_LABEL_INVINCIBLE_SUFFIX`）。あわせて**打撃必殺技（`projectile=false` の必殺技）**が `startSpecial`→`beginAttack` の通常 hitbox 経路で動くことを明文化（Core は projectile 時のみ弾を生成＝既存実装で打撃必殺技は動作。今回が初の実例）。`fighter001.json`（Aoi）に 2 つ目の必殺技 `rising_dragon`（command `CHARGE_SHOT`・打撃・`invincibleFrames:9`・dmg110・4/6/30）を追加し、飛び道具（HADOUKEN）＋無敵対空（CHARGE_SHOT）の 2 系統を実証。乱数なし＝決定的（経過フレームのみ）。`GameConstants`/JSON スキーマ（後方互換）は不変。「必殺技ステート」節に打撃必殺技を追記し「無敵リバーサル必殺技（Task 53）」節を新設。
- (Task 51) AI の投げ抜け反応を追記。`AiController.control` に最優先の反応分岐を追加：相手が投げ中（`isThrowing()`）＋中心間 ≤ `THROW_TECH_RANGE`(160px)＋自分が接地＋行動可能なら、毎フレーム `self.armThrowTech()` で投げ抜け窓をアームしニュートラルで抜けに専念する（掴みの startup 中に窓を立て active で投げ抜け＝ノーダメージ）。優先順を「投げ抜け反応 ＞ ガード反応 ＞ 投げ崩し ＞ 接近 ＞ 通常攻撃」に更新。これで Task 37 と合わせ「打撃＝ガード／ガード＝投げ／投げ＝投げ抜け」の三すくみが CPU 戦でも完成。AI の攻撃硬直・のけぞり中（`canStartAction()==false`）は窓を立てられず掴まれる＝硬直を投げで狩る counterplay は残る。`THROW_TECH_RANGE` 定数を `AiController` に追加。`armThrowTech()`（Task 36・既存）を呼ぶだけで `Fighter`/Core・`GameConstants`・JSON は不変。判断は相手の状態＋距離＋自分の接地/行動可否のみ＝**乱数なし（決定的・入力リプレイと両立）**。「簡易 AI」節に「投げ抜け反応（Task 51）」サブ節を追加。
- (Task 50) AI のダッシュ接近を追記。`AiController.control` の接近分岐を距離で 2 段化し、中心間 > `DASH_APPROACH_RANGE`(260px) なら歩きでなくダッシュ（Task 49 の二度押し前ステップ）で素早く間合いを詰める。`Fighter` のダッシュ検出（同方向押下エッジ×2 が受付窓内）に合わせ、AI が `dashTapStep`（0=押下 → 1=ニュートラル → 2=押下＝発動）の 3 フレームを生成。`isDashing()` 中は方向維持＋パターン 0 復帰。ダッシュ以外の分岐（ガード/投げ/歩き/攻撃）と `reset()` で `dashTapStep=0`（持ち越し防止）。`DASH_APPROACH_RANGE` 定数を `AiController` に追加。さらに Codex 指摘対応として、ダッシュ中は `Fighter` が `guarding=false` を強制するため接近中だけガード反応(Task 37)が機能しない問題を、ガード反応分岐でダッシュ中なら `self.cancelDash()` してからガードする形で解消（`Fighter` に `cancelDash()`＝`dashFrames=0` の小フックを追加。呼ばれない限り挙動不変）。判断は距離と観測状態のみ＝**乱数なし（決定的・入力リプレイと両立）**。Core の戦闘ロジック・`GameConstants`・JSON は不変（AI の接近分岐＋ `Fighter` のキャンセルフックのみ）。「簡易 AI」節に「ダッシュ接近（Task 50）」サブ節を追加。
- (Task 49) ダッシュ（二度押しステップ）を追記。`Fighter` に `prevMoveDir`/`dashTapDir`/`dashTapWindow`/`dashFrames`/`dashDir` を追加し、`update()` で `moveDir` の立ち上がりエッジ＋受付窓（`DASH_TAP_WINDOW`=12f）で二度押しを検出して `dashFrames`（`DASH_FRAMES`=12f）を立てる。歩行分岐の前に dash 分岐を置き `walkSpeed × DASH_SPEED_MULTIPLIER`（2.4）で確定移動（方向を離しても継続・攻撃/必殺技/投げ/ジャンプ/しゃがみ/被弾でキャンセル）。バックステップはガードと被るためダッシュ中は `guarding=false`。`isDashing()` を公開し `GameRenderer` は名前ラベルを `dash` に（歩行アニメ流用・新規 `AnimationState` なし）。入力系（`PlayerInput`/`CommandDetector`）は不変で Fighter 内に完結。`beginAttack`/`applyHit`/`applyThrow`/`applyThrowTech`/`reset()` で `dashFrames` クリア。`GameConstants` に `DASH_TAP_WINDOW`/`DASH_FRAMES`/`DASH_SPEED_MULTIPLIER` を追加。乱数なし＝決定的。全キャラ共通の定数（JSON 変更なし）。「ダッシュ（二度押しステップ）（Task 49）」節を追加。
