package com.phantomnexus.runtime.battle;

import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.AttackButton;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.Move;

/**
 * 実行時のファイター状態（Task 7: 移動 / Task 8: ジャンプ / Task 11: 攻撃 / Task 24: 複数技 / Task 25: しゃがみ / Task 27: ガード / Task 28: しゃがみ攻撃 / Task 29: しゃがみ移動 / Task 30: しゃがみガード / Task 31: 下段判定 / Task 32: 空中攻撃）。
 *
 * <p>静的定義 {@link Character} を参照し、位置（中心 X / 足元 Y）・垂直速度・接地状態・向き・HP・攻撃区間
 * といった実行時状態を保持する。入力の読み取りは行わず、左右移動量・ジャンプ・攻撃ボタン（弱/中/強 or null）・
 * しゃがみ押下は {@link #update(int, boolean, AttackButton, boolean)} に外部から渡す。
 * しゃがみ中は低姿勢を維持し通常速度の半分でクロール移動できる（ジャンプは受け付けない）。しゃがみ攻撃は低姿勢を維持したまま発動する。
 */
public class Fighter {

    private final Character def;
    private float x;          // 中心 X（ワールド座標）
    private float y;          // 足元 Y。接地時は GROUND_Y、ジャンプ中は上昇
    private float velocityY;  // 垂直速度（px/frame）。上向きが正
    private boolean grounded = true;
    private int airJumpsRemaining; // 残りの空中ジャンプ回数（二段ジャンプ・接地で回復・Task 68）
    private int airDashesRemaining; // 残りの空中ダッシュ回数（air dash・接地で回復・Task 69）
    private boolean facingRight;
    private int moveDir;      // 直近フレームに適用した左右移動方向（-1/0/+1）
    private int currentHp;    // 現在 HP
    private AttackPhase attackPhase = AttackPhase.NONE;
    private int attackFrame;
    private Move currentMove;  // 進行中の技（攻撃中のみ非 null）
    private int attackHits;             // 進行中の技がこれまでに命中した回数（多段ヒット・Task 74。0=未命中）
    private int attackHitGap;           // 次のサブヒットを許可するまでの待機フレーム（多段ヒットの間隔・Task 74）
    private int armorHitsUsed;          // 進行中の技で消費したアーマー数（スーパーアーマー・Task 80）
    private int hitstunFrames;
    private int knockdownFrames;        // ダウン（knockdown）の行動不能フレーム。ダウン中は被弾無敵（Task 60）
    private boolean knockdownInertThisFrame; // このフレームの update 処理前にダウン中だったか（被弾ゲートの 1F ラッチ・Task 60）
    private boolean ukemiRecovery;      // 受け身（クイック起き上がり）が成立中か（ダウン短縮・表示用・Task 66）
    private boolean hardKnockdown;      // 現在のダウンが受け身不能（hard knockdown）か（Task 88）
    private float velocityX;
    private boolean crouching;
    private boolean crouchAttacking; // しゃがみ中に開始した攻撃（Task 28）
    private boolean aerialAttacking;  // 空中で開始した攻撃（Task 32）
    private boolean throwing;          // 投げ（ガード不能の掴み）を発動中か（Task 35）
    private boolean dashAttacking;     // ダッシュ中に開始した突進攻撃を発動中か（Task 65）
    private boolean exAttack;           // 進行中の技が EX 版か（メーター消費・打撃のダメージ強化・Task 54）
    private int throwTechWindow;        // 投げ抜け猶予窓（投げボタン押下でアーム・毎フレーム減衰）（Task 36）
    private int throwTechFrames;        // 投げ抜け成立後の硬直/表示フレーム（ノーダメージ・hitstun と併走）（Task 36）
    private int comboCount;             // 現在受けている連続ヒット数（hitstun 継続中の被弾で加算・回復で 0）（Task 39）
    private int counterHitFrames;       // カウンターヒットを受けた直後の表示フレーム（ラベルに (CH) を付す・Task 71）
    private boolean wallBounceArmed;    // 壁バウンド技を食らい、まだ壁に当たって跳ね返っていない状態（Task 101）
    private int wallBounceFrames;       // 壁バウンド成立（跳ね返り）直後の表示フレーム（ラベル wall_bounce・Task 101）
    private int stunMeter;              // 蓄積中のスタン値（被弾で増え非被弾で減衰・しきい値超えでめまい・Task 79）
    private int dizzyFrames;            // めまい（dizzy）の無防備行動不能フレーム（被弾無敵ではない＝フルコンボ確定・Task 79）
    private boolean guarding;  // 後退方向保持でガード中か（接地/滞空＝空中ガード・Task 27/59）
    private int guardHeldFrames; // ガードを連続保持しているフレーム数（ジャストガード判定用・Task 81）
    private int justGuardFrames;  // ジャストガード成立直後の表示フレーム（ラベルに [JUST] を付す・Task 81）
    private float guardGauge = GameConstants.GUARD_GAUGE_MAX; // ガードゲージ（ガードで減り非ガードで回復・Task 43）
    private int guardBreakFrames; // ガードクラッシュの行動不能/表示フレーム（hitstun を流用・Task 43）
    private float superMeter; // 必殺技ゲージ（攻撃の当て / 被弾 / ガードで貯まり EX 必殺技で消費・Task 44）
    private int prevMoveDir;   // 前フレームの移動入力方向（ダッシュの二度押しエッジ検出用・Task 49）
    private int dashTapDir;    // 直近に押した方向（二度押し判定用・Task 49）
    private int dashTapWindow; // 二度押し受付の残りフレーム（毎フレーム減衰・Task 49）
    private boolean dashTapGrounded; // 1 度目のタップをアームした時の接地状態（空中ダッシュは空中アーム窓のみ消費・Task 69）
    private int dashFrames;    // ダッシュ継続の残りフレーム（>0 でダッシュ中・Task 49）
    private int dashDir;       // ダッシュ方向（-1=左 / +1=右・Task 49）

    public Fighter(Character def, float spawnX, boolean facingRight) {
        this.def = def;
        this.x = spawnX;
        this.y = GameConstants.GROUND_Y;
        this.facingRight = facingRight;
        this.currentHp = def.getHp();
        this.airJumpsRemaining = def.getAirJumps(); // 初期接地状態で満タン（Task 68）
        this.airDashesRemaining = def.getAirDashes(); // 初期接地状態で満タン（Task 69）
    }

