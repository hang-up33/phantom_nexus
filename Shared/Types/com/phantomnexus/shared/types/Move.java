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
 * Task 13（ダメージ処理）で {@code damage} を用いる。供給元は MVP ではコード生成（Task 11）だが、
 * Task 15/16 で {@code Shared/Schema} の JSON ローダへ差し替える（LibGDX {@code Json} がリフレクションで
 * 設定できるよう全フィールド非 final・無引数コンストラクタを備える）。
 *
 * @see <a href="../../../../../../docs/DataFormat.md">docs/DataFormat.md</a>
 */
public class Move {

    private String id;
    private String command;   // 発生コマンド（MVP は単キー。Task 19 で波動拳等へ拡張）
    private int damage;
    private int startup;      // 発生（攻撃判定が出るまで）のフレーム数
    private int active;       // 攻撃判定が有効なフレーム数
    private int recovery;     // 技後の硬直フレーム数
    // hitbox 矩形：キャラの「前方の前面・足元」を原点とする相対座標（px）。向きで左右反転（Task 12）。
    private float hitboxOffsetX;
    private float hitboxOffsetY;
    private float hitboxWidth;
    private float hitboxHeight;

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

    public String getCommand() {
        return command;
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
}
