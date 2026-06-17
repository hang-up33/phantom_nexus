#!/usr/bin/env bash
# make-stage-background.sh
# 1枚絵（PNG/JPG）から Phantom Nexus 用のステージ全画面背景（1280×720）を生成する。
# 外部デザインツール（ClaudeDesign / Canva 等）で作ったステージアートを、ワールド解像度に
# 合わせて「カバー（縦横比維持で全面を埋め、はみ出しは中央基準でトリミング）」する。
# 出力 PNG を Assets/Stages/ に置き、対応する stageNNN.json の "background" に
# そのパス（例 "Stages/stage011_bg.png"）を書けば背景として表示される。
#
# 使い方:
#   scripts/make-stage-background.sh <入力画像> <出力先.png>
#   例:
#   scripts/make-stage-background.sh my_stage.png Assets/Stages/stage011_bg.png
#
# 詳細は docs/StageDesignSpec.md を参照。

set -e

INPUT="$1"
OUTPUT="$2"

if [ -z "$INPUT" ] || [ -z "$OUTPUT" ]; then
    echo "使い方: $0 <入力画像> <出力先.png>"
    exit 1
fi

if [ ! -f "$INPUT" ]; then
    echo "エラー: 入力ファイルが見つかりません: $INPUT"
    exit 1
fi

# Java ソースを /tmp に書き出して実行（PIL 等の追加依存なしで動く）
JAVA_SRC="/tmp/MakeStageBackground.java"
cat > "$JAVA_SRC" << 'JAVA_EOF'
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class MakeStageBackground {
    // Phantom Nexus のワールド解像度（GameConstants.WORLD_WIDTH/HEIGHT）。
    static final int OUT_W = 1280;
    static final int OUT_H = 720;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        if (args.length < 2) {
            System.err.println("Usage: MakeStageBackground <input> <output.png>");
            System.exit(1);
        }
        File inputFile  = new File(args[0]);
        File outputFile = new File(args[1]);

        BufferedImage src = ImageIO.read(inputFile);
        if (src == null) {
            System.err.println("読み込み失敗: " + inputFile);
            System.exit(1);
        }

        // 1280×720 にカバー（縦横比維持で全面を埋め、はみ出しは中央でトリミング）。
        BufferedImage out = resizeCover(src, OUT_W, OUT_H);

        outputFile.getParentFile().mkdirs();
        ImageIO.write(out, "PNG", outputFile);
        System.out.println("生成完了: " + outputFile.getAbsolutePath()
            + " (" + OUT_W + "×" + OUT_H + ")");
    }

    /** 縦横比を維持しつつ targetW×targetH を完全に覆うようスケールし、中央基準でトリミングする。 */
    static BufferedImage resizeCover(BufferedImage src, int targetW, int targetH) {
        double scaleX = (double) targetW / src.getWidth();
        double scaleY = (double) targetH / src.getHeight();
        double scale  = Math.max(scaleX, scaleY); // 大きい方＝全面を覆う
        int scaledW = (int) Math.ceil(src.getWidth()  * scale);
        int scaledH = (int) Math.ceil(src.getHeight() * scale);
        int offX = (targetW - scaledW) / 2; // 中央寄せ（負＝左右はみ出しをトリミング）
        int offY = (targetH - scaledH) / 2;

        BufferedImage result = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, offX, offY, scaledW, scaledH, null);
        g.dispose();
        return result;
    }
}
JAVA_EOF

# コンパイル & 実行
javac -d /tmp "$JAVA_SRC"
java -Djava.awt.headless=true -cp /tmp MakeStageBackground "$INPUT" "$OUTPUT"
