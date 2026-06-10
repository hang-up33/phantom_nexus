package com.phantomnexus.shared.types;

import java.util.Locale;

/**
 * ガード高さ属性（Task 33 / リファクタで列挙化）。技を「どのガードで防げるか」を表す。
 *
 * <p>従来は {@code "overhead"} / {@code "mid"} / {@code "low"} の生文字列を {@link Move}・
 * {@code CharacterLoader}・{@code PhantomNexusGame} の各所で個別に正規化・検証・分岐していた。
 * 正準値と意味が複数箇所へ散在していたため、<b>データ型の単一の真実</b>（CLAUDE.md コア原則）として
 * 本列挙へ集約する。JSON 上の表現（小文字トークン）は不変で、後方互換も維持する。
 *
 * <ul>
 *   <li>{@link #OVERHEAD}（上段）：立ちガードのみ成立・しゃがみガードでは防げない。</li>
 *   <li>{@link #MID}（中段・既定）：立ち / しゃがみどちらのガードでも成立。</li>
 *   <li>{@link #LOW}（下段）：しゃがみガードのみ成立・立ちガードでは防げない。</li>
 * </ul>
 *
 * @see <a href="../../../../../../docs/DataFormat.md">docs/DataFormat.md</a>
 * @see <a href="../../../../../../docs/BattleSystem.md">docs/BattleSystem.md</a>
 */
public enum GuardHeight {

    /** 上段：立ちガードのみ成立（しゃがみガード貫通）。 */
    OVERHEAD,
    /** 中段（既定）：立ち / しゃがみどちらのガードでも成立。 */
    MID,
    /** 下段：しゃがみガードのみ成立（立ちガード貫通）。 */
    LOW;

    /** 未指定（旧 JSON はキーを持たない）の既定値（中段）。 */
    public static final GuardHeight DEFAULT = MID;

    /**
     * JSON トークン（{@code "overhead"} / {@code "mid"} / {@code "low"}、大文字小文字無視・前後空白許容）を
     * 列挙へ正規化する。
     *
     * <ul>
     *   <li>{@code null} / 空白 → {@link #DEFAULT}（後方互換：旧 JSON はキーが無いため既定の中段）。</li>
     *   <li>既知トークン → 対応する定数。</li>
     *   <li>未知トークン（例 {@code "high"}）→ {@code null}（不正値のシグナル。検証側が弾く）。</li>
     * </ul>
     *
     * @param token JSON に書かれた生のトークン（未正規化）
     * @return 対応する {@link GuardHeight}、未指定なら {@link #DEFAULT}、未知トークンなら {@code null}
     */
    public static GuardHeight fromToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return DEFAULT;
        }
        switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "overhead":
                return OVERHEAD;
            case "mid":
                return MID;
            case "low":
                return LOW;
            default:
                return null;
        }
    }
}
