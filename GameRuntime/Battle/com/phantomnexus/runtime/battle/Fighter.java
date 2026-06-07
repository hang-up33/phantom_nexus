package com.phantomnexus.runtime.battle;

import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Character;

/**
 * 実行時のファイター状態（Task 7: 移動 / Task 8: ジャンプ）。
 *
 * <p>静的定義 {@link Character} を参照し、位置（中心 X / 足元 Y）・垂直速度・接地状態・向きといった
 * 実行時状態を保持する。入力の読み取りは行わず、左右移動量とジャンプ入力は
 * {@link #update(int, boolean)} に外部から渡す（入力配線と分離し、後続の AI 差し替え・
 * テスト容易性を確保する）。攻撃（Task 11）・HP（Task 10）はフィールドとステートを段階的に追加していく。
 */
public class Fighter {

    private final Character def;
    private float x;          // 中心 X（ワールド座標）
    private float y;          // 足元 Y。接地時は GROUND_Y、ジャンプ中は上昇（Task 8）
    private float velocityY;  // 垂直速度（px/frame）。上向きが正
    private boolean grounded = true;
    private boolean facingRight;

    public Fighter(Character def, float spawnX, boolean facingRight) {
        this.def = def;
        this.x = spawnX;
        this.y = GameConstants.GROUND_Y;
        this.facingRight = facingRight;
    }

    /**
     * 1 フレームの移動・ジャンプを適用する。
     *
     * @param moveDir     左右移動方向（-1 = 左 / 0 = 静止 / +1 = 右）。歩行速度は {@code def.walkSpeed}。
     * @param jumpPressed このフレームでジャンプ入力の立ち上がりがあったか。接地時のみ発動する。
     */
    public void update(int moveDir, boolean jumpPressed) {
        // 左右移動（MVP は空中横移動も許可）
        x += moveDir * def.getWalkSpeed();
        clampToStage();

        // 接地中にジャンプ入力があれば初速を与えて離地
        if (jumpPressed && grounded) {
            velocityY = def.getJumpPower();
            grounded = false;
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

    /** キャラ矩形が画面端からはみ出さないよう中心 X を制限する。 */
    private void clampToStage() {
        float half = def.getWidth() / 2f;
        if (x < half) {
            x = half;
        } else if (x > GameConstants.WORLD_WIDTH - half) {
            x = GameConstants.WORLD_WIDTH - half;
        }
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
}
