package com.phantomnexus.shared.types;

import java.util.Locale;

/**
 * 通常技のボタン種別（Task 24 / リファクタで列挙化）。弱（light）/ 中（medium）/ 強（heavy）の 3 ボタンを表す。
 *
 * <p>従来は {@code "light"} / {@code "medium"} / {@code "heavy"} の生文字列を {@link Move}・
 * {@code CharacterLoader}・{@code Fighter}・{@code PhantomNexusGame}・{@code AiController} の各所で
 * 個別に構築・照合・検証していた。正準値と意味が複数箇所へ散在していたため、<b>データ型の単一の真実</b>
 * （CLAUDE.md コア原則）として本列挙へ集約する（{@code GuardHeight} と同じパターン）。
 * JSON 上の表現（小文字トークン）は不変で、後方互換も維持する。
 *
 * <p>{@code guardHeight}（任意・未指定は既定値）と異なり、{@code button} は通常技の<b>必須</b>フィールド
 * のため既定値を持たない。未指定（null / 空白）は {@link #fromToken(String)} が {@code null} を返し、
 * ローダの必須チェックが弾く。
 *
 * @see <a href="../../../../../../docs/DataFormat.md">docs/DataFormat.md</a>
 */
public enum AttackButton {

    /** 弱攻撃（P1: F / P2: Numpad 1）。 */
    LIGHT,
    /** 中攻撃（P1: G / P2: Numpad 2）。 */
    MEDIUM,
    /** 強攻撃（P1: H / P2: Numpad 3）。 */
    HEAVY;

    /**
     * JSON トークン（{@code "light"} / {@code "medium"} / {@code "heavy"}、大文字小文字無視・前後空白許容）を
     * 列挙へ正規化する。
     *
     * <ul>
     *   <li>{@code null} / 空白 → {@code null}（必須フィールドのため既定値なし。ローダの必須チェックが弾く）。</li>
     *   <li>既知トークン → 対応する定数。</li>
     *   <li>未知トークン（例 {@code "punch"}）→ {@code null}（不正値のシグナル。検証側が弾く）。</li>
     * </ul>
     *
     * @param token JSON に書かれた生のトークン（未正規化）
     * @return 対応する {@link AttackButton}、未指定・未知トークンなら {@code null}
     */
    public static AttackButton fromToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "light":
                return LIGHT;
            case "medium":
                return MEDIUM;
            case "heavy":
                return HEAVY;
            default:
                return null;
        }
    }
}
