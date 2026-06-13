package com.phantomnexus.shared.types;

/**
 * 技（攻撃）の静的定義（データの単一の真実）。
 *
 * <p>キャラクターが持つ技 1 つ分の定義。攻撃は <b>startup / active / recovery</b> の 3 区間を持ち、
 * {@code active} 区間のみ攻撃判定（hitbox）が有効になる（[docs/BattleSystem.md](../../../../../../docs/BattleSystem.md)）。
 * フレーム値はすべて 60fps 固定ステップ基準のフレーム数。hitbox の矩形（オフセット / 幅 / 高さ）は
 * キャラの「前方・足元」を原点とする相対座標で持ち、向きに応じて左右反転して使う（当たり判定は Task 12）。
 *
 * <p>Task 11（攻撃処理）で startup/active/recovery を、Task 12（当たり判定）で hitbox 矩形を、
 * Task 13（ダメージ処理）で {@code damage} を用いる。供給元は Task 16 以降 {@code Shared/Schema} の
 * JSON ローダ（{@code CharacterLoader}）で、LibGDX {@code Json} がリフレクションで設定できるよう
 * 全フィールド非 final・無引数コンストラクタを備える。
 *
 * @see <a href="../../../../../../docs/DataFormat.md">docs/DataFormat.md</a>
 */
public class Move {

