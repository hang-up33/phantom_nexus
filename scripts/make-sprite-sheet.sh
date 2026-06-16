#!/usr/bin/env bash
# make-sprite-sheet.sh
# 1枚絵（PNG）から Phantom Nexus 用スプライトシート（256×896・64×128×4列×7行）を生成する。
# 全 7 状態 × 4 フレームに同じ絵を敷き詰め、アニメなしの「静止スプライト」として機能する。
# アニメーションを作り込む場合は SpriteDesignSpec.md を参照して手動でシートを編集すること。
#
# 使い方:
#   scripts/make-sprite-sheet.sh <入力画像.png> <出力先.png>
#   例:
#   scripts/make-sprite-sheet.sh my_char.png Assets/Characters/fighter001.png

set -e

INPUT="$1"
OUTPUT="$2"

if [ -z "$INPUT" ] || [ -z "$OUTPUT" ]; then
    echo "使い方: $0 <入力画像.png> <出力先.png>"
    exit 1
fi

if [ ! -f "$INPUT" ]; then
    echo "エラー: 入力ファイルが見つかりません: $INPUT"
    exit 1
fi

# Java ソースを /tmp に書き出して実行
JAVA_SRC="/tmp/MakeSpriteSheet.java"
cat > "$JAVA_SRC" << 'JAVA_EOF'
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class MakeSpriteSheet {
    static final int CELL_W = 64;
    static final int CELL_H = 128;
    static final int COLS   = 4;
    static final int ROWS   = 7;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        if (args.length < 2) {
            System.err.println("Usage: MakeSpriteSheet <input.png> <output.png>");
            System.exit(1);
        }
        File inputFile  = new File(args[0]);
        File outputFile = new File(args[1]);

        BufferedImage src = ImageIO.read(inputFile);
        if (src == null) {
            System.err.println("読み込み失敗: " + inputFile);
            System.exit(1);
        }

        // 1 セルサイズにリサイズ（アスペクト比維持で透過セルにレターボックス）
        BufferedImage cell = resizeFit(src, CELL_W, CELL_H);

        // シート全体を透過 PNG で作成
        int sheetW = CELL_W * COLS;
        int sheetH = CELL_H * ROWS;
        BufferedImage sheet = new BufferedImage(sheetW, sheetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                g.drawImage(cell, col * CELL_W, row * CELL_H, null);
            }
        }
        g.dispose();

        outputFile.getParentFile().mkdirs();
        ImageIO.write(sheet, "PNG", outputFile);
        System.out.println("生成完了: " + outputFile.getAbsolutePath()
            + " (" + sheetW + "×" + sheetH + ")");
    }

    static BufferedImage resizeFit(BufferedImage src, int targetW, int targetH) {
        double scaleX = (double) targetW / src.getWidth();
        double scaleY = (double) targetH / src.getHeight();
        double scale  = Math.min(scaleX, scaleY);
        int fitW = (int) (src.getWidth()  * scale);
        int fitH = (int) (src.getHeight() * scale);
        int offX = (targetW - fitW) / 2;
        int offY = (targetH - fitH) / 2;

        BufferedImage result = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, offX, offY, fitW, fitH, null);
        g.dispose();
        return result;
    }
}
JAVA_EOF

# コンパイル & 実行
javac -d /tmp "$JAVA_SRC"
java -Djava.awt.headless=true -cp /tmp MakeSpriteSheet "$INPUT" "$OUTPUT"
