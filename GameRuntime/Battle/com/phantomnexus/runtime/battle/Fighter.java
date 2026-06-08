package com.phantomnexus.runtime.battle;

import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.Move;

/**
 * 実行時のファイター状態（Task 7: 移動 / Task 8: ジャンプ / Task 11: 攻撃 / Task 24: 複数技 / Task 25: しゃがみ）。
 *
 * <p>静的定義 {@link Character} を参照し、位置（中心 X / 足元 Y）・垂直速度・接地状態・向き・HP・攻撃区間
 * といった実行時状態を保持する。入力の読み取りは行わず、左右移動量・ジャンプ・攻撃ボタン（弱/中/強 or null）・
 * しゃがみ押下は {@link #update(int, boolean, String, boolean)} に外部から渡す。
 * しゃがみ中は移動・ジャンプ・通常技入力を受け付けず、食らい判定が半分の高さになる。
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
     * @param attackButton 押されたボタン種別（"light" / "medium" / "heavy"）。null なら攻撃なし
     * @param crouchHeld   DOWN ボタンを押し続けているか（接地中のみしゃがみ遷移）
     */
    public void update(int moveDir, boolean jumpPressed, String attackButton, boolean crouchHeld) {
        if (hitstunFrames > 0) {
            crouching = false;
            this.moveDir = 0;
            hitstunFrames--;
            x += velocityX;
            clampToStage();
            velocityX *= GameConstants.KNOCKBACK_FRICTION;
            if (Math.abs(velocityX) < 0.1f) {
                velocityX = 0f;
            }
        } else {
            // しゃがみ中は通常技入力を受け付けない（しゃがみ攻撃は将来拡張）。
            if (attackPhase == AttackPhase.NONE && attackButton != null && grounded && !crouching) {
                Move move = selectNormalMove(attackButton);
                if (move != null) {
                    beginAttack(move);
                }
            }

            if (attackPhase != AttackPhase.NONE) {
                crouching = false;  // 攻撃開始でしゃがみ解除
                this.moveDir = 0;
                advanceAttack();
            } else if (crouchHeld && grounded) {
                crouching = true;
                this.moveDir = 0;   // しゃがみ中は横移動不可
                // ジャンプ入力は無視
            } else {
                crouching = false;
                this.moveDir = moveDir;
                x += moveDir * def.getWalkSpeed();
                clampToStage();
                if (jumpPressed && grounded) {
                    velocityY = def.getJumpPower();
                    grounded = false;
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

    /**
     * 被弾を適用する（HP 減算・のけぞり遷移・knockback）。攻撃中だった場合は中断する。
     */
    public void applyHit(int damage, int hitstun, int knockbackDir) {
        applyDamage(damage);
        hitstunFrames = hitstun;
        velocityX = knockbackDir * GameConstants.KNOCKBACK_SPEED;
        attackPhase = AttackPhase.NONE;
        attackFrame = 0;
        currentMove = null;
        crouching = false;
    }

    /**
     * 指定の必殺技を開始する（Task 20/24）。接地・非攻撃・非のけぞり時のみ。
     *
     * @param move 発動する必殺技（{@code Character.getSpecialMoves()} から選んだもの）
     * @return 開始できたか（飛び道具の発射判定に使う）
     */
    public boolean startSpecial(Move move) {
        if (!canStartAction() || move == null) {
            return false;
        }
        beginAttack(move);
        return true;
    }

    /** 新たな行動（攻撃 / 必殺技）を開始できる状態か（接地・非攻撃・非のけぞり）。 */
    public boolean canStartAction() {
        return grounded && attackPhase == AttackPhase.NONE && hitstunFrames <= 0;
    }

    /** ボタン種別に対応する通常技を返す（見つからなければ null）。 */
    private Move selectNormalMove(String button) {
        Move[] moves = def.getNormalMoves();
        if (moves == null) {
            return null;
        }
        String trimmed = button.trim();
        for (Move m : moves) {
            String mb = m.getButton();
            if (mb != null && trimmed.equalsIgnoreCase(mb.trim())) {
                return m;
            }
        }
        return null;
    }

    /** 指定の技で攻撃ステートを開始する（通常 / 必殺で共通）。 */
    private void beginAttack(Move move) {
        currentMove = move;
        attackPhase = AttackPhase.STARTUP;
        attackFrame = 0;
        attackConnected = false;
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
        return grounded && moveDir != 0;
    }

    public boolean isCrouching() {
        return crouching;
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
}
