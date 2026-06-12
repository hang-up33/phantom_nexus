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
    /**
     * 空中での追加ジャンプ回数（二段ジャンプ・任意, 既定 0, Task 68）。0 ならそのキャラは空中ジャンプを持たない（後方互換）。
     * 1 なら地上ジャンプ後に空中でもう一度ジャンプできる（機動型キャラの差別化）。接地で回数が回復する。
     */
    private int airJumps;
    /**
     * 空中ダッシュの回数（air dash・任意, 既定 0, Task 69）。0 ならそのキャラは空中ダッシュを持たない（後方互換）。
     * 1 なら滞空中に方向二度押しで水平ダッシュ（バーストで前/後へ滑空）できる（接地で回復）。機動型キャラの差別化。
     */
    private int airDashes;
    /** 表示色 RGB（0..1, 任意）。スプライト未指定時のプレースホルダ矩形色。未設定なら描画側の既定色。 */
    private float[] color;
    /** スプライト（描画用画像）定義（任意）。未設定なら従来どおりプレースホルダ矩形で描画する（Task 34）。 */
    private Sprite sprite;
    /** 通常技の技定義配列（弱 / 中 / 強ボタン対応）。1 件以上必須。 */
    private Move[] normalMoves;
    /** 必殺技の技定義配列（コマンド対応）。省略可（null / 空）。 */
    private Move[] specialMoves;
    /**
     * 投げ技の技定義（任意）。ガード不能の近接掴み（Task 35）。未設定（{@code null}）ならそのキャラは投げを持たない
     * （後方互換）。ボタン / コマンド / ガード高さは不要（投げ専用の発動経路 = 投げボタンで起動し、ガードを無視する）。
     * 再利用する {@link Move} の damage / startup / active / recovery / hitbox 矩形が「掴み判定（grab box）」を表す。
     */
    private Move throwMove;
    /**
     * ダッシュ攻撃の技定義（任意）。ダッシュ（二度押しステップ・Task 49）中に攻撃ボタンを押すと出る突進攻撃（Task 65）。
     * 未設定（{@code null}）ならそのキャラはダッシュ攻撃を持たず、ダッシュ中の攻撃は従来どおり通常技へキャンセルされる
     * （後方互換）。通常技と同じく {@code button} / {@code guardHeight} / hitbox を持つが、発動はダッシュ中の攻撃入力に限る。
     */
    private Move dashAttack;
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

    /** 空中での追加ジャンプ回数（二段ジャンプ・既定 0＝空中ジャンプなし、Task 68）。負値は 0 に丸める。 */
    public int getAirJumps() {
        return Math.max(0, airJumps);
    }

    /** 空中ダッシュの回数（air dash・既定 0＝空中ダッシュなし、Task 69）。負値は 0 に丸める。 */
    public int getAirDashes() {
        return Math.max(0, airDashes);
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

    /** スプライト定義（描画用画像）。未設定なら {@code null}（描画側はプレースホルダ矩形を使う。Task 34）。 */
    public Sprite getSprite() {
        return sprite;
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

    /** 投げ技の定義（ガード不能の近接掴み）。未設定なら {@code null}（そのキャラは投げを持たない。Task 35）。 */
    public Move getThrowMove() {
        return throwMove;
    }

    /** ダッシュ攻撃の定義（ダッシュ中の攻撃で出る突進技）。未設定なら {@code null}（そのキャラはダッシュ攻撃を持たない。Task 65）。 */
    public Move getDashAttack() {
        return dashAttack;
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
