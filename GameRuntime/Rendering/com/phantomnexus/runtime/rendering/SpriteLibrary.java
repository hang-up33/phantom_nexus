package com.phantomnexus.runtime.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phantomnexus.shared.types.Character;
import com.phantomnexus.shared.types.Sprite;
import com.phantomnexus.shared.types.SpriteStateRow;

import java.util.HashMap;
import java.util.Map;

/**
 * スプライトシートの読み込み・フレーム切り出しキャッシュ（Task 34: スプライト描画）。
 *
 * <p>{@link Character#getSprite()} の {@link Sprite}（パス＋フレーム寸法＋状態→行マップ）を受け取り、
 * クラスパス上の PNG を {@link Texture} として 1 度だけ読み込んで {@link TextureRegion} の格子に切り分ける。
 * {@link GameRenderer} は毎フレーム {@link #region(Character, AnimationState, int)} を呼び、アニメーション
 * 状態（→行）と現在フレーム（→列）に対応する領域を引いて描画する。
 *
 * <p>データ（パス／レイアウト）の単一の真実は {@code Shared/Types.Sprite} 側にあり、本クラスは GPU
 * リソース（テクスチャ）と描画用の派生（領域分割・状態→行の索引）だけを担う。スプライト未指定や PNG
 * 欠落・読み込み失敗時は「描画不可（{@code null}）」を返し、呼び出し側はプレースホルダ矩形へフォールバックする。
 */
public class SpriteLibrary {

    /** 1 キャラ分の読み込み済みスプライト（テクスチャ＋切り出し済み領域＋状態→行索引）。 */
    private static final class Entry {
        final Texture texture;            // 読み込み成功時のみ非 null
        final TextureRegion[][] regions;  // [row][col]。texture が null なら null
        final Map<String, Integer> stateRow; // 状態ラベル（小文字）→ 行番号
        final boolean ready;

        Entry(Texture texture, TextureRegion[][] regions, Map<String, Integer> stateRow) {
            this.texture = texture;
            this.regions = regions;
            this.stateRow = stateRow;
            this.ready = texture != null && regions != null && regions.length > 0;
        }

        static Entry unavailable() {
            return new Entry(null, null, null);
        }
    }

    private final Map<String, Entry> byCharId = new HashMap<>();

    /**
     * キャラのスプライトを（未読込なら）読み込んでキャッシュする。テクスチャ生成は GL コンテキストを
     * 要するため描画スレッドから呼ぶこと。スプライト未指定・PNG 欠落・読み込み失敗時は「描画不可」を
     * キャッシュし、以降の {@link #isReady(Character)} は {@code false} を返す（矩形フォールバック）。
     */
    public void ensureLoaded(Character def) {
        if (def == null || byCharId.containsKey(def.getId())) {
            return;
        }
        byCharId.put(def.getId(), load(def));
    }

    private Entry load(Character def) {
        Sprite spec = def.getSprite();
        if (spec == null) {
            return Entry.unavailable();
        }
        String path = spec.getPath();
        FileHandle file = Gdx.files.classpath(path);
        if (!file.exists()) {
            Gdx.app.log("SpriteLibrary", "スプライト PNG が見つかりません（矩形にフォールバック）: " + path);
            return Entry.unavailable();
        }
        try {
            Texture texture = new Texture(file);
            // frameWidth×frameHeight の等間隔グリッドに切り分ける（端数は切り捨て）。
            TextureRegion[][] regions = TextureRegion.split(texture, spec.getFrameWidth(), spec.getFrameHeight());
            Map<String, Integer> stateRow = new HashMap<>();
            if (spec.getStateRows() != null) {
                for (SpriteStateRow r : spec.getStateRows()) {
                    if (r != null && r.getState() != null) {
                        stateRow.put(r.getState().trim().toLowerCase(), r.getRow());
                    }
                }
            }
            Gdx.app.log("SpriteLibrary", "スプライト読み込み成功: " + path
                    + " (" + regions.length + " 行 × " + (regions.length > 0 ? regions[0].length : 0) + " 列)");
            return new Entry(texture, regions, stateRow);
        } catch (RuntimeException e) {
            Gdx.app.log("SpriteLibrary", "スプライト読み込み失敗（矩形にフォールバック）: " + path + " (" + e.getMessage() + ")");
            return Entry.unavailable();
        }
    }

    /** このキャラがスプライト描画可能か（読み込み済みかつテクスチャ有効）。未読込なら先に {@link #ensureLoaded} を呼ぶこと。 */
    public boolean isReady(Character def) {
        if (def == null) {
            return false;
        }
        Entry e = byCharId.get(def.getId());
        return e != null && e.ready;
    }

    /**
     * 指定アニメーション状態・フレーム番号に対応する領域を返す。状態は {@code stateRows} で行に対応づけ
     * （未マップは行 0 = 待機）、フレームは列に対応づける。行・列はシートの範囲にクランプする。
     *
     * @return 描画する {@link TextureRegion}。描画不可（未読込・テクスチャ欠落）なら {@code null}
     */
    public TextureRegion region(Character def, AnimationState state, int frameIndex) {
        if (!isReady(def)) {
            return null;
        }
        Entry e = byCharId.get(def.getId());
        Integer mapped = e.stateRow.get(state.label());
        int row = mapped != null ? mapped : 0;
        row = clamp(row, e.regions.length);
        int cols = e.regions[row].length;
        int col = clamp(frameIndex, cols);
        return e.regions[row][col];
    }

    private static int clamp(int value, int size) {
        if (value < 0) {
            return 0;
        }
        return value >= size ? size - 1 : value;
    }

    /** 読み込んだ全テクスチャを解放する。 */
    public void dispose() {
        for (Entry e : byCharId.values()) {
            if (e.texture != null) {
                e.texture.dispose();
            }
        }
        byCharId.clear();
    }
}