    private String id;
    /**
     * 通常技：ボタン種別の JSON 生トークン（{@code "light"} / {@code "medium"} / {@code "heavy"}）。
     * 正準値と意味は {@link AttackButton} に集約し、本フィールドは LibGDX {@code Json} が書き込む生値を保持する。
     * 必殺技：コマンド名（{@link com.phantomnexus.runtime.input.Command} の name。例 "HADOUKEN"）は
     * {@code command} フィールドを使う。歴史的互換フィールド {@code command} を必殺技向けに転用し、
     * {@code button} を新設（Task 24）。値の解釈・検証は {@link AttackButton#fromToken(String)}。
     */
    private String button;
    private String command;   // 必殺技のコマンド名（Command.name() と照合）
    private int damage;
    private int startup;      // 発生（攻撃判定が出るまで）のフレーム数
    private int active;       // 攻撃判定が有効なフレーム数
    private int recovery;     // 技後の硬直フレーム数
    // hitbox 矩形：キャラの「前方の前面・足元」を原点とする相対座標（px）。向きで左右反転（Task 12）。
    private float hitboxOffsetX;
    private float hitboxOffsetY;
    private float hitboxWidth;
    private float hitboxHeight;
    // 必殺技（Task 20）：飛び道具として発射するか・その速度（px/frame, 前方）。通常技は false/0。
    private boolean projectile;
    private float projectileSpeed;
    /**
     * 無敵フレーム数（Task 53）。技の発生から数えてこのフレーム数だけ、この技を出している側が
     * <b>食らい判定（hurtbox）を失う</b>（被弾・被弾飛び道具を無効化＝リバーサル / 対空）。0（既定）＝無敵なし。
     * 旧 JSON（キー無し）は初期化子の 0 を保ち後方互換。打撃必殺技（{@code projectile=false}）に付けると
     * 昇龍拳タイプの無敵対空になる。{@code attackFrame <= invincibleFrames} の間だけ無敵（{@link com.phantomnexus.shared.types.Move} の
     * 値を {@code Fighter.isInvincible()} が参照）。値は技の全長を超えてもよい（全体無敵）。
     */
    private int invincibleFrames = 0;
    /**
     * ダウン技か（Task 60）。{@code true} の技を非ガードで食らうと、相手は通常のけぞりではなく
     * <b>ダウン</b>（{@link com.phantomnexus.shared.constants.GameConstants#KNOCKDOWN_FRAMES} の行動不能＋ダウン中無敵）に陥る。
     * 任意フィールドで既定 {@code false}（旧 JSON はキー無しで {@code false}＝後方互換＝通常のけぞり）。
     * 飛び道具のダウンは将来対応で、現状は打撃ヒット（{@code resolveHit}）でのみ参照する。
     */
    private boolean knockdown = false;
    /**
     * 多段ヒット数（Task 74）。この技の active 区間中に最大何回ヒットさせるか。既定 1（単発・後方互換）。
     * 2 以上にすると、active 中に {@link #hitGap} フレーム間隔で複数回ヒットする多段技になる（各サブヒットは
     * のけぞり中の相手にコンボとして加算され、コンボダメージ補正＝Task 46 が乗る）。旧 JSON（キー無し）は 1。
     */
    private int hits = 1;
    /**
     * 多段ヒットのサブヒット間隔（フレーム数・Task 74）。{@link #hits} が 2 以上のとき、1 回ヒットしてから
     * 次のヒットを許可するまでの待機フレーム。既定 4。{@code hits == 1}（単発）では無視される。
     */
    private int hitGap = 4;
    /**
     * スーパーアーマー数（Task 80）。技の <b>startup 中</b>に、被弾しても<b>のけぞらず</b>に技を継続できる回数。
     * 既定 0＝アーマーなし（後方互換）。ダメージは受けるが hitstun に入らない＝強気の差し込み / 切り返しに使う。
     * 投げ（ガード不能）はアーマーを貫通する。旧 JSON（キー無し）は 0。
     */
    private int armorHits = 0;
    /**
     * 浮かせ（launch・Task 83）の上方初速（px/frame）。{@code > 0} の技を非ガードでヒットさせると、相手を
     * その初速で<b>打ち上げて空中やられ</b>にする（空中コンボ＝ジャグルの起点）。打ち上がった相手はのけぞり中で
     * 無防備＝追撃可能。既定 0＝打ち上げなし（後方互換）。ダウン技（{@code knockdown}）とは排他（ダウンが優先）。
     */
    private float launch = 0f;
    /**
     * OTG（off-the-ground・追い打ち・Task 85）。{@code true} の技は<b>ダウン中（Task 60）の相手にも当たる</b>
     * （通常はダウン中無敵で当たらない）。倒れた相手への追撃を一部の技に許して起き攻め / コンボの幅を足す。
     * 既定 {@code false}（後方互換＝ダウン中無敵を貫通しない）。無敵リバーサル（{@code invincibleFrames}）中の相手は貫通しない。
     */
    private boolean otg = false;
    /**
     * 受け身不能ダウン（hard knockdown・Task 88）。{@code knockdown=true} の技にさらにこれを付けると、
     * 食らった相手は受け身（ukemi・Task 66）でクイック起き上がりできず、必ずフルダウン（`KNOCKDOWN_FRAMES`）する＝
     * 起き攻めが確定する。既定 {@code false}（後方互換＝通常ダウンは受け身可能）。{@code knockdown=false} の技では無意味。
     */
    private boolean hardKnockdown = false;
    /**
     * 投げ抜け不能（command throw・Task 94）。投げ技（{@code throwMove}/{@code airThrowMove}）にこれを付けると、
     * 相手が投げ抜け（throw tech・Task 36）入力をしていても抜けられない＝確定の掴み（グラップラーの代名詞）。
     * 既定 {@code false}（後方互換＝通常の投げは抜け可能）。打撃 / 飛び道具では無意味（投げ成立判定でのみ参照）。
     */
    private boolean noTech = false;
    /**
     * 壁バウンド（wall bounce・Task 101）。{@code true} の技を非ガードでヒットさせると、相手を強い水平初速で
     * <b>横方向に吹き飛ばし</b>、画面端（壁）に到達すると<b>跳ね返って再び浮く</b>＝画面端ジャグルの延長点になる。
     * のけぞり中＝無防備なので、跳ね返り際を追撃できる。既定 {@code false}（後方互換）。ダウン技（{@code knockdown}）とは
     * 排他（ダウンが優先）。浮かせ（{@code launch}）より優先して解決する。
     */
    private boolean wallBounce = false;
    /**
     * ガード高さ属性（Task 33）の JSON 生トークン（{@code "overhead"} / {@code "mid"} / {@code "low"}）。
     * 正準値と意味は {@link GuardHeight} に集約し、本フィールドは LibGDX {@code Json} が書き込む生値を保持する。
     * 未指定（旧 JSON はキー無し）はフィールド初期化子の {@code "mid"} を保つ（後方互換）。なお、しゃがみ中に
     * 出した通常技は状態により下段（low）として解決される（{@code Fighter.isCrouchAttacking()} / Task 31）ため、
     * 本属性は主に立ち技の overhead / mid を区別する用途に使う。値の解釈・検証は {@link GuardHeight#fromToken(String)}。
     */
    private String guardHeight = "mid";

    /** JSON デシリアライズ（Task 16）用の無引数コンストラクタ。 */
    public Move() {
    }

    public Move(String id, String command, int damage, int startup, int active, int recovery,
                float hitboxOffsetX, float hitboxOffsetY, float hitboxWidth, float hitboxHeight) {
        this.id = id;
        this.command = command;
        this.damage = damage;
        this.startup = startup;
        this.active = active;
        this.recovery = recovery;
        this.hitboxOffsetX = hitboxOffsetX;
        this.hitboxOffsetY = hitboxOffsetY;
        this.hitboxWidth = hitboxWidth;
        this.hitboxHeight = hitboxHeight;
    }

    public String getId() {
        return id;
    }

    /**
     * 通常技のボタン種別を {@link AttackButton} で返す。未指定・未知トークンは {@code null}
     * （検証済み JSON では通常技は非 null が保証される）。必殺技では {@link #getCommand()} を使う。
     */
    public AttackButton getButton() {
        return AttackButton.fromToken(button);
    }