    /**
     * 1 フレームの攻撃・移動・ジャンプ・しゃがみを適用する。
     *
     * @param moveDir      左右移動方向（-1 = 左 / 0 = 静止 / +1 = 右）
     * @param jumpPressed  このフレームでジャンプ入力の立ち上がりがあったか
     * @param attackButton 押されたボタン種別（{@link AttackButton}）。null なら通常攻撃なし
     * @param crouchHeld   DOWN ボタンを押し続けているか（接地中のみしゃがみ遷移）
     * @param throwReq     投げ（ガード不能の掴み）の発動要求があるか（Task 35。地上・立ちでのみ成立）
     */
    public void update(int moveDir, boolean jumpPressed, AttackButton attackButton, boolean crouchHeld,
                       boolean throwReq) {
        // 投げ抜け猶予窓・表示フレームを毎フレーム減衰させる（Task 36）。窓は投げボタン押下でアームされる。
        if (throwTechWindow > 0) {
            throwTechWindow--;
        }
        if (throwTechFrames > 0) {
            throwTechFrames--;
        }
        // ガードクラッシュの表示/拘束フレームを減衰（Task 43。拘束自体は hitstunFrames が担う）。
        if (guardBreakFrames > 0) {
            guardBreakFrames--;
        }
        // カウンターヒット被弾の表示フレームを減衰（Task 71。ラベルの (CH) 表示専用・拘束は hitstunFrames が担う）。
        if (counterHitFrames > 0) {
            counterHitFrames--;
        }
        // 壁バウンド成立（跳ね返り）の表示フレームを減衰（Task 101・ラベル表示専用・拘束は hitstunFrames が担う）。
        if (wallBounceFrames > 0) {
            wallBounceFrames--;
        }
        // ジャストガード成立の表示フレームを減衰（Task 81。ラベルの [JUST] 表示専用）。
        if (justGuardFrames > 0) {
            justGuardFrames--;
        }
        // めまい（Task 79）の行動不能フレームを減衰。dizzyFrames が拘束の真の長さで、被弾で短い hitstun に上書き
        // されても独立に減るため「めまい中はずっと無防備」が保たれる（コンボでリセットされない）。
        if (dizzyFrames > 0) {
            dizzyFrames--;
        }
        // スタン値の自然減衰：完全に中立（のけぞり / ダウン / めまいでない）な間だけ抜けていく（Task 79）。
        if (hitstunFrames <= 0 && knockdownFrames <= 0 && dizzyFrames <= 0 && stunMeter > 0) {
            stunMeter = Math.max(0, stunMeter - GameConstants.STUN_DECAY_PER_FRAME);
        }
        // ダウンの被弾無敵ラッチ（Task 60・Codex 指摘）：この update の処理前にダウン中だったか（＝このフレームは inert か）を
        // 記録する。減算は下の inert 分岐内で行い 60F の行動不能を確保しつつ、当たり判定の被弾ゲート（isKnockedDown）は
        // このラッチ ‖ knockdownFrames>0 で判定する。これにより、ダウンが解ける最終フレーム（inert 分岐で knockdownFrames が
        // 0 になるフレーム）も resolveHit/描画では down 扱い＝被弾無敵になり、「行動可能フレーム」と「被弾可能フレーム」が揃う
        // （まだ動けないのに被弾だけ可能、という 1F の無防備窓を作らない）。応用フレーム（applyKnockdown 直後）は
        // knockdownFrames>0 側で無敵になる。
        knockdownInertThisFrame = knockdownFrames > 0;
        // ガード判定：非のけぞり・非攻撃中に後退方向を保持しているか。接地でも滞空でも成立する（空中ガード・Task 59）。
        // 後退方向保持は立ち（crouchHeld=false）でも しゃがみ（crouchHeld=true）でも成立し、
        // しゃがみ後退は低姿勢ガード（crouch guard）になる（Task 30。しゃがみは接地時のみ）。低姿勢判定は crouching を併用。
        // 滞空中の後退保持は空中ガード（air guard）＝立ち扱い（crouching=false）で、飛び道具・中段/上段を chip で凌ぐ（Task 59）。
        int backDir = facingRight ? -1 : 1;
        guarding = hitstunFrames <= 0 && knockdownFrames <= 0 && dizzyFrames <= 0
                   && attackPhase == AttackPhase.NONE
                   && moveDir != 0 && moveDir == backDir;
        // ジャストガード判定用：ガード連続保持フレームを数える（Task 81）。保持し始めて JUST_GUARD_WINDOW 以内の
        // ガード成立はジャストガード（chip / ゲージ削りなし＋メーター獲得）になる。非ガードで 0 リセット。
        guardHeldFrames = guarding ? guardHeldFrames + 1 : 0;
        // ガードゲージは非ガード・非クラッシュ中に徐々に回復する（Task 43。ガード中は減る一方）。
        if (!guarding && guardBreakFrames <= 0 && guardGauge < GameConstants.GUARD_GAUGE_MAX) {
            guardGauge = Math.min(GameConstants.GUARD_GAUGE_MAX,
                                  guardGauge + GameConstants.GUARD_REGEN_PER_FRAME);
        }
        if (knockdownFrames > 0) {
            // ダウン（Task 60）：のけぞりと同じく行動不能だが、より長く・ダウン中は被弾無敵（起き攻め無し）。
            // hitstun より優先（ダウン技は通常のけぞりを上書きする）。knockback の滑りは hitstun と同じ式で減衰。
            // 減算はこの inert 分岐内で行い、KNOCKDOWN_FRAMES 分（60F）ちょうど行動不能にする。被弾ゲートとの整合は
            // 上で記録した knockdownInertThisFrame ラッチが担う（最終フレームも resolveHit では down 扱い＝無敵）。
            crouching = false;
            guarding = false;
            this.moveDir = 0;
            // 受け身（ukemi・クイック起き上がり・Task 66）：ダウン開始から UKEMI_WINDOW 以内に行動入力（攻撃/ジャンプ/投げ）が
            // あれば残りダウンを UKEMI_RISE_FRAMES へ短縮して早く起き上がる（起き攻めへの対抗択）。経過フレームは減算前の
            // knockdownFrames から算出。短縮済み（残り ≤ UKEMI_RISE_FRAMES）や窓を過ぎたら無効＝フルダウンを待つ。乱数なし。
            int elapsed = GameConstants.KNOCKDOWN_FRAMES - knockdownFrames;
            boolean ukemiInput = attackButton != null || jumpPressed || throwReq;
            // 受け身不能ダウン（Task 88）はクイック起き上がりを許さない（必ずフルダウン＝起き攻め確定）。
            if (!hardKnockdown && ukemiInput && elapsed <= GameConstants.UKEMI_WINDOW
                    && knockdownFrames > GameConstants.UKEMI_RISE_FRAMES) {
                knockdownFrames = GameConstants.UKEMI_RISE_FRAMES;
                ukemiRecovery = true; // 表示用（クイック起き上がりであることを識別）
            }
            knockdownFrames--;
            // 起き上がった瞬間にコンボを終了（ダウンはコンボの締め＝次の被弾は新規コンボ）（Task 39/60）。
            if (knockdownFrames == 0) {
                comboCount = 0;
            }
            x += velocityX;
            clampToStage();
            velocityX *= GameConstants.KNOCKBACK_FRICTION;
            if (Math.abs(velocityX) < 0.1f) {
                velocityX = 0f;
            }
        } else if (hitstunFrames > 0 || dizzyFrames > 0) {
            // のけぞり（Task 13）＋めまい（Task 79）：いずれも無防備で行動不能。めまいは hitstun より長く、被弾で
            // 短い hitstun に上書きされても dizzyFrames が独立に拘束を保つ（上で減衰済み）。被弾無敵ではない（ダウンと違う）。
            crouching = false;
            this.moveDir = 0;
            if (hitstunFrames > 0) {
                hitstunFrames--;
                // hitstun から復帰した瞬間にコンボを終了（次の被弾は新規コンボ＝1 から数え直す）（Task 39）。
                if (hitstunFrames == 0) {
                    comboCount = 0;
                }
            }
            x += velocityX;
            clampToStage();
            // 壁バウンド（Task 101）：壁バウンド技を食らって横へ飛ばされ、画面端（壁）に達したら一度だけ跳ね返る。
            // 反対方向へ WALL_BOUNCE_REBOUND_SCALE 倍の速度で戻し、再び浮かせて（POP）のけぞりを延長＝跳ね返り際を追撃可能。
            if (wallBounceArmed && atStageEdge() && pushingIntoEdge()) {
                velocityX = -velocityX * GameConstants.WALL_BOUNCE_REBOUND_SCALE;
                velocityY = GameConstants.WALL_BOUNCE_POP;
                grounded = false;
                hitstunFrames += GameConstants.WALL_BOUNCE_BONUS_HITSTUN;
                wallBounceArmed = false;
                wallBounceFrames = GameConstants.WALL_BOUNCE_LABEL_FRAMES;
            }
            velocityX *= GameConstants.KNOCKBACK_FRICTION;
            if (Math.abs(velocityX) < 0.1f) {
                velocityX = 0f;
            }
        } else {
            // ガード knockback：hitstun 無しでも velocityX（applyGuard 由来）を位置へ反映する。
            if (velocityX != 0) {
                x += velocityX;
                clampToStage();
                velocityX *= GameConstants.KNOCKBACK_FRICTION;
                if (Math.abs(velocityX) < 0.1f) {
                    velocityX = 0f;
                }
            }
            // ダッシュ（二度押しステップ・Task 49）：方向入力の立ち上がりエッジを検出し、直近の同方向タップが
            // 受付窓内なら短いダッシュを開始する（接地・非攻撃・非しゃがみのみ）。窓は毎フレーム減衰。
            if (dashTapWindow > 0) {
                dashTapWindow--;
            }
            boolean dirEdge = moveDir != 0 && moveDir != prevMoveDir;
            if (dirEdge) {
                boolean canGroundDash = grounded && attackPhase == AttackPhase.NONE && !crouchHeld && dashFrames <= 0;
                // 空中ダッシュ（Task 69）：滞空中の二度押しで水平バースト。データ駆動（airDashes>0 のキャラのみ）。
                // !dashTapGrounded：1 度目のタップも空中でアームされた窓のみ消費する（地上アーム窓の流用を防ぐ＝
                // 「地上で 1 度押し→ジャンプ→空中で 1 度押し」で発動しない。仕様は滞空中の二度押し・Codex 指摘）。
                // attackButton==null && !throwReq：同フレームで空中攻撃/投げが始まる入力では成立させない。
                //   （後段の beginAttack が dashFrames を 0 に戻すため水平バーストは出ず、airDashesRemaining だけ
                //    無駄に消費されるのを防ぐ。攻撃が優先＝この frame は air dash を成立させず窓を再アームする・Codex 指摘）。
                boolean canAirDash = !grounded && attackPhase == AttackPhase.NONE && airDashesRemaining > 0
                        && dashFrames <= 0 && !dashTapGrounded && attackButton == null && !throwReq;
                if ((canGroundDash || canAirDash) && moveDir == dashTapDir && dashTapWindow > 0) {
                    dashFrames = GameConstants.DASH_FRAMES; // 二度押し成立 → ダッシュ開始（接地＝地上ステップ / 滞空＝空中ダッシュ）
                    dashDir = moveDir;
                    dashTapWindow = 0;
                    velocityX = 0f; // 残留 knockback を打ち消し、ダッシュ移動との二重加算を防ぐ
                    if (canAirDash) {
                        airDashesRemaining--; // 空中ダッシュ回数を消費（接地で回復・Task 69）
                    }
                } else {
                    dashTapDir = moveDir;                    // 1 度目の押下 → 受付窓をアーム
                    dashTapWindow = GameConstants.DASH_TAP_WINDOW;
                    dashTapGrounded = grounded;              // アーム時の接地状態を記録（空中ダッシュ判定用・Task 69）
                }
            }
            prevMoveDir = moveDir;
            // ダッシュ中（特にバックステップ）は後退方向保持と被るためガードを抑止する。
            if (dashFrames > 0) {
                guarding = false;
            }
            // 攻撃の発動：接地時はしゃがみ遷移フレーム（crouchHeld かつ未しゃがみ）を除いて可。
            // 空中では空中攻撃（Task 32）として発動可（しゃがみ条件は無視）。
            if (attackPhase == AttackPhase.NONE && (attackButton != null || throwReq)
                    && (grounded ? (!crouchHeld || crouching) : true)) {
                // 投げ（Task 35）は throwReq で起動するガード不能掴み。地上では地上投げ（地上の相手専用）、
                // 滞空中では空中投げ（Task 70・空中の相手専用）を選ぶ。throwing フラグは両者共通（接地状態で種別を区別）。
                // 通常技 / 空中攻撃 / しゃがみ攻撃のいずれにも分類しない。
                // ダッシュ攻撃（Task 65）：接地ダッシュ中の攻撃で、キャラが dashAttack を持つなら通常技でなく突進技を出す。
                boolean dashAtk = !throwReq && grounded && !crouchHeld
                        && dashFrames > 0 && def.getDashAttack() != null;
                Move move = throwReq ? (grounded ? def.getThrowMove() : def.getAirThrowMove())
                        : dashAtk ? def.getDashAttack()
                        : selectNormalMove(attackButton);
                if (move != null) {
                    throwing = throwReq;
                    dashAttacking = dashAtk;                              // ダッシュ突進攻撃フラグ（Task 65）
                    crouchAttacking = !throwReq && !dashAtk && grounded && crouching; // しゃがみ中に発動 → 下段攻撃フラグ（投げ / ダッシュ / 空中は不可）
                    aerialAttacking = !throwReq && !grounded;             // 空中で発動 → 空中攻撃フラグ（Task 32）
                    int lungeDir = dashDir;                               // beginAttack が dashFrames を 0 にする前に方向を退避
                    beginAttack(move);
                    if (dashAtk) {
                        // ダッシュの勢いを引き継ぐ突進：velocityX に前方初速を与える（既存の velocityX 適用＋減衰経路を流用）。
                        velocityX = lungeDir * GameConstants.DASH_ATTACK_LUNGE_SPEED;
                    }
                }
            } else if (attackButton != null && !throwReq && canChainInto(attackButton)) {
                // チェーンキャンセル（Task 45）：命中した通常技を上位ボタンの通常技へ即キャンセルし、
                // 硬直を待たずに繋いで連続ヒットにする。新技は接地の立ち通常技として開始する。
                Move move = selectNormalMove(attackButton);
                if (move != null) {
                    crouchAttacking = false;
                    aerialAttacking = false;
                    throwing = false;
                    dashAttacking = false;
                    beginAttack(move); // attackConnected/phase をリセット → 新技が改めて命中判定される
                }
            }

            if (attackPhase != AttackPhase.NONE) {
                if (!crouchAttacking) {
                    crouching = false; // 立ち攻撃中はしゃがみ解除
                }
                this.moveDir = 0;
                advanceAttack();
                if (attackPhase == AttackPhase.NONE) {
                    crouchAttacking = false; // 攻撃終了でフラグクリア
                    aerialAttacking = false; // 空中攻撃も終了でクリア
                    throwing = false;        // 投げも終了でクリア（Task 35）
                    dashAttacking = false;   // ダッシュ攻撃も終了でクリア（Task 65）
                    if (!crouchHeld) {
                        crouching = false; // DOWN を離していれば攻撃終了と同フレームに姿勢解除
                    }
                }
            } else if (crouchHeld && grounded) {
                crouching = true;
                dashFrames = 0;                             // しゃがみでダッシュをキャンセル（凍結回避・Task 49）
                this.moveDir = moveDir;                     // 低速クロール：方向を記録
                x += moveDir * def.getWalkSpeed() * 0.5f;  // 通常の半速で移動
                clampToStage();
                // ジャンプ入力は無視
            } else if (dashFrames > 0) {
                // ダッシュ中（Task 49）：通常歩行より速く確定移動する（方向を離しても継続）。ジャンプでキャンセル可。
                crouching = false;
                dashFrames--;
                this.moveDir = dashDir;
                x += dashDir * def.getWalkSpeed() * GameConstants.DASH_SPEED_MULTIPLIER;
                clampToStage();
                if (jumpPressed && grounded) {
                    velocityY = def.getJumpPower();
                    grounded = false;
                    dashFrames = 0; // ジャンプでダッシュをキャンセル（飛び込みへ）
                    // 空中ガード可（Task 59）：後退保持なら滞空後も guarding を維持（前ジャンプは moveDir != backDir で自然に false）。
                }
            } else {
                crouching = false;
                this.moveDir = moveDir;
                x += moveDir * def.getWalkSpeed();
                clampToStage();
                if (jumpPressed && grounded) {
                    velocityY = def.getJumpPower();
                    grounded = false;
                    // 空中ガード可（Task 59）：後退保持なら滞空後も guarding を維持（前ジャンプは moveDir != backDir で自然に false）。
                } else if (jumpPressed && !grounded && airJumpsRemaining > 0) {
                    // 二段ジャンプ（Task 68）：空中でジャンプ入力の立ち上がりがあれば、残り回数を消費して再度跳ぶ。
                    // velocityY を上向き初速へ上書き（下降中でも再上昇）。回数は接地で回復する。データ駆動（airJumps>0 のキャラのみ）。
                    velocityY = def.getJumpPower();
                    airJumpsRemaining--;
                }
            }
        }

        boolean wasGrounded = grounded;
        velocityY -= GameConstants.GRAVITY;
        y += velocityY;

        if (y <= GameConstants.GROUND_Y) {
            y = GameConstants.GROUND_Y;
            velocityY = 0f;
            grounded = true;
            airJumpsRemaining = def.getAirJumps();   // 接地で空中ジャンプ回数を回復（Task 68）
            airDashesRemaining = def.getAirDashes();  // 接地で空中ダッシュ回数を回復（Task 69）
            if (!wasGrounded && dashFrames > 0) {
                dashFrames = 0; // 着地で空中ダッシュを終了（地上ダッシュへ持ち越さない・Task 69）
            }
        }
    }

