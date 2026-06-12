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
    private boolean facingRight;
    private int moveDir;      // 直近フレームに適用した左右移動方向（-1/0/+1）
    private int currentHp;    // 現在 HP
    private AttackPhase attackPhase = AttackPhase.NONE;
    private int attackFrame;
    private Move currentMove;  // 進行中の技（攻撃中のみ非 null）
    private boolean attackConnected;
    private int hitstunFrames;
    private float velocityX;
    private boolean crouching;
    private boolean crouchAttacking; // しゃがみ中に開始した攻撃（Task 28）
    private boolean aerialAttacking;  // 空中で開始した攻撃（Task 32）
    private boolean throwing;          // 投げ（ガード不能の掴み）を発動中か（Task 35）
    private int throwTechWindow;        // 投げ抜け猶予窓（投げボタン押下でアーム・毎フレーム減衰）（Task 36）
    private int throwTechFrames;        // 投げ抜け成立後の硬直/表示フレーム（ノーダメージ・hitstun と併走）（Task 36）
    private int comboCount;             // 現在受けている連続ヒット数（hitstun 継続中の被弾で加算・回復で 0）（Task 39）
    private boolean guarding;  // 接地中・後退方向保持でガード中か（Task 27）
    private float guardGauge = GameConstants.GUARD_GAUGE_MAX; // ガードゲージ（ガードで減り非ガードで回復・Task 43）
    private int guardBreakFrames; // ガードクラッシュの行動不能/表示フレーム（hitstun を流用・Task 43）
    private float superMeter; // 必殺技ゲージ（攻撃の当て / 被弾 / ガードで貯まり EX 必殺技で消費・Task 44）

    public Fighter(Character def, float spawnX, boolean facingRight) {
        this.def = def;
        this.x = spawnX;
        this.y = GameConstants.GROUND_Y;
        this.facingRight = facingRight;
        this.currentHp = def.getHp();
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
        // ガード判定：接地・非のけぞり・非攻撃中に後退方向を保持しているか。
        // 後退方向保持は立ち（crouchHeld=false）でも しゃがみ（crouchHeld=true）でも成立し、
        // しゃがみ後退は低姿勢ガード（crouch guard）になる（Task 30）。低姿勢判定は crouching を併用。
        int backDir = facingRight ? -1 : 1;
        guarding = grounded && hitstunFrames <= 0 && attackPhase == AttackPhase.NONE
                   && moveDir != 0 && moveDir == backDir;
        // ガードゲージは非ガード・非クラッシュ中に徐々に回復する（Task 43。ガード中は減る一方）。
        if (!guarding && guardBreakFrames <= 0 && guardGauge < GameConstants.GUARD_GAUGE_MAX) {
            guardGauge = Math.min(GameConstants.GUARD_GAUGE_MAX,
                                  guardGauge + GameConstants.GUARD_REGEN_PER_FRAME);
        }
        if (hitstunFrames > 0) {
            crouching = false;
            this.moveDir = 0;
            hitstunFrames--;
            // hitstun から復帰した瞬間にコンボを終了（次の被弾は新規コンボ＝1 から数え直す）（Task 39）。
            if (hitstunFrames == 0) {
                comboCount = 0;
            }
            x += velocityX;
            clampToStage();
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
            // 攻撃の発動：接地時はしゃがみ遷移フレーム（crouchHeld かつ未しゃがみ）を除いて可。
            // 空中では空中攻撃（Task 32）として発動可（しゃがみ条件は無視）。
            if (attackPhase == AttackPhase.NONE && (attackButton != null || throwReq)
                    && (grounded ? (!crouchHeld || crouching) : true)) {
                // 投げ（Task 35）は throwReq で起動する地上・立ち専用のガード不能掴み。
                // 通常技 / 空中攻撃 / しゃがみ攻撃のいずれにも分類せず、専用フラグ throwing を立てる。
                Move move = throwReq ? (grounded ? def.getThrowMove() : null) : selectNormalMove(attackButton);
                if (move != null) {
                    throwing = throwReq;
                    crouchAttacking = !throwReq && grounded && crouching; // しゃがみ中に発動 → 下段攻撃フラグ（投げ / 空中は不可）
                    aerialAttacking = !throwReq && !grounded;             // 空中で発動 → 空中攻撃フラグ（Task 32）
                    beginAttack(move);
                }
            } else if (attackButton != null && !throwReq && canChainInto(attackButton)) {
                // チェーンキャンセル（Task 45）：命中した通常技を上位ボタンの通常技へ即キャンセルし、
                // 硬直を待たずに繋いで連続ヒットにする。新技は接地の立ち通常技として開始する。
                Move move = selectNormalMove(attackButton);
                if (move != null) {
                    crouchAttacking = false;
                    aerialAttacking = false;
                    throwing = false;
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
                    if (!crouchHeld) {
                        crouching = false; // DOWN を離していれば攻撃終了と同フレームに姿勢解除
                    }
                }
            } else if (crouchHeld && grounded) {
                crouching = true;
                this.moveDir = moveDir;                     // 低速クロール：方向を記録
                x += moveDir * def.getWalkSpeed() * 0.5f;  // 通常の半速で移動
                clampToStage();
                // ジャンプ入力は無視
            } else {
                crouching = false;
                this.moveDir = moveDir;
                x += moveDir * def.getWalkSpeed();
                clampToStage();
                if (jumpPressed && grounded) {
                    velocityY = def.getJumpPower();
                    grounded = false;
                    guarding = false; // 空中ガード不可：ジャンプ成立フレームでガードをクリア
                }
            }
        }

        velocityY -= GameConstants.GRAVITY;
        y += velocityY;

        if (y <= GameConstants.GROUND_Y) {
            y = GameConstants.GROUND_Y;
            velocityY = 0f;
            grounded = true;
        }
    }

    /** ラウンド間リセット（HP・位置・向き・全状態をスポーン時の値に戻す）。 */
    public void reset(float spawnX, boolean spawnFacingRight) {
        x = spawnX;
        y = GameConstants.GROUND_Y;
        velocityY = 0f;
        velocityX = 0f;
        grounded = true;
        facingRight = spawnFacingRight;
        moveDir = 0;
        currentHp = def.getHp();
        attackPhase = AttackPhase.NONE;
        attackFrame = 0;
        currentMove = null;
        attackConnected = false;
        hitstunFrames = 0;
        crouchAttacking = false;
        aerialAttacking = false;
        throwing = false;
        throwTechWindow = 0;
        throwTechFrames = 0;
        comboCount = 0;
        guarding = false;
        guardGauge = GameConstants.GUARD_GAUGE_MAX;
        guardBreakFrames = 0;
        superMeter = 0f;
    }

    /**
     * ガード中の被弾を適用する（chip ダメージのみ・のけぞりなし・軽微な knockback）（Task 27）。
     * chip ダメージは通常ダメージの 10%（最低 1）。攻撃の勢いで微小後退する。
     */
    public void applyGuard(int attackDamage, int knockbackDir) {
        int chip = Math.max(1, attackDamage / 10);
        applyDamage(chip);
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
        throwTechWindow = 0;
        throwTechFrames = 0; // 投げ抜け硬直中に被弾したらラベルをのけぞりへ戻す（表示 desync 防止・Task 36）
        guardBreakFrames = 0; // ガードクラッシュ硬直中にフル被弾したらラベルをのけぞりへ戻す（Task 43）
        guarding = false;    // 被弾で neutral から抜けるので guarding を即解除（同フレームの飛び道具/描画が誤ってガード扱いしない）
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
        throwTechWindow = 0;
        throwTechFrames = 0; // 投げ抜け硬直中に投げで上書きされたらラベルをのけぞりへ戻す（Task 36）
        guardBreakFrames = 0; // ガードクラッシュ硬直中に投げで上書きされたらラベルを更新（Task 43）
        guarding = false;    // 投げ（ガード不能）で neutral から抜けるので guarding を即解除（同フレームの飛び道具/描画が誤ってガード扱いしない）
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
        guarding = false; // 投げ抜けで neutral から抜けるので guarding を即解除（同フレームの飛び道具/描画が誤ってガード扱いしない）
    }

    /** 投げ抜けの硬直中か（表示ラベルを "tech" にするための判定）（Task 36）。 */
    public boolean isThrowTeched() {
        return throwTechFrames > 0;
    }

    /**
     * 指定の必殺技を開始する（Task 20/24）。接地・非攻撃・非のけぞり時のみ。
     *
     * @param move 発動する必殺技（{@code Character.getSpecialMoves()} から選んだもの）
     * @return 開始できたか（飛び道具の発射判定に使う）
     */
    public boolean startSpecial(Move move) {
        // 新規発動（非攻撃中）に加え、命中した通常技からの特殊キャンセル（Task 47）でも開始できる。
        if (move == null || !(canStartAction() || canSpecialCancel())) {
            return false;
        }
        beginAttack(move);
        return true;
    }

    /** 新たな行動（攻撃 / 必殺技）を開始できる状態か（接地・非攻撃・非のけぞり）。 */
    public boolean canStartAction() {
        return grounded && attackPhase == AttackPhase.NONE && hitstunFrames <= 0;
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
                && attackConnected
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
        attackConnected = false;
        guarding = false; // 攻撃開始フレームにガード状態を残さない（同フレームの被弾が誤って applyGuard になるのを防ぐ）
    }

    /** 攻撃の経過フレームを 1 進め、startup/active/recovery の境界で区間を遷移させる（終了で NONE）。 */
    private void advanceAttack() {
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

    public int getMoveDir() {
        return moveDir;
    }

    public boolean isWalking() {
        return grounded && moveDir != 0 && !crouching;
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

    /** ガード中か（後退方向保持・接地・非のけぞり・非攻撃）。立ち / しゃがみ両方を含む。 */
    public boolean isGuarding() {
        return guarding;
    }

    /** しゃがみガード中か（ガード中 + 低姿勢を維持）（Task 30）。 */
    public boolean isCrouchGuarding() {
        return guarding && crouching;
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

    public boolean hasAttackConnected() {
        return attackConnected;
    }

    public void markAttackConnected() {
        attackConnected = true;
    }

    public boolean isInHitstun() {
        return hitstunFrames > 0;
    }

    public int getHitstunFrames() {
        return hitstunFrames;
    }

    /** 現在このファイターが受けている連続ヒット数（コンボ数）。hitstun が切れると 0 に戻る（Task 39）。 */
    public int getComboCount() {
        return comboCount;
    }
}
