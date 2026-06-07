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
}