    /** ラウンド間リセット（HP・位置・向き・全状態をスポーン時の値に戻す）。 */
    public void reset(float spawnX, boolean spawnFacingRight) {
        x = spawnX;
        y = GameConstants.GROUND_Y;
        velocityY = 0f;
        velocityX = 0f;
        grounded = true;
        airJumpsRemaining = def.getAirJumps(); // 空中ジャンプ回数をスポーン時に満タンへ（Task 68）
        airDashesRemaining = def.getAirDashes(); // 空中ダッシュ回数をスポーン時に満タンへ（Task 69）
        facingRight = spawnFacingRight;
        moveDir = 0;
        currentHp = def.getHp();
        attackPhase = AttackPhase.NONE;
        attackFrame = 0;
        currentMove = null;
        attackHits = 0;
        attackHitGap = 0;
        armorHitsUsed = 0;
        hitstunFrames = 0;
        knockdownFrames = 0;
        knockdownInertThisFrame = false;
        ukemiRecovery = false;
        hardKnockdown = false;
        crouching = false;
        crouchAttacking = false;
        aerialAttacking = false;
        throwing = false;
        dashAttacking = false;
        throwTechWindow = 0;
        throwTechFrames = 0;
        comboCount = 0;
        counterHitFrames = 0;
        wallBounceArmed = false;
        wallBounceFrames = 0;
        stunMeter = 0;
        dizzyFrames = 0;
        guarding = false;
        guardHeldFrames = 0;
        justGuardFrames = 0;
        guardGauge = GameConstants.GUARD_GAUGE_MAX;
        guardBreakFrames = 0;
        superMeter = 0f;
        prevMoveDir = 0;
        dashTapDir = 0;
        dashTapWindow = 0;
        dashTapGrounded = false;
        dashFrames = 0;
        dashDir = 0;
    }

