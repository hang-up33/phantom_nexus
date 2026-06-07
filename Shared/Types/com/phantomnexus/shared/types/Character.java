package com.phantomnexus.shared.types;

/**
 * キャラクターの静的定義（データの単一の真実）。
 *
 * <p>外部 JSON（{@code Assets/Characters/<id>.json}）から読み込まれる静的属性を表す POJO。
 * 位置・速度・現在 HP などの実行時状態は本クラスには持たせず、戦闘側（{@code GameRuntime/Battle}）の
 * 実行時オブジェクトが本定義を参照して保持する。
 *
 * <p>Task 6 ではコード生成のサンプル定義を描画に用いる。Task 15/16 で本型を正式化し、
 * 供給元を {@code Shared/Schema} の JSON ローダへ差し替える（フィールドは LibGDX {@code Json}
 * が無引数コンストラクタ + リフレクションで設定できるよう非 final にしてある）。
 *
 * @see <a href="../../../../../../docs/DataFormat.md">docs/DataFormat.md</a>
 */
public class Character {

    private String id;
    private String name;
    private int hp;
    private float walkSpeed;
    private float jumpPower;
    private float width;
    private float height;
    /** 表示色 RGB（0..1, 任意）。スプライト導入までのプレースホルダ矩形色（Task 22）。未設定なら描画側の既定色。 */
    private float[] color;
    /** 通常攻撃の技定義（Task 11）。MVP は 1 キャラ 1 技。Task 15/16 で JSON の moves[] から供給。 */
    private Move normalAttack;
    /** 必殺技の技定義（Task 20）。コマンド（波動拳）で発動。MVP は飛び道具 1 種。未設定可（null）。 */
    private Move specialMove;

    /** JSON デシリアライズ（Task 16）用の無引数コンストラクタ。 */
    public Character() {
    }

    public Character(String id, String name, int hp, float walkSpeed, float jumpPower,
                     float width, float height) {
        this.id = id;
        this.name = name;
        this.hp = hp;
        this.walkSpeed = walkSpeed;
        this.jumpPower = jumpPower;
        this.width = width;
        this.height = height;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getHp() {
        return hp;
    }

    public float getWalkSpeed() {
        return walkSpeed;
    }

    public float getJumpPower() {
        return jumpPower;
    }

    /** 描画 / 当たり判定に用いる横幅（px）。 */
    public float getWidth() {
        return width;
    }

    /** 描画 / 当たり判定に用いる高さ（px）。 */
    public float getHeight() {
        return height;
    }

    /** 表示色 RGB（0..1, 長さ 3）。未設定なら {@code null}（描画側の既定色を使う）。 */
    public float[] getColor() {
        return color;
    }

    /** 通常攻撃の技定義（未設定なら {@code null}）。 */
    public Move getNormalAttack() {
        return normalAttack;
    }

    /** 通常攻撃の技定義を設定する（Task 11 はコード生成で設定。Task 16 で JSON ローダが設定）。 */
    public void setNormalAttack(Move normalAttack) {
        this.normalAttack = normalAttack;
    }

    /** 必殺技の技定義（未設定なら {@code null}）。 */
    public Move getSpecialMove() {
        return specialMove;
    }

    public void setSpecialMove(Move specialMove) {
        this.specialMove = specialMove;
    }
}
