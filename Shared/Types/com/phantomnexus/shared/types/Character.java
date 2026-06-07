package com.phantomnexus.shared.types;

/**
 * キャラクターの静的定義（データの単一の真実）。
 *
 * <p>外部 JSON（{@code Assets/Characters/<id>.json}）から読み込まれる静的属性を表す POJO。
 * 位置・速度・現在 HP などの実行時状態は本クラスには持たせず、戦闘側（{@code GameRuntime/Battle}）の
 * 実行時オブジェクトが本定義を参照して保持する。
 *
 * <p>Task 24 で技定義を配列（{@code normalMoves[]} / {@code specialMoves[]}）に拡張した。
 * 通常技はボタン種別（"light"/"medium"/"heavy"）、必殺技はコマンド名（"HADOUKEN" 等）で識別する。
 * Task 24 以前の旧形式フィールド（{@code normalAttack} / {@code specialMove}）は後方互換マイグレーション
 * のため {@code CharacterLoader} が読み取る専用フィールドとして保持する（ゲームロジックからは参照しない）。
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
    /** 表示色 RGB（0..1, 任意）。スプライト導入までのプレースホルダ矩形色。未設定なら描画側の既定色。 */
    private float[] color;
    /** 通常技の技定義配列（弱 / 中 / 強ボタン対応）。1 件以上必須。 */
    private Move[] normalMoves;
    /** 必殺技の技定義配列（コマンド対応）。省略可（null / 空）。 */
    private Move[] specialMoves;
    /**
     * 旧形式互換フィールド（Task 24 以前）。LibGDX Json がデシリアライズ後に
     * {@code CharacterLoader.migrateIfLegacy()} が {@code normalMoves} へ移行し、ゲームロジックは参照しない。
     */
    private Move normalAttack;
    /** 旧形式互換フィールド（Task 24 以前）。{@link #normalAttack} と同様の用途。 */
    private Move specialMove;

    /** JSON デシリアライズ用の無引数コンストラクタ。 */
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

    /** 通常技の技定義配列（null 非許容、length ≥ 1）。 */
    public Move[] getNormalMoves() {
        return normalMoves;
    }

    /** {@code CharacterLoader} が旧形式から移行後に配列を注入する。 */
    public void setNormalMoves(Move[] normalMoves) {
        this.normalMoves = normalMoves;
    }

    /** 必殺技の技定義配列（null / 空配列 = 必殺技なし）。 */
    public Move[] getSpecialMoves() {
        return specialMoves;
    }

    /** {@code CharacterLoader} が旧形式から移行後に配列を注入する。 */
    public void setSpecialMoves(Move[] specialMoves) {
        this.specialMoves = specialMoves;
    }

    /** 旧形式互換：{@code CharacterLoader.migrateIfLegacy()} が使用する。ゲームロジックから呼ばない。 */
    public Move legacyNormalAttack() {
        return normalAttack;
    }

    /** 旧形式互換：{@code CharacterLoader.migrateIfLegacy()} が使用する。ゲームロジックから呼ばない。 */
    public Move legacySpecialMove() {
        return specialMove;
    }
}