    /**
     * ガード中の被弾を適用する（chip ダメージのみ・のけぞりなし・軽微な knockback）（Task 27）。
     * chip ダメージは通常ダメージの 10%（最低 1）。攻撃の勢いで微小後退する。
     */
    public void applyGuard(int attackDamage, int knockbackDir) {
        // ジャストガード（Task 81）：ガードを保持し始めて JUST_GUARD_WINDOW 以内に受けたら chip / ゲージ削りなし＋
        // メーター獲得＋最小 knockback。押しっぱなしのターンでは guardHeldFrames が大きく成立しない（反応ガードのみ）。
        if (guardHeldFrames <= GameConstants.JUST_GUARD_WINDOW) {
            gainMeter(GameConstants.JUST_GUARD_METER);
            velocityX = knockbackDir * GameConstants.KNOCKBACK_SPEED * 0.1f;
            justGuardFrames = GameConstants.JUST_GUARD_LABEL_FRAMES;
            return; // ダメージ・ゲージ削りなし（完全防御）
        }
        int chip = Math.max(1, attackDamage / 10);
        // 削り KO 禁止（Task 82）：既定では chip で HP を 1 未満にしない（最低 1 残す）。非ガードヒットは 0 まで削れる。
        if (!GameConstants.CHIP_DAMAGE_CAN_KO && currentHp - chip <= 0) {
            currentHp = 1;
        } else {
            applyDamage(chip);
        }
        velocityX = knockbackDir * GameConstants.KNOCKBACK_SPEED * 0.3f;
        // ガードゲージを攻撃力に応じて削る。0 以下でガードクラッシュ（Task 43）。
        guardGauge -= Math.max(1, attackDamage / GameConstants.GUARD_DRAIN_DIVISOR);
        if (guardGauge <= 0f) {
            // 崩し成立：ゲージを満タンに戻し、行動不能＋ガード不能（hitstun を流用）にする。
            // この「崩した一撃」自体は chip のみ（防御は成立）で、続く攻撃がフル確定になる。
            guardGauge = GameConstants.GUARD_GAUGE_MAX;
            guardBreakFrames = GameConstants.GUARD_BREAK_FRAMES;
            hitstunFrames = GameConstants.GUARD_BREAK_FRAMES;
            guarding = false;
            velocityX = 0f; // クラッシュはその場硬直（軽 knockback を打ち消す）
        }
    }

    /**
     * 被弾を適用する（HP 減算・のけぞり遷移・knockback）。攻撃中だった場合は中断する。
     */
    public void applyHit(int damage, int hitstun, int knockbackDir) {
        // 連続ヒット計数（Task 39）：既に hitstun 中の被弾はコンボ継続（+1）、neutral からの被弾は新規コンボ（=1）。
        comboCount = hitstunFrames > 0 ? comboCount + 1 : 1;
        // コンボダメージ補正（Task 46）：2 ヒット目以降は与ダメージを段階的に減衰させる（1 ヒット目は等倍）。
        applyDamage(scaledComboDamage(damage));
        hitstunFrames = hitstun;
        velocityX = knockbackDir * GameConstants.KNOCKBACK_SPEED;
        attackPhase = AttackPhase.NONE;
        attackFrame = 0;
        currentMove = null;
        crouching = false;
        crouchAttacking = false;
        aerialAttacking = false;
        throwing = false;
        dashAttacking = false;
        throwTechWindow = 0;
        throwTechFrames = 0; // 投げ抜け硬直中に被弾したらラベルをのけぞりへ戻す（表示 desync 防止・Task 36）
        guardBreakFrames = 0; // ガードクラッシュ硬直中にフル被弾したらラベルをのけぞりへ戻す（Task 43）
        guarding = false;    // 被弾で neutral から抜けるので guarding を即解除（同フレームの飛び道具/描画が誤ってガード扱いしない）
        dashFrames = 0;      // 被弾でダッシュをキャンセル（Task 49）
        wallBounceArmed = false; // 新たな被弾で保留中の壁バウンドをキャンセル（Task 101。applyWallBounce はこの後に再アーム）
    }

    /**
     * 浮かせ（launch, Task 83）を適用する。通常被弾（{@link #applyHit}）に加えて相手を上方初速 {@code launchVelocity} で
     * 打ち上げ、空中やられ（のけぞり中＝無防備）にする＝空中コンボ（ジャグル）の起点。打ち上がった相手は重力で落下し、
     * のけぞりが切れるか着地するまで追撃可能。{@link #applyHit} のダメージ / コンボ計数 / 水平 knockback を流用する。
     */
    public void applyLaunch(int damage, int hitstun, int knockbackDir, float launchVelocity) {
        applyHit(damage, hitstun, knockbackDir);
        velocityY = launchVelocity; // 上方初速で打ち上げ（重力は update 末尾で毎フレーム適用＝弧を描いて落下）
        grounded = false;
    }