    /**
     * JSON に書かれた生のボタントークン（未正規化・{@code null} あり得る）。
     * 必須・許可値の検証に用いる（{@code CharacterLoader} のみ使用）。実行時の照合には {@link #getButton()} を使う。
     */
    public String getButtonToken() {
        return button;
    }

    /** ローダが旧形式 JSON から移行する際にボタン種別を注入する（{@code CharacterLoader} のみ使用）。 */
    public void setButton(String button) {
        this.button = button;
    }

    public String getCommand() {
        return command;
    }

    /** ローダが旧形式 JSON から移行する際にコマンド名を正規化する（{@code CharacterLoader} のみ使用）。 */
    public void setCommand(String command) {
        this.command = command;
    }

    public int getDamage() {
        return damage;
    }

    public int getStartup() {
        return startup;
    }

    public int getActive() {
        return active;
    }

    public int getRecovery() {
        return recovery;
    }

    /** startup + active + recovery の総フレーム数（技の全長）。 */
    public int getTotalFrames() {
        return startup + active + recovery;
    }

    public float getHitboxOffsetX() {
        return hitboxOffsetX;
    }

    public float getHitboxOffsetY() {
        return hitboxOffsetY;
    }

    public float getHitboxWidth() {
        return hitboxWidth;
    }

    public float getHitboxHeight() {
        return hitboxHeight;
    }

    /** 飛び道具として発射する技か（Task 20: 必殺技ステート）。 */
    public boolean isProjectile() {
        return projectile;
    }

    /** 飛び道具の進行速度（px/frame, 前方）。{@link #isProjectile()} が true のとき有効。 */
    public float getProjectileSpeed() {
        return projectileSpeed;
    }

    /**
     * 無敵フレーム数（Task 53）。技発生からこのフレーム数だけ食らい判定を失う（リバーサル / 対空）。
     * 負値は 0 に丸める（後方互換・防御的）。0 ＝無敵なし。
     */
    public int getInvincibleFrames() {
        return Math.max(0, invincibleFrames);
    }

    /** ダウン技か（Task 60）。{@code true} の技を非ガードで食らうと相手はダウンする。既定 {@code false}（後方互換）。 */
    public boolean isKnockdown() {
        return knockdown;
    }

    /** 多段ヒット数（Task 74）。最小 1（単発）に丸める。2 以上で {@link #getHitGap()} 間隔の多段技になる。 */
    public int getHits() {
        return Math.max(1, hits);
    }

    /** 多段ヒットのサブヒット間隔（フレーム数・Task 74）。負値は 0 に丸める。{@link #getHits()} が 1 なら無視される。 */
    public int getHitGap() {
        return Math.max(0, hitGap);
    }

    /** スーパーアーマー数（Task 80）。startup 中に被弾してものけぞらず継続できる回数。負値は 0 に丸める（既定 0＝なし）。 */
    public int getArmorHits() {
        return Math.max(0, armorHits);
    }

    /** 浮かせ（launch・Task 83）の上方初速（px/frame）。`> 0` で相手を打ち上げて空中やられにする。負値は 0 に丸める（既定 0＝なし）。 */
    public float getLaunch() {
        return Math.max(0f, launch);
    }

    /** OTG（追い打ち・Task 85）か。{@code true} ならダウン中（Task 60）の相手にも当たる。既定 {@code false}（後方互換）。 */
    public boolean isOtg() {
        return otg;
    }

    /** 受け身不能ダウン（hard knockdown・Task 88）か。{@code true} なら食らった相手は受け身できずフルダウンする。既定 {@code false}。 */
    public boolean isHardKnockdown() {
        return hardKnockdown;
    }

    /** 投げ抜け不能（command throw・Task 94）か。{@code true} なら相手は投げ抜け（Task 36）できない確定の掴み。既定 {@code false}。 */
    public boolean isNoTech() {
        return noTech;
    }

    /** 壁バウンド（Task 101）か。{@code true} なら相手を横へ吹き飛ばし画面端で跳ね返らせる（画面端ジャグル延長）。既定 {@code false}（後方互換）。 */
    public boolean isWallBounce() {
        return wallBounce;
    }

    /**
     * ガード高さ属性（Task 33）を {@link GuardHeight} で返す。未指定・空文字は {@link GuardHeight#DEFAULT}
     * （中段）に正規化する（後方互換）。検証済み JSON では未知値は来ないが、防御的に未知値も既定へ丸める。
     */
    public GuardHeight getGuardHeight() {
        GuardHeight g = GuardHeight.fromToken(guardHeight);
        return g != null ? g : GuardHeight.DEFAULT;
    }

    /**
     * JSON に書かれた生のガード高さトークン（未正規化・{@code null} あり得る）。
     * 許可値か否かの検証に用いる（{@code CharacterLoader} のみ使用）。実行時の解釈には {@link #getGuardHeight()} を使う。
     */
    public String getGuardHeightToken() {
        return guardHeight;
    }
}
