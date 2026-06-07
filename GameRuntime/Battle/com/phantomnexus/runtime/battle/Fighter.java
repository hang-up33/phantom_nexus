package com.phantomnexus.runtime.battle;

import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.Character;

/**
 * 実行時のファイター状態（Task 7: キャラクター移動）。
 *
 * <p>静的定義 {@link Character} を参照し、位置（中心 X / 足元 Y）・向きといった実行時状態を保持する。
 * 入力の読み取りは行わず、移動量は {@link #update(int)} に {@code moveDir} として外部から渡す
 * （入力配線と分離し、後続の AI 差し替え・テスト容易性を確保する）。ジャンプ（Task 8）・攻撃
 * （Task 11）・HP（Task 10）はフィールドとステートを段階的に追加していく。
 */
public class Fighter {

    private final Character def;
    private float x;          // 中心 X（ワールド座標）
    private float y;          // 足元 Y。MVP は地面固定。ジャンプは Task 8 で可変化
    private boolean facingRight;

    public Fighter(Character def, float spawnX, boolean facingRight) {
        this.def = def;
        this.x = spawnX;
        this.y = GameConstants.GROUND_Y;
        this.facingRight = facingRight;
    }

    /**
     * 1 フレームの左右移動を適用する。
     *
     * @param moveDir 移動方向（-1 = 左 / 0 = 静止 / +1 = 右）。歩行速度は {@code def.walkSpeed}。
     */
    public void update(int moveDir) {
        x += moveDir * def.getWalkSpeed();
        clampToStage();
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
}