    /**
     * 壁バウンド（wall bounce・Task 101）を適用する。{@link #applyHit} に加えて相手を強い水平初速で横へ吹き飛ばし、
     * 画面端（壁）に達したら {@code update} 内で一度だけ跳ね返って再び浮く（画面端ジャグルの延長）。
     * {@code applyHit} がダメージ / コンボ計数 / 各種クリアを行うため、{@code wallBounceArmed} はその後に立てる。
     */
    public void applyWallBounce(int damage, int hitstun, int knockbackDir) {
        applyHit(damage, hitstun, knockbackDir);
        velocityX = knockbackDir * GameConstants.WALL_BOUNCE_SPEED; // 強い水平吹き飛ばし（壁へ向かう）
        wallBounceArmed = true; // applyHit の後に立てる（applyHit がクリアするため）
    }

    /**
     * ダウン（knockdown, Task 60）を適用する。{@code Move.knockdown=true} の技を非ガードで食らったときに
     * {@link #applyHit} の代わりに呼ぶ。通常のけぞりより長い {@link GameConstants#KNOCKDOWN_FRAMES} の行動不能と
     * 強い knockback（{@link GameConstants#KNOCKDOWN_KNOCKBACK_SCALE} 倍）を与え、ダウン中は被弾無敵になる
     * （{@link #isKnockedDown()} を当たり判定が参照＝起き攻め / OTG なし）。コンボ補正・計数は {@link #applyHit} と同じ。
     */
    public void applyKnockdown(int damage, int knockbackDir) {
        applyKnockdown(damage, knockbackDir, false);
    }

    /**
     * ダウンを適用する（受け身可否つき・Task 88）。{@code hard} が true なら受け身不能ダウン（クイック起き上がり不可＝
     * 起き攻め確定）。それ以外は {@link #applyKnockdown(int, int)} と同じ（受け身可能）。
     */
    public void applyKnockdown(int damage, int knockbackDir, boolean hard) {
        comboCount = hitstunFrames > 0 ? comboCount + 1 : 1;
        applyDamage(scaledComboDamage(damage));
        hitstunFrames = 0;                 // のけぞりではなくダウンへ（ラベル / 優先順が knockdown を表示）
        knockdownFrames = GameConstants.KNOCKDOWN_FRAMES;
        hardKnockdown = hard;              // 受け身可否（Task 88）
        ukemiRecovery = false;             // 新しいダウン＝受け身を再度受け付ける（Task 66。hard なら受け付けても短縮しない）
        velocityX = knockbackDir * GameConstants.KNOCKBACK_SPEED * GameConstants.KNOCKDOWN_KNOCKBACK_SCALE;
        attackPhase = AttackPhase.NONE;
        attackFrame = 0;
        currentMove = null;
        crouching = false;
        crouchAttacking = false;
        aerialAttacking = false;
        throwing = false;
        dashAttacking = false;
        throwTechWindow = 0;
        throwTechFrames = 0;
        guardBreakFrames = 0;
        guarding = false;
        dashFrames = 0;
        wallBounceArmed = false; // ダウンで保留中の壁バウンドをキャンセル（Task 101）
        // ダッシュ二度押しの受付状態もクリア（Task 60・CodeRabbit 指摘）。ダウン中は dash 検出ブロック（else 側）が
        // 走らず dashTapWindow が減衰しないため、被弾前にアームされた 1 回目のタップが 60F 温存され、起き上がり後の
        // 最初の方向入力で暴発ダッシュになる。窓・方向・前フレーム方向をニュートラルへ戻して保留タップを破棄する。
        dashTapWindow = 0;
        dashTapDir = 0;
        dashTapGrounded = false;
        prevMoveDir = 0;
    }

    /**
     * 投げ（ガード不能の近接掴み）の被弾を適用する（Task 35）。通常被弾（{@link #applyHit}）より長い hitstun
     * （{@link GameConstants#THROW_HITSTUN_FRAMES}）と強い knockback（{@link GameConstants#THROW_KNOCKBACK_SCALE} 倍）を与える。
     * ガードは無視される（成立判定は呼び出し側で済ませ、本メソッドは常にフルダメージを適用する）。進行中の攻撃は中断する。
     */
    public void applyThrow(int damage, int knockbackDir) {
        // 連続ヒット計数（Task 39）：hitstun 中の相手を掴んだら（空中コンボ等）コンボ継続、neutral からは新規。
        comboCount = hitstunFrames > 0 ? comboCount + 1 : 1;
        // コンボダメージ補正（Task 46）：コンボ中の投げも 2 段目以降は減衰する（1 段目は等倍）。
        applyDamage(scaledComboDamage(damage));
        hitstunFrames = GameConstants.THROW_HITSTUN_FRAMES;
        velocityX = knockbackDir * GameConstants.KNOCKBACK_SPEED * GameConstants.THROW_KNOCKBACK_SCALE;
        attackPhase = AttackPhase.NONE;
        attackFrame = 0;
        currentMove = null;
        crouching = false;
        crouchAttacking = false;
        aerialAttacking = false;
        throwing = false;
        dashAttacking = false;
        throwTechWindow = 0;
        throwTechFrames = 0; // 投げ抜け硬直中に投げで上書きされたらラベルをのけぞりへ戻す（Task 36）
        guardBreakFrames = 0; // ガードクラッシュ硬直中に投げで上書きされたらラベルを更新（Task 43）
        guarding = false;    // 投げ（ガード不能）で neutral から抜けるので guarding を即解除（同フレームの飛び道具/描画が誤ってガード扱いしない）
        dashFrames = 0;      // 投げ被弾でダッシュをキャンセル（Task 49）
    }

    /**
     * 投げ抜けの猶予窓をアームする（Task 36）。投げボタンを押した（接地）フレームに Core から呼ぶ。
     * この窓が残っている間に相手の投げを掴まれると投げ抜け（{@link #applyThrowTech}）が成立する。
     */
    public void armThrowTech() {
        throwTechWindow = GameConstants.THROW_TECH_WINDOW;
    }

    /** 投げ抜け可能な状態か（直近に投げボタンを押して猶予窓が残っているか）（Task 36）。 */
    public boolean canTechThrow() {
        return throwTechWindow > 0;
    }

    /**
     * 投げ抜け（throw tech, Task 36）を適用する。ノーダメージで両者が反対方向へ弾かれ、短い硬直に入る。
     * 進行中の投げ / 攻撃は中断する。ダメージ・のけぞりは無し（hitstun と同じ移動・行動拘束を {@link #throwTechFrames} で再利用）。
     */
    public void applyThrowTech(int pushDir) {
        comboCount = 0; // 投げ抜けは仕切り直し（コンボではない）（Task 39）
        velocityX = pushDir * GameConstants.THROW_TECH_PUSHBACK;
        hitstunFrames = GameConstants.THROW_TECH_FRAMES; // 行動拘束・knockback 減衰の既存ロジックを流用（ダメージは無し）
        throwTechFrames = GameConstants.THROW_TECH_FRAMES; // 表示用（label を "tech" にする）
        throwTechWindow = 0;
        attackPhase = AttackPhase.NONE;
        attackFrame = 0;
        currentMove = null;
        crouching = false;
        crouchAttacking = false;
        aerialAttacking = false;
        throwing = false;
        dashAttacking = false;
        guarding = false; // 投げ抜けで neutral から抜けるので guarding を即解除（同フレームの飛び道具/描画が誤ってガード扱いしない）
        dashFrames = 0;   // 投げ抜けでダッシュをキャンセル（Task 49）
    }

    /** 投げ抜けの硬直中か（表示ラベルを "tech" にするための判定）（Task 36）。 */
    public boolean isThrowTeched() {
        return throwTechFrames > 0;
    }

