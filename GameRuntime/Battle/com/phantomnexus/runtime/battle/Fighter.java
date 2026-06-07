package com.phantomnexus.runtime.battle;

import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.Move;

/**
 * 実行時のファイター状態（Task 7: 移動 / Task 8: ジャンプ / Task 11: 攻撃）。
 *
 * <p>静的定義 {@link Character} を参照し、位置（中心 X / 足元 Y）・垂直速度・接地状態・向き・HP・攻撃区間
 * といった実行時状態を保持する。入力の読み取りは行わず、左右移動量・ジャンプ・攻撃入力は
 * {@link #update(int, boolean, boolean)} に外部から渡す（入力配線と分離し、後続の AI 差し替え・
 * テスト容易性を確保する）。攻撃は startup/active/recovery の 3 区間（{@link AttackPhase}）を進む。
 */
public class Fighter {

    private final Character def;
    private float x;          // 中心 X（ワールド座標）
    private float y;          // 足元 Y。接地時は GROUND_Y、ジャンプ中は上昇（Task 8）
    private float velocityY;  // 垂直速度（px/frame）。上向きが正
    private boolean grounded = true;
    private boolean facingRight;
    private int moveDir;      // 直近フレームに適用した左右移動方向（-1/0/+1）。アニメ状態導出に使用
    private int currentHp;    // 現在 HP（Task 10: 初期値は def.hp。減算は Task 13 のダメージ処理）
    private AttackPhase attackPhase = AttackPhase.NONE; // 攻撃区間（Task 11）
    private int attackFrame;   // 現在の攻撃に入ってからの経過フレーム
    private boolean attackConnected; // 現在の攻撃が既に命中済みか（多段ヒット防止。Task 12/13）

    public Fighter(Character def, float spawnX, boolean facingRight) {
        this.def = def;
        this.x = spawnX;
        this.y = GameConstants.GROUND_Y;
        this.facingRight = facingRight;
        this.currentHp = def.getHp();
    }

    /**
     * 1 フレームの攻撃・移動・ジャンプを適用する。
     *
     * <p>攻撃中（{@link AttackPhase#NONE} 以外）は行動拘束し、左右移動・ジャンプ・新規攻撃を受け付けない。
     * 攻撃は接地時のみ発生し、{@code def.normalAttack} の startup/active/recovery を順に進めて終了で
     * {@code NONE} へ戻る。重力・着地は攻撃中も適用する（地上開始のため通常は接地を維持）。
     *
     * @param moveDir       左右移動方向（-1 = 左 / 0 = 静止 / +1 = 右）。歩行速度は {@code def.walkSpeed}。
     * @param jumpPressed   このフレームでジャンプ入力の立ち上がりがあったか。接地時のみ発動。
     * @param attackPressed このフレームで攻撃入力の立ち上がりがあったか。接地・非攻撃時のみ発動。
     */
    public void update(int moveDir, boolean jumpPressed, boolean attackPressed) {
        // 非攻撃かつ接地中に攻撃入力があり、技定義があれば攻撃を開始する。
        if (attackPhase == AttackPhase.NONE && attackPressed && grounded && def.getNormalAttack() != null) {
            attackPhase = AttackPhase.STARTUP;
            attackFrame = 0;
            attackConnected = false; // 新しい攻撃ごとに命中済みフラグをリセット
        }

        if (attackPhase != AttackPhase.NONE) {
            // 攻撃中：行動拘束（横移動・ジャンプ無効）し、攻撃区間のみ進める。
            this.moveDir = 0;
            advanceAttack();
        } else {
            // 通常時：左右移動（MVP は空中横移動も許可）＋ジャンプ。
            this.moveDir = moveDir;
            x += moveDir * def.getWalkSpeed();
            clampToStage();
            if (jumpPressed && grounded) {
                velocityY = def.getJumpPower();
                grounded = false;
            }
        }

        // 重力 + 垂直積分
        velocityY -= GameConstants.GRAVITY;
        y += velocityY;

        // 着地判定
        if (y <= GameConstants.GROUND_Y) {
            y = GameConstants.GROUND_Y;
            velocityY = 0f;
            grounded = true;
        }
    }

    /** 攻撃の経過フレームを 1 進め、startup/active/recovery の境界で区間を遷移させる（終了で NONE）。 */
    private void advanceAttack() {
        attackFrame++;
        Move move = def.getNormalAttack();
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

    /** 押し合い解消などで中心 X を移動させ、画面端にクランプする（Task 12 の衝突処理から呼ぶ）。 */
    public void nudgeX(float dx) {
        x += dx;
        clampToStage();
    }

    /** 相手の位置に応じて向きを更新する（相手側を向く。同 X のときは右向き維持）。 */
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

    /** 接地中か（ジャンプ可否・着地判定・後続のアニメーション状態に使用）。 */
    public boolean isGrounded() {
        return grounded;
    }

    /** 直近フレームに適用した左右移動方向（-1 = 左 / 0 = 静止 / +1 = 右）。 */
    public int getMoveDir() {
        return moveDir;
    }

    /** 接地中に左右入力で移動しているか（アニメーションの walk 状態導出に使用）。 */
    public boolean isWalking() {
        return grounded && moveDir != 0;
    }

    /** 現在 HP。 */
    public int getCurrentHp() {
        return currentHp;
    }

    /** 最大 HP（キャラ定義の {@code hp}）。HP ゲージの割合算出に使用。 */
    public int getMaxHp() {
        return def.getHp();
    }

    /** 現在 HP の割合（0.0〜1.0）。HP ゲージの描画に使用。 */
    public float getHpRatio() {
        int max = def.getHp();
        return max > 0 ? Math.max(0f, Math.min(1f, currentHp / (float) max)) : 0f;
    }

    /**
     * ダメージを適用し HP を減算する（0 未満にはしない）。Task 13 のダメージ処理から呼ぶ。
     *
     * @param amount 減算量（負値は無視）
     */
    public void applyDamage(int amount) {
        if (amount <= 0) {
            return;
        }
        currentHp = Math.max(0, currentHp - amount);
    }

    /** HP が 0 か（KO 判定。Task 14 の勝敗判定に使用）。 */
    public boolean isKO() {
        return currentHp <= 0;
    }

    /** 現在の攻撃区間。 */
    public AttackPhase getAttackPhase() {
        return attackPhase;
    }

    /** 攻撃中（startup/active/recovery のいずれか）か。 */
    public boolean isAttacking() {
        return attackPhase.isAttacking();
    }

    /** 攻撃判定（hitbox）が有効か（active 区間のみ true。当たり判定は Task 12）。 */
    public boolean isHitboxActive() {
        return attackPhase == AttackPhase.ACTIVE;
    }

    /** 現在の攻撃に入ってからの経過フレーム（攻撃可視化 / デバッグ用）。 */
    public int getAttackFrame() {
        return attackFrame;
    }

    /** 現在の攻撃が既に相手へ命中済みか（多段ヒット防止フラグ）。 */
    public boolean hasAttackConnected() {
        return attackConnected;
    }

    /** 現在の攻撃を命中済みにする（同一 active 区間での再ヒットを防ぐ。Task 12/13）。 */
    public void markAttackConnected() {
        attackConnected = true;
    }
}
