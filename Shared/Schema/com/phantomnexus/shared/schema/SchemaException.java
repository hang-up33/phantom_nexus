package com.phantomnexus.shared.schema;

/**
 * 外部データ（JSON）の読み込み / バリデーション失敗を表す例外（Task 16）。
 *
 * <p>どのファイル / フィールドが原因かをメッセージに含めて投げる（[docs/DataFormat.md](../../../../../../docs/DataFormat.md)
 * 「バリデーション方針」）。データ不整合はフェイルファストで検知し、原因を明確にログ表示する。
 */
public class SchemaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SchemaException(String message) {
        super(message);
    }

    public SchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