    /**
     * カウンターヒット（Task 71）を受けたことを記録する（表示用）。{@code resolveHit} が、相手の攻撃 startup 中に
     * 打撃を当てた（＝差し返した）ときに {@link #applyHit} / {@link #applyKnockdown} の直後に呼ぶ。被弾ラベルに
     * {@code (CH)} を付すための表示専用カウンタを立てるだけで、ダメージ / 拘束は呼び出し側が既に適用している。
     */
    public void markCounterHit() {
        counterHitFrames = GameConstants.COUNTER_HIT_LABEL_FRAMES;
    }

    /** 直近にカウンターヒットを受けたか（表示ラベルに {@code (CH)} を付すための判定・Task 71）。 */
    public boolean isCounterHit() {
        return counterHitFrames > 0;
    }

    /** 直近に壁バウンド（跳ね返り）が成立したか（表示ラベルを "wall_bounce" にするための判定・Task 101）。 */
    public boolean isWallBounced() {
        return wallBounceFrames > 0;
    }

    /**
     * スタン値を加算する（Task 79）。通常ヒットの被弾時に与ダメージ量を渡す。{@code stunThreshold} が 0（既定）の
     * キャラはめまい無効＝何もしない（後方互換）。しきい値以上になると<b>めまい</b>（{@link GameConstants#DIZZY_FRAMES}
     * の無防備行動不能）に陥り、スタン値は 0 にリセットされる。既にめまい中は加算しない（多重発生を防ぐ）。
     * 乱数なし（被弾ダメージ量のみで決まる＝決定的）。
     */
    public void addStun(int amount) {
        int threshold = def.getStunThreshold();
        if (threshold <= 0 || amount <= 0 || dizzyFrames > 0) {
            return;
        }
        stunMeter += amount;
        if (stunMeter >= threshold) {
            stunMeter = 0;
            dizzyFrames = GameConstants.DIZZY_FRAMES; // 長い無防備硬直（hitstun と独立＝コンボでリセットされない）
        }
    }

    /** めまい（dizzy）中か（Task 79）。無防備な長硬直（被弾無敵ではない＝フルコンボ確定の隙）。表示ラベルにも使う。 */
    public boolean isDizzy() {
        return dizzyFrames > 0;
    }

    /**
     * スーパーアーマー（Task 80）が有効か。進行中の技が startup 区間にあり、{@code armorHits} の残りがあれば、
     * 被弾してものけぞらずに技を継続できる（ダメージは受ける）。投げ（ガード不能）はアーマーを貫通する。
     */
    public boolean isArmorActive() {
        return attackPhase == AttackPhase.STARTUP && currentMove != null
                && armorHitsUsed < currentMove.getArmorHits();
    }

    /**
     * アーマーで 1 発受け止める（Task 80）。ダメージは適用するが hitstun / 中断はしない（技は継続）。軽い knockback を
     * 与える。アーマー残数を 1 消費する。コンボ計数・補正は通常ヒットと同じ（{@link #scaledComboDamage}）。
     */
    public void absorbArmorHit(int damage, int knockbackDir) {
        comboCount = hitstunFrames > 0 ? comboCount + 1 : 1;
        applyDamage(scaledComboDamage(damage));
        armorHitsUsed++;
        velocityX = knockbackDir * GameConstants.KNOCKBACK_SPEED * 0.25f; // のけぞらず軽く押されるだけ
    }

    /** 現在の蓄積スタン値（HUD / デバッグ用・Task 79）。 */
    public int getStunMeter() {
        return stunMeter;
    }

    /**
     * 指定の必殺技を開始する（Task 20/24）。接地・非攻撃・非のけぞり時のみ。
     *
     * @param move 発動する必殺技（{@code Character.getSpecialMoves()} から選んだもの）
     * @return 開始できたか（飛び道具の発射判定に使う）
     */
    public boolean startSpecial(Move move) {
        return startSpecial(move, false);
    }

    /**
     * 必殺技を開始する（EX 指定つき・Task 54）。{@code ex} が true なら EX 必殺技として開始し、打撃必殺技では
     * 与ダメージが {@code EX_DAMAGE_MULTIPLIER} 倍になる（飛び道具の EX は Core 側で別途処理）。メーター消費・
     * 飛び道具生成は Core が行う。
     *
     * @param move 発動する必殺技（{@code Character.getSpecialMoves()} から選んだもの）
     * @param ex   EX 版として開始するか（メーター満タン時。打撃必殺技のダメージ強化に使う）
     * @return 開始できたか（メーター消費 / 飛び道具の発射判定に使う）
     */
    public boolean startSpecial(Move move, boolean ex) {
        // 新規発動（非攻撃中）に加え、命中した通常技からの特殊キャンセル（Task 47）でも開始できる。
        if (move == null || !(canStartAction() || canSpecialCancel())) {
            return false;
        }
        // しゃがみ通常技からの特殊キャンセル時に低姿勢フラグを引き継がないよう、開始前に姿勢フラグを落とす
        // （必殺技は立ち扱い。クリアしないと crouchAttacking が残り必殺技の hurtbox/判定高さ=LOW に化ける・Task 47）。
        // 新規発動（attackPhase==NONE）ではこれらは既に false なので no-op（チェーン開始経路と同じ作法）。
        crouchAttacking = false;
        aerialAttacking = false;
        throwing = false;
        dashAttacking = false;
        beginAttack(move);    // beginAttack が exAttack=false にリセットするので、その後に EX を立てる。
        exAttack = ex;        // 打撃必殺技なら CollisionSystem.activeHitbox がダメージを EX 倍にする（Task 54）。
        return true;
    }

    /** 新たな行動（攻撃 / 必殺技）を開始できる状態か（接地・非攻撃・非のけぞり・非ダウン）。 */
    public boolean canStartAction() {
        // knockdownFrames も見る（Task 60）：ダウン中は startSpecial が update の外から呼ばれても発動させない。
        // 見落とすと STARTUP が凍結保持され、起き上がりと同時に必殺技が暴発する（ノー startup の起き上がりリバーサル）。
        return grounded && attackPhase == AttackPhase.NONE && hitstunFrames <= 0 && knockdownFrames <= 0
                && dizzyFrames <= 0;
    }

    /**
     * 進行中の通常技がキャンセル可能な状態か（チェーンコンボ / 特殊キャンセルの共通前提・Task 45/47）。
     * 接地中・進行中が**通常技**（必殺技/投げ不可）・その技が active か recovery・命中/ガードで接触済み
     * （空振りキャンセル不可）。キャンセル先（上位通常技 or 必殺技）は呼び出し側で判定する。
     */
    private boolean isCancelableNormal() {
        return grounded
                && currentMove != null
                && currentMove.getButton() != null // 通常技のみ（必殺技/投げからはキャンセルしない）
                && attackHits > 0                  // 接触済み（空振りキャンセル不可・Task 74 で多段カウンタに統一）
                && (attackPhase == AttackPhase.ACTIVE || attackPhase == AttackPhase.RECOVERY);
    }

    /**
     * 進行中の通常技を、より強いボタンの通常技へキャンセル（チェーンコンボ）できるか（Task 45）。
     * {@link #isCancelableNormal()} に加え、新ボタンの段位（{@code ordinal}）が現在より上（弱→中→強の一方向）。
     * これにより、通常技の硬直を待たずに上位技へ繋いで連続ヒット（コンボ）を成立させられる。
     */
    public boolean canChainInto(AttackButton next) {
        if (next == null || !isCancelableNormal()) {
            return false;
        }
        return next.ordinal() > currentMove.getButton().ordinal();
    }

    /**
     * 進行中の通常技を必殺技でキャンセルできるか（特殊キャンセル・Task 47）。条件は {@link #isCancelableNormal()}
     * のみ（必殺技側にボタン段位は無いので段位条件はない）。通常技 → 必殺技（飛び道具等）へ繋いでコンボを伸ばせる。
     */
    public boolean canSpecialCancel() {
        return isCancelableNormal();
    }

