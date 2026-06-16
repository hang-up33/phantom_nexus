package com.phantomnexus.runtime.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;

/**
 * SE の読み込みと再生を管理する。
 *
 * <p>Assets/Sounds/ 以下の WAV ファイルを起動時に一括ロードし、
 * ゲームロジックから playXxx() を呼ぶだけで再生できるようにする。
 * ロード失敗（ヘッドレス環境・ファイル欠落）は警告のみで続行し、
 * 以降の再生呼び出しは静かに無視される（null-safe）。
 *
 * <p>M キーでトグル可能（{@link #toggle()}）。
 */
public class SoundManager {

    private final Sound hitLight;
    private final Sound hitMedium;
    private final Sound hitHeavy;
    private final Sound guard;
    private final Sound special;
    private final Sound throwSe;
    private final Sound ko;
    private final Sound roundStart;

    private boolean enabled = true;

    public SoundManager() {
        hitLight   = load("Sounds/hit_light.wav");
        hitMedium  = load("Sounds/hit_medium.wav");
        hitHeavy   = load("Sounds/hit_heavy.wav");
        guard      = load("Sounds/guard.wav");
        special    = load("Sounds/special.wav");
        throwSe    = load("Sounds/throw.wav");
        ko         = load("Sounds/ko.wav");
        roundStart = load("Sounds/round_start.wav");
    }

    private static Sound load(String path) {
        try {
            return Gdx.audio.newSound(Gdx.files.classpath(path));
        } catch (Exception e) {
            Gdx.app.log("SoundManager", "SE ロード失敗（無音で続行）: " + path);
            return null;
        }
    }

    /** 弱攻撃ヒット音 */
    public void playHitLight()  { play(hitLight,   1.0f); }

    /** 中攻撃ヒット音 */
    public void playHitMedium() { play(hitMedium,  1.0f); }

    /** 強攻撃・必殺技ヒット音 */
    public void playHitHeavy()  { play(hitHeavy,   1.0f); }

    /** ガード音（chip ダメージ）*/
    public void playGuard()     { play(guard,       0.8f); }

    /** 必殺技・飛び道具発射音 */
    public void playSpecial()   { play(special,     1.0f); }

    /** 投げヒット音 */
    public void playThrow()     { play(throwSe,     1.0f); }

    /** KO 決着音 */
    public void playKO()        { play(ko,          1.0f); }

    /** ラウンド開始（"FIGHT!"）音 */
    public void playRoundStart() { play(roundStart, 0.9f); }

    /** M キーで SE 全体をミュート/アンミュートする */
    public void toggle() {
        enabled = !enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void dispose() {
        disposeSound(hitLight);
        disposeSound(hitMedium);
        disposeSound(hitHeavy);
        disposeSound(guard);
        disposeSound(special);
        disposeSound(throwSe);
        disposeSound(ko);
        disposeSound(roundStart);
    }

    private void play(Sound s, float volume) {
        if (enabled && s != null) {
            s.play(volume);
        }
    }

    private static void disposeSound(Sound s) {
        if (s != null) {
            s.dispose();
        }
    }
}