    /** ボタン種別に対応する通常技を返す（見つからなければ null）。 */
    private Move selectNormalMove(AttackButton button) {
        Move[] moves = def.getNormalMoves();
        if (moves == null) {
            return null;
        }
        for (Move m : moves) {
            if (m.getButton() == button) {
                return m;
            }
        }
        return null;
    }

    /** 指定の技で攻撃ステートを開始する（通常 / 必殺で共通）。攻撃開始時にガードを解除する。 */
    private void beginAttack(Move move) {
        currentMove = move;
        attackPhase = AttackPhase.STARTUP;
        attackFrame = 0;
        attackHits = 0;     // 多段ヒットの命中回数をリセット（Task 74。新技は改めて命中判定される）
        attackHitGap = 0;
        armorHitsUsed = 0;  // スーパーアーマーの消費数をリセット（Task 80）
        exAttack = false; // 既定は非 EX。EX 必殺技のみ startSpecial(move, true) が beginAttack 後に true へ立てる（Task 54）。
        guarding = false; // 攻撃開始フレームにガード状態を残さない（同フレームの被弾が誤って applyGuard になるのを防ぐ）
        dashFrames = 0;   // 攻撃でダッシュをキャンセル（ダッシュ攻撃は通常攻撃として出る・Task 49）
    }

    /** 攻撃の経過フレームを 1 進め、startup/active/recovery の境界で区間を遷移させる（終了で NONE）。 */
    private void advanceAttack() {
        if (attackHitGap > 0) {
            attackHitGap--; // 多段ヒットのサブヒット間隔を減衰（Task 74。0 で次のヒットを許可）
        }
        attackFrame++;
        Move move = currentMove;
        int startup = move.getStartup();
        int active = move.getActive();
        int recovery = move.getRecovery();
        if (attackFrame < startup) {
            attackPhase = AttackPhase.STARTUP;
        } else if (attackFrame < startup + active) {
            attackPhase = AttackPhase.ACTIVE;
        } else if (attackFrame < startup + active + recovery) {
            attackPhase = AttackPhase.RECOVERY;
        } else {
            attackPhase = AttackPhase.NONE;
            attackFrame = 0;
            currentMove = null;
        }
    }

    /** キャラ矩形が画面端からはみ出さないよう中心 X を制限する。 */
    private void clampToStage() {
        float half = def.getWidth() / 2f;
        if (x < half) {
            x = half;
        } else if (x > GameConstants.WORLD_WIDTH - half) {
            x = GameConstants.WORLD_WIDTH - half;
        }
    }

    /** 画面端（壁）に接しているか（{@link #clampToStage} でクランプされた位置か）。壁バウンド判定用（Task 101）。 */
    private boolean atStageEdge() {
        float half = def.getWidth() / 2f;
        return x <= half + 0.5f || x >= GameConstants.WORLD_WIDTH - half - 0.5f;
    }

    /** 現在の水平速度が壁の方向へ向かっているか（壁バウンドの跳ね返り判定用・Task 101）。 */
    private boolean pushingIntoEdge() {
        float half = def.getWidth() / 2f;
        return (x <= half + 0.5f && velocityX < 0f)
                || (x >= GameConstants.WORLD_WIDTH - half - 0.5f && velocityX > 0f);
    }

    /** 押し合い解消などで中心 X を移動させ、画面端にクランプする。 */
    public void nudgeX(float dx) {
        x += dx;
        clampToStage();
    }

    /** 相手の位置に応じて向きを更新する（同 X のときは右向き維持）。 */
    public void faceTowards(Fighter opponent) {
        if (opponent.x > this.x) {
            facingRight = true;
        } else if (opponent.x < this.x) {
            facingRight = false;
        }
    }

    public Character getDef() {
        return def;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public boolean isFacingRight() {
        return facingRight;
    }

    public boolean isGrounded() {
        return grounded;
    }

    /** 垂直速度（px/frame, 上向き正）。AI の対空判断で「下降中（{@code <= 0}）か」を見るのに使う（Task 55）。 */
    public float getVelocityY() {
        return velocityY;
    }

    public int getMoveDir() {
        return moveDir;
    }

    public boolean isWalking() {
        return grounded && moveDir != 0 && !crouching;
    }

    /** ダッシュ（二度押しステップ）中か（Task 49）。 */
    public boolean isDashing() {
        return dashFrames > 0;
    }

    /**
     * 進行中のダッシュを即時キャンセルする（残りフレームを 0 に）（Task 50）。
     *
     * <p>ダッシュ中は {@code update()} が {@code guarding=false} を強制するため、ダッシュ接近中の AI は
     * {@code GUARD_RANGE} 内で相手の打撃を検知してもガードに移れない。防御を優先したいときにこのフックで
     * 自分のダッシュを中断し、同フレームの後退入力をガードとして成立させる（攻撃/被弾による既存のキャンセルと同じく
     * 単に {@code dashFrames} を 0 にするだけで、次の {@code update()} から歩行/ガード分岐へ戻る）。
     */
    public void cancelDash() {
        dashFrames = 0;
    }

    /** しゃがみ移動中か（低速クロール）（Task 29）。 */
    public boolean isCrouchWalking() {
        return crouching && grounded && moveDir != 0;
    }

    public boolean isCrouching() {
        return crouching;
    }

    /** しゃがみ攻撃中か（攻撃中 + しゃがみ姿勢を維持）（Task 28）。 */
    public boolean isCrouchAttacking() {
        return crouchAttacking;
    }

    /** 空中攻撃中か（空中で発動した攻撃が進行中）（Task 32）。 */
    public boolean isAerialAttacking() {
        return aerialAttacking;
    }

    /** 投げ（ガード不能の掴み）を発動中か（Task 35）。攻撃ステート中かつ投げ技として開始したもの。 */
    public boolean isThrowing() {
        return throwing;
    }

    /**
     * 現在発動中の投げが空中投げ（{@code airThrowMove}）か（Task 70）。
     * 発動した {@link Move} の同一性で判定するため、低空空中投げが startup/active 中に着地しても種別は不変
     * （解決時の接地状態に依存せず、「空中投げ＝空中の相手専用」「着地で回避可」の不変条件を保つ・Codex 指摘）。
     */
    public boolean isAirThrowing() {
        return throwing && currentMove != null && currentMove == def.getAirThrowMove();
    }

    /** ダッシュ攻撃（突進攻撃）を発動中か（Task 65）。攻撃ステート中かつダッシュ中に開始したもの。 */
    public boolean isDashAttacking() {
        return dashAttacking;
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public int getMaxHp() {
        return def.getHp();
    }

    public float getHpRatio() {
        int max = def.getHp();
        return max > 0 ? Math.max(0f, Math.min(1f, currentHp / (float) max)) : 0f;
    }

    /** HP を最大まで回復する（トレーニングモードの無限 HP ダミー用・Task 90）。 */
    public void restoreFullHp() {
        currentHp = def.getHp();
    }

    public void applyDamage(int amount) {
        if (amount <= 0) {
            return;
        }
        currentHp = Math.max(0, currentHp - amount);
    }

    /**
     * コンボダメージ補正後の与ダメージを返す（Task 46）。現在の {@code comboCount}（このメソッドを呼ぶ前に
     * 加算済み）に応じて、2 ヒット目以降は倍率を {@link GameConstants#COMBO_SCALE_STEP} ずつ下げる
     * （{@link GameConstants#COMBO_SCALE_MIN} で下限）。1 ヒット目（または単発）は等倍。最低 1 ダメージは保証する。
     * 補正は倍率のみで乱数を使わない（入力リプレイの決定性を保つ）。
     */
    private int scaledComboDamage(int baseDamage) {
        if (baseDamage <= 0 || comboCount <= 1) {
            return baseDamage;
        }
        float scale = Math.max(GameConstants.COMBO_SCALE_MIN,
                1f - (comboCount - 1) * GameConstants.COMBO_SCALE_STEP);
        return Math.max(1, Math.round(baseDamage * scale));
    }

    /** ガード中か（後退方向保持・非のけぞり・非攻撃）。立ち / しゃがみ / 空中いずれも含む（接地は不要・Task 59）。 */
    public boolean isGuarding() {
        return guarding;
    }

    /** ジャストガード成立直後か（表示ラベルに {@code [JUST]} を付すための判定・Task 81）。 */
    public boolean isJustGuarding() {
        return justGuardFrames > 0;
    }

    /** しゃがみガード中か（ガード中 + 低姿勢を維持）（Task 30）。 */
    public boolean isCrouchGuarding() {
        return guarding && crouching;
    }

    /** 空中ガード中か（ガード中 + 滞空）（Task 59）。立ち扱いで飛び道具・中段/上段を防ぐ。 */
    public boolean isAirGuarding() {
        return guarding && !grounded;
    }

    /** ガードクラッシュ中か（ゲージが尽きてガード不能・行動不能の隙にある）（Task 43）。 */
    public boolean isGuardBroken() {
        return guardBreakFrames > 0;
    }

    /** 現在のガードゲージ量（0〜{@link GameConstants#GUARD_GAUGE_MAX}）。HUD ゲージ表示に使用（Task 43）。 */
    public float getGuardGauge() {
        return guardGauge;
    }

    /** 必殺技ゲージを加算する（{@link GameConstants#SUPER_METER_MAX} で頭打ち）（Task 44）。 */
    public void gainMeter(float amount) {
        superMeter = Math.min(GameConstants.SUPER_METER_MAX, superMeter + amount);
    }

    /** 必殺技ゲージが満タンか（EX 必殺技を撃てるか）（Task 44）。 */
    public boolean hasFullMeter() {
        return superMeter >= GameConstants.SUPER_METER_MAX;
    }

    /** 満タンの必殺技ゲージを消費する（EX 発動時に呼ぶ。0 にする）（Task 44）。 */
    public void spendFullMeter() {
        superMeter = 0f;
    }

    /** 撮影 / 初期化用にゲージ量を直接設定する（0〜MAX にクランプ）（Task 44）。 */
    public void setMeter(float value) {
        superMeter = Math.max(0f, Math.min(GameConstants.SUPER_METER_MAX, value));
    }

    /** 現在の必殺技ゲージ量（0〜{@link GameConstants#SUPER_METER_MAX}）。HUD ゲージ表示に使用（Task 44）。 */
    public float getSuperMeter() {
        return superMeter;
    }

    public boolean isKO() {
        return currentHp <= 0;
    }

    public AttackPhase getAttackPhase() {
        return attackPhase;
    }

    public boolean isAttacking() {
        return attackPhase.isAttacking();
    }

    public boolean isHitboxActive() {
        return attackPhase == AttackPhase.ACTIVE;
    }

    public int getAttackFrame() {
        return attackFrame;
    }

    public Move getCurrentMove() {
        return currentMove;
    }

    /**
     * 現在、技の無敵フレーム中か（Task 53）。進行中の技に {@code invincibleFrames > 0} があり、その技の発生からの
     * 経過フレーム（{@link #attackFrame}）が無敵窓内（{@code 1..invincibleFrames}）なら true。
     *
     * <p>無敵中はこのファイターの食らい判定が無効になり（{@code CollisionSystem.isHitting}/{@code hits} が defender 側で参照）、
     * 打撃必殺技に付ければ昇龍拳タイプのリバーサル / 対空になる。攻撃中（{@code attackPhase != NONE}）のみ有効で、
     * 技が終われば自動的に false。乱数は使わず経過フレームのみで決まる（決定的＝入力リプレイと両立）。
     */
    public boolean isInvincible() {
        if (attackPhase == AttackPhase.NONE || currentMove == null) {
            return false;
        }
        int inv = currentMove.getInvincibleFrames();
        return inv > 0 && attackFrame <= inv;
    }

    /**
     * 進行中の技が EX 版か（Task 54）。EX 打撃必殺技は与ダメージが {@code EX_DAMAGE_MULTIPLIER} 倍になり、
     * 描画も金色 strike ＋ {@code [EX]} ラベルで区別する。攻撃中（{@code attackPhase != NONE}）のみ有効で、
     * 技が終われば自動的に false（次の技開始で {@code beginAttack} が false に戻す）。
     */
    public boolean isExAttack() {
        return attackPhase != AttackPhase.NONE && exAttack;
    }

    /** 必殺技を発動中か（進行中の技が specialMoves 配列の要素か）。 */
    public boolean isSpecialActive() {
        if (currentMove == null) {
            return false;
        }
        Move[] specials = def.getSpecialMoves();
        if (specials == null) {
            return false;
        }
        for (Move sp : specials) {
            if (currentMove == sp) {
                return true;
            }
        }
        return false;
    }

    /** 進行中の技が一度でも命中したか（チェーン / 特殊キャンセルの「接触済み」判定に使う・Task 74）。 */
    public boolean hasAttackConnected() {
        return attackHits > 0;
    }

    /**
     * 進行中の技が「今このフレームに」ヒット判定を確定できるか（Task 74・多段ヒット対応）。
     * 単発技（{@code hits == 1}）は 1 回まで、多段技は {@code hits} 回まで、かつ前ヒットから {@code hitGap}
     * フレーム空いていれば true。{@link CollisionSystem#isHitting} と AND して resolveHit が判定する。
     */
    public boolean canHitNow() {
        return currentMove != null && attackHits < currentMove.getHits() && attackHitGap <= 0;
    }

    /**
     * 命中（または投げ whiff）を 1 回消費する。多段ヒット数を加算し、次ヒットまでの待機フレーム（{@code hitGap}）を立てる。
     * 単発技では 1 回呼べば {@link #canHitNow()} が false になり多段ヒットを防ぐ（従来の attackConnected と同じ挙動）。
     */
    public void markAttackConnected() {
        attackHits++;
        attackHitGap = currentMove != null ? currentMove.getHitGap() : 0;
    }

    public boolean isInHitstun() {
        return hitstunFrames > 0;
    }

    public int getHitstunFrames() {
        return hitstunFrames;
    }

    /**
     * ダウン中か（Task 60）。ダウン中は行動不能かつ<b>被弾無敵</b>（起き攻め / OTG なし・当たり判定 / 描画が参照）。
     * {@code knockdownFrames > 0}（応用フレーム〜拘束中）に加え、ダウンが解ける最終フレーム（{@code update()} の inert 分岐で
     * {@code knockdownFrames} が 0 になったフレーム）も {@link #knockdownInertThisFrame} ラッチで down 扱いにする。
     * これにより「まだ動けないのに被弾だけ可能」な 1F の無防備窓を防ぎ、行動可能フレームと被弾可能フレームを揃える。
     */
    public boolean isKnockedDown() {
        return knockdownFrames > 0 || knockdownInertThisFrame;
    }

    /** 受け身（ukemi・クイック起き上がり）成立中か（Task 66）。ダウン中の早期起き上がりを表示で識別するための判定。 */
    public boolean isUkemiRecovering() {
        return ukemiRecovery && knockdownFrames > 0;
    }

    /** 現在のダウンが受け身不能（hard knockdown・Task 88）か。ダウン中のみ有効（表示ラベル用）。 */
    public boolean isHardKnockedDown() {
        return hardKnockdown && knockdownFrames > 0;
    }

    /** 現在このファイターが受けている連続ヒット数（コンボ数）。hitstun が切れると 0 に戻る（Task 39）。 */
    public int getComboCount() {
        return comboCount;
    }
}
