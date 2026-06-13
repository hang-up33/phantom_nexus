package com.phantomnexus.runtime.battle;

import com.phantomnexus.shared.constants.GameConstants;
import com.phantomnexus.shared.types.AttackButton;
import com.phantomnexus.shared.types.GuardHeight;
import com.phantomnexus.shared.types.Move;

/**
 * 簡易 AI（Task 21 → Task 37 で読み合い反応を追加）。1 体のファイターを状態ベースで操作する。
 *
 * <p>人間の {@code PlayerInput} の代わりに、相手との距離 <b>＋ 相手の現在状態</b>から行動を決めて
 * {@link Fighter#update(int, boolean, AttackButton, boolean, boolean)} を呼ぶ（入力源の差し替え）。
 * Task 21 の素朴な「近づいて、間合いに入ったら攻撃」に、Task 37 で 2 つ・Task 51 で 1 つの反応を足した：
 * <ul>
 *   <li><b>投げ抜け反応</b>（Task 51）：相手が<b>投げ中</b>で近接なら、投げ抜け猶予窓をアームして掴みを<b>投げ抜け</b>る（ノーダメージ）。</li>
 *   <li><b>ガード反応</b>（Task 37）：相手が打撃中（投げ以外）で間合い内なら、後退方向を保持して<b>ガード</b>する（chip のみで凌ぐ）。</li>
 *   <li><b>投げ崩し</b>（Task 37）：相手が<b>ガード中</b>で近接なら、ガード不能の<b>投げ</b>で崩す（打撃＝ガードされる相手への択）。</li>
 * </ul>
 * 判断は相手の観測可能な状態（{@link Fighter#isAttacking()} / {@link Fighter#isThrowing()} /
 * {@link Fighter#isGuarding()}）のみに基づき<b>乱数を使わない</b>（決定的＝入力リプレイと両立）。これにより
 * 「打撃＝ガード／ガード＝投げで崩す／投げ＝投げ抜け」の三すくみが CPU 戦でも成立する。さらに無敵対空（Task 55）・
 * 飛び込み（ジャンプ攻撃・Task 57）・<b>下段読みのしゃがみガード</b>（Task 63）・<b>飛び道具牽制</b>（zoner・Task 64）を
 * 備える（いずれも HARD のみ）。
 *
 * <p>状態（クールダウン）を持つため 1 体につき 1 インスタンス。判定に用いる距離は中心間距離。
 */
public final class AiController {

    /**
     * AI の難易度（Task 56）。実装済みの反応群を段階的に解放してプレイ感を変える。判断ロジック自体は同じで、
     * <b>どの反応を有効にするか</b>だけが変わる（乱数は増やさない＝決定的・入力リプレイと両立）。
     * <ul>
     *   <li>{@link #EASY}：反応なし。歩いて接近し間合いで通常攻撃するだけ（Task 21 の素の AI 相当）。</li>
     *   <li>{@link #NORMAL}：＋ <b>ガード反応 / 投げ崩し</b>（Task 37 の読み合い）。</li>
     *   <li>{@link #HARD}：＋ <b>投げ抜け（Task 51）/ ダッシュ接近（Task 50）/ 無敵対空（Task 55）/ 飛び込み（Task 57）/ 下段読みのしゃがみガード（Task 63）/ 飛び道具牽制（Task 64）</b>＝全反応。</li>
     * </ul>
     */
    public enum Difficulty {
        EASY, NORMAL, HARD;

        /** 小文字トークン（{@code "easy"} 等）から解決。未知・null は {@code null}（呼び手が既定へ丸める）。 */
        public static Difficulty fromToken(String token) {
            if (token == null) {
                return null;
            }
            switch (token.trim().toLowerCase()) {
                case "easy":   return EASY;
                case "normal": return NORMAL;
                case "hard":   return HARD;
                default:       return null;
            }
        }
    }

    /** この距離（中心間, px）以下で通常攻撃を試みる。通常攻撃の届く間合いより少し内側。 */
    private static final float ATTACK_RANGE = 150f;
    /** この距離（中心間, px）以下で相手の打撃に反応してガードする。攻撃間合いより少し広く取り、被弾前に盾を構える。 */
    private static final float GUARD_RANGE = 200f;
    /** この距離（中心間, px）以下でガード中の相手を投げで崩す。掴みの届く近接に限定する。 */
    private static final float THROW_RANGE = 130f;
    /**
     * この距離（中心間, px）以下で相手の投げに反応して投げ抜け窓をアームする（Task 51）。{@link #THROW_RANGE} より少し広く取り、
     * 掴みが成立する前（startup 中）に窓を立てて、active で掴まれた瞬間に抜けられるようにする。
     */
    private static final float THROW_TECH_RANGE = 160f;
    /** 攻撃 / 投げ後に次の能動行動まで空けるフレーム数（連打防止）。 */
    private static final int ATTACK_COOLDOWN = 45;
    /**
     * パリィ読み（Task 106・HARD のみ）の再発までのクールダウン（フレーム）。一度パリィを試みたらこの間は再試行しない＝
     * すべての打撃を弾く「壁」にせず、たまに差し込む「読み」に留める。クールダウン中の打撃は通常どおりガード反応で凌ぐ。
     */
    private static final int AI_PARRY_COOLDOWN = 90;
    /**
     * パリィ読み（Task 106）で前方を押し始める「active までの残り startup フレーム」のリード（Task 105 の PARRY_WINDOW=5 より
     * 小さくする）。active 着弾フレームで {@code forwardHeldFrames} が 1〜PARRY_WINDOW に収まるよう、直前 2 フレームから押し始める。
     */
    private static final int AI_PARRY_LEAD = 2;
    /**
     * この距離（中心間, px）より遠ければ歩きでなくダッシュ（二度押し前ステップ・Task 49）で素早く間合いを詰める（Task 50）。
     * {@link #ATTACK_RANGE} までの接近のうち、遠距離はダッシュ・近距離は歩きと使い分ける。
     */
    private static final float DASH_APPROACH_RANGE = 260f;
    /**
     * この距離（中心間, px）以下に<b>落ちてくる相手</b>（空中＋下降中）がいれば無敵対空（リバーサル）で落とす（Task 55）。
     * 無敵打撃必殺技を持つキャラのみ発動。対空 hitbox（縦長）が届く水平間合いに合わせる。
     */
    private static final float ANTI_AIR_RANGE = 170f;
    /**
     * 飛び込み（ジャンプ攻撃・Task 57）で空中から攻撃を出す水平間合い（中心間, px）。下降中にこの距離まで詰めたら
     * 空中攻撃（Task 32）を出す。jump_attack の hitbox が相手に届く近さに合わせる。
     */
    private static final float JUMP_IN_ATTACK_RANGE = 130f;

    private int cooldown;
    private int parryCooldown;          // パリィ読みの再発クールダウン（Task 106）
    private int parryHold;              // パリィ commit 中：前方を押し続ける残りフレーム（Task 106）
    /**
     * 飛び込み（ジャンプ攻撃・Task 57）を実行中か。地上から踏み切った瞬間に立て、着地で下ろす。空中の間は
     * 相手へ向かってドリフトし、下降中に間合いへ入ったら空中攻撃を出す（HARD のみ）。
     */
    private boolean jumpingIn;
    /**
     * 難易度（Task 56）。既定 {@link Difficulty#HARD}＝全反応有効で、Task 55 までの従来挙動と同一
     * （入力リプレイの決定性・既存スクショレシピを保つため既定は HARD）。
     */
    private Difficulty difficulty = Difficulty.HARD;
    /**
     * AI のダッシュ二度押しパターンの進行状態（Task 50）。Fighter のダッシュ検出は「同方向の押下エッジが受付窓内に 2 回」で
     * 成立するため、AI 側で 0=1 度目押下 → 1=ニュートラル（離す）→ 2=2 度目押下（発動）の 3 フレームを生成する。
     */
    private int dashTapStep;
    /**
     * AI がこのフレームに発射した飛び道具技（Task 64）。AI は Core の {@code updateFighterInput} を経由しないため、
     * 飛び道具の<b>弾生成だけ</b>は Core が {@link #control} 後に {@link #consumePendingProjectile()} を読んで
     * {@code spawnProjectile} する（打撃必殺技＝対空・Task 55 は Core 不要だが、飛び道具は弾生成のため Core 連携が要る）。
     * 読み取りで {@code null} に戻す（1 フレーム 1 発）。
     */
    private Move pendingProjectile;
    /**
     * 前フレームにダウン（Task 60）していたか（起き上がりリバーサル・Task 97 の検出用）。前フレーム down かつ
     * このフレーム行動可能＝「起き上がった瞬間」を一度だけ捉えて無敵リバーサルを置く。
     */
    private boolean wasKnockedDown;

    /** ラウンド間リセット（クールダウン・ダッシュ進行・保留中の弾を消去して次ラウンド開始時の行動可否を初期化する）。 */
    public void reset() {
        cooldown = 0;
        parryCooldown = 0;
        parryHold = 0;
        dashTapStep = 0;
        jumpingIn = false;
        pendingProjectile = null;
        wasKnockedDown = false;
    }

    /**
     * このフレームに AI が発射した飛び道具技を返し、内部状態をクリアする（Task 64）。Core が {@link #control} 直後に呼び、
     * 非 {@code null} なら {@code spawnProjectile} で弾を生成する（AI が飛び道具を撃たなかったフレームは {@code null}）。
     * 1 フレーム 1 発（読み取りで消費）。
     */
    public Move consumePendingProjectile() {
        Move m = pendingProjectile;
        pendingProjectile = null;
        return m;
    }

    /** 難易度を設定する（Task 56）。{@code null} は無視（既定 {@link Difficulty#HARD} を保つ）。 */
    public void setDifficulty(Difficulty d) {
        if (d != null) {
            difficulty = d;
        }
    }

    /**
     * 難易度を次段階へ循環させる（EASY → NORMAL → HARD → EASY…）（Task 78・実行時切替）。
     * Core が F3 押下で呼ぶ（通常プレイのみ。リプレイ記録/再生中は format 不変・決定性を保つため呼ばない）。
     */
    public void cycleDifficulty() {
        Difficulty[] all = Difficulty.values();
        difficulty = all[(difficulty.ordinal() + 1) % all.length];
    }

    /** 現在の難易度（HUD 表示・テスト用）。 */
    public Difficulty getDifficulty() {
        return difficulty;
    }

    /**
     * 1 フレーム分、AI の判断で {@code self} を操作する。
     *
     * <p>優先順：<b>無敵対空 ＞ 投げ抜け反応 ＞ ガード反応 ＞ 投げ崩し ＞ 接近 ＞ 通常攻撃</b>。相手の状態に反応する反応群を
     * 距離ベースの行動（接近 / 攻撃）より優先する。落ちてくる相手（空中）への無敵対空（Task 55）を最優先に置く。
     * 各反応は{@link #difficulty 難易度}（Task 56）で解放段階が決まる：対空 / 投げ抜け / ダッシュ接近 / 飛び込み（Task 57）/
     * 飛び道具牽制（Task 64）は HARD のみ、ガード反応 / 投げ崩しは NORMAL 以上。EASY は反応なし（接近＋通常攻撃のみ）。
     * 解放されない反応は分岐をスキップし、下位の接近 / 攻撃へ自然にフォールスルーする。遠距離は飛び道具牽制（持っていれば）→
     * クールダウン中はダッシュ接近、の順で評価する。飛び込み（ジャンプ攻撃）中は空中の振る舞い（ドリフト＋空中攻撃）を
     * 最優先の専用分岐が一手に引き受ける（地上反応は接地時のみ成立するため空中では自然に無効）。
     *
     * @param self     操作対象のファイター
     * @param opponent 相手（距離 / 状態判定の基準）
     */
    public void control(Fighter self, Fighter opponent) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (parryCooldown > 0) {
            parryCooldown--;
        }
        if (parryHold > 0) {
            parryHold--; // パリィ commit の残りフレームを減衰（Task 106）
        }
        // 起き上がりリバーサル（Task 97）検出用：前フレームの down 状態を退避してから今フレームの状態へ更新する
        // （ukemi の早期 return を跨いでも必ず更新されるよう、ここで先に行う）。
        boolean prevKnockedDown = wasKnockedDown;
        wasKnockedDown = self.isKnockedDown();
        // AI 受け身（ukemi・Task 75・HARD のみ）：ダウン中（行動不能）の間は毎フレーム行動入力を出し、受付窓
        // （Task 66・Fighter 側が持つ）内なら最早フレームでクイック起き上がりする＝起き攻めへの対抗。窓は AI から
        // 見えないが、Fighter が窓外の入力を無視するので「ダウン中は入力し続ける」だけで成立する。`!canStartAction()`
        // で起き上がり確定フレーム（ラッチで isKnockedDown が true でも canStartAction が回復するフレーム）を除外し、
        // そこで通常技が暴発しないようにする。乱数なし＝決定的（自分のダウン状態のみで判断・入力リプレイと両立）。
        if (difficulty == Difficulty.HARD && self.isKnockedDown() && !self.canStartAction()) {
            self.update(0, false, AttackButton.LIGHT, false, false);
            return;
        }
        float dx = opponent.getX() - self.getX();
        float distance = Math.abs(dx);
        int towardDir = dx >= 0 ? 1 : -1; // 相手の方向
        int backDir = -towardDir;          // 後退（ガード）方向
        boolean hasThrow = self.getDef().getThrowMove() != null;
        // 牽制に使える飛び道具（Task 64）。無ければ null＝そのキャラは AI 飛び道具を撃たない（grappler/charge 専用キャラ）。
        Move projectile = findProjectileMove(self);
        // 相手が打撃中か（投げはガード不能なのでガード反応の対象外）。
        boolean opponentStriking = opponent.isAttacking() && !opponent.isThrowing();

        int moveDir = 0;
        boolean attack = false;
        boolean throwReq = false;
        boolean jumpReq = false;
        boolean crouchGuard = false; // ガード反応時に下段読みでしゃがみガードへ切り替えるか（Task 63・HARD のみ）

        // 難易度（Task 56）でどの反応を解放するか。defends=ガード/投げ崩し（NORMAL 以上）、advanced=投げ抜け/ダッシュ/対空/飛び込み（HARD のみ）。
        boolean defends = difficulty != Difficulty.EASY;
        boolean advanced = difficulty == Difficulty.HARD;

        // 飛び込み（Task 57）の状態管理：着地したら解除（地上行動へ戻す）。AI は飛び込み以外で空中に行かない。
        if (self.isGrounded()) {
            jumpingIn = false;
        }

        // 無敵打撃必殺技（リバーサル・Task 53）を持つなら対空に使える。落ちてくる相手をこれで迎撃する。
        Move antiAir = findAntiAirMove(self);
        boolean opponentJumpIn = !opponent.isGrounded()        // 相手が空中
                && opponent.getVelocityY() <= 0f               // 下降（または頂点）＝こちらへ落ちてくる
                && distance <= ANTI_AIR_RANGE;                 // 縦長対空 hitbox の届く水平間合い

        // パリィ読み（Task 106・HARD のみ）：相手の打撃が active 直前（startup 残り AI_PARRY_LEAD フレーム以内）に入ったら、
        // 前方タップで弾く準備（parryHold）を立てる。commit 中（parryHold>0）は下の専用分岐が前方を短く押し続け、active を
        // パリィ（Task 105）する。一度試みたら parryCooldown の間は再発しない＝全打撃を弾く壁にせず「読み」に留める。
        //
        // 重要：Fighter 側の窓は「前方を押し始めて PARRY_WINDOW(5) フレーム以内」のみ成立（押しっぱなしでは不成立）。
        // よって active 着弾フレームで forwardHeldFrames が 1〜5 に収まるよう、active の直前 AI_PARRY_LEAD(2) フレームから
        // 押し始める（active 時 forwardHeldFrames ≒ 3）。長く保持すると窓を外す（self-review で検出した off-by-one を回避）。
        // 相手の attackFrame / startup（観測可能）だけで判断＝乱数なし・決定的。投げ（ガード不能）は parry できない。
        Move oppAtk = opponent.getCurrentMove();
        boolean opponentAboutToHit = opponentStriking
                && opponent.getAttackPhase() == AttackPhase.STARTUP
                && oppAtk != null
                && oppAtk.getStartup() - opponent.getAttackFrame() <= AI_PARRY_LEAD;
        if (advanced && parryCooldown == 0 && parryHold == 0 && opponentAboutToHit
                && distance <= GUARD_RANGE && self.isGrounded() && self.canStartAction()) {
            parryHold = AI_PARRY_LEAD + 2; // active の直前 LEAD フレーム＋着弾後 1〜2 フレームをカバー（fhf ≤ 4 ≤ PARRY_WINDOW）
            parryCooldown = AI_PARRY_COOLDOWN;
        }

        if (!self.isGrounded() && jumpingIn) {
            // 飛び込み中（空中・Task 57）：相手へドリフトしつつ、下降中に間合いへ入ったら空中攻撃（Task 32）を出す。
            // 空中攻撃は attackPhase==NONE のとき attackButton で発動するので、非攻撃中のみ attack を立てる
            // （既に出していれば isAttacking()==true で再発動しない）。地上反応はすべて canStartAction()==false で
            // 自然に無効化されるため、この分岐を最優先に置いて空中の振る舞いを一手に引き受ける。乱数なし＝決定的。
            moveDir = towardDir;
            if (self.getVelocityY() <= 0f && distance <= JUMP_IN_ATTACK_RANGE
                    && !self.isAttacking() && !self.isInHitstun()) {
                attack = true;
            }
        } else if (advanced && antiAir != null && opponentJumpIn && self.isGrounded()
                && self.canStartAction() && cooldown == 0) {
            // 無敵対空（Task 55）：飛び込んでくる相手を無敵フレーム付き打撃必殺技で落とす。
            // AI はコマンド検出（updateFighterInput）を経由しないので、自分で startSpecial を直接呼ぶ。
            // 打撃必殺技なので飛び道具生成・メーター消費は不要＝Core 無改修で成立。直後の self.update が技を進める。
            // 乱数なし＝決定的（相手の空中状態・下降・距離のみで判断）。
            self.startSpecial(antiAir);
            cooldown = ATTACK_COOLDOWN;
            dashTapStep = 0;
        } else if (advanced && prevKnockedDown && self.canStartAction() && antiAir != null
                && distance <= GUARD_RANGE) {
            // 起き上がりリバーサル（Task 97・HARD のみ）：ダウンから起き上がった瞬間（前フレーム down・今行動可能）に、
            // 相手が起き攻めの間合い（GUARD_RANGE 内）にいれば無敵打撃必殺技を置いて切り返す（昇龍拳タイプの wakeup DP）。
            // 起き上がりの 1 フレームだけ発火（prevKnockedDown が次フレームには false）＝乱発しない。乱数なし＝決定的。
            // 空振り / ガードされれば長 recovery で手痛い反確＝リスク/リターンの読み合い（撃たない選択は人間側のフェイント）。
            self.startSpecial(antiAir);
            cooldown = ATTACK_COOLDOWN;
            dashTapStep = 0;
        } else if (advanced && opponent.isThrowing() && distance <= THROW_TECH_RANGE
                && self.isGrounded() && self.canStartAction()) {
            // 投げ抜け反応（Task 51）：相手の掴み（ガード不能）に反応して投げ抜け窓をアームし、ニュートラルで抜けに専念する。
            // 掴みの startup 中から毎フレーム armThrowTech() し続けるので、active で掴まれた瞬間に canTechThrow() が成立して
            // 投げ抜け（相互に弾かれ・ノーダメージ）になる。空中は掴めない（Task 35）ので接地時のみ。乱数なし＝決定的。
            // 自分が攻撃硬直 / のけぞり中（canStartAction()==false）は窓を立てられず掴まれる＝硬直を投げで狩る択は残る。
            // ダッシュ接近中（Task 50）にこの分岐へ入ったらダッシュを止めてニュートラルに戻す（ガード反応と同じ作法）。
            // 止めないとダッシュ移動が継続して間合いがずれ、投げの成立可否・抜け後の位置まで変わる（CodeRabbit 指摘）。
            if (self.isDashing()) {
                self.cancelDash();
            }
            self.armThrowTech();
            dashTapStep = 0;
        } else if (advanced && parryHold > 0 && self.canStartAction()) {
            // パリィ commit（Task 106・HARD のみ）：上で立てた parryHold の間、前方を押し続けて相手の active を
            // パリィ（Task 105）で弾く＝ダメージ/chip/のけぞりなしで完全防御＋反撃確定。ダッシュ接近中なら止めて
            // 読みを優先する（ガード反応と同じ作法）。タイミングを外せば前進＝committal な被弾リスク（壁にならない）。
            if (self.isDashing()) {
                self.cancelDash();
            }
            moveDir = towardDir;
            dashTapStep = 0;
        } else if (defends && opponentStriking && distance <= GUARD_RANGE && self.canStartAction()) {
            // ガード反応：相手の打撃に合わせて後退方向を保持し、ガードで chip に抑える。
            // ダッシュ接近中（dashFrames>0 で guarding が抑止される）に GUARD_RANGE 内で相手の打撃を検知したら、
            // 自分のダッシュをキャンセルしてガードを優先する（Task 50 / Codex 指摘）。ダッシュは AI 自身の選択なので
            // 防御のために中断してよく、これで「打撃にはガード」(Task 37) の保証が接近中も成立する。
            if (self.isDashing()) {
                self.cancelDash();
            }
            moveDir = backDir;
            // 下段読み（Task 63・HARD のみ）：相手の打撃が下段なら しゃがみガードで対応する（立ちガードは下段に貫通される）。
            // 下段は (a) 相手がしゃがみ攻撃中（isCrouchAttacking＝実行時の下段・Task 31）か、(b) 技の guardHeight が
            // LOW（立ち下段＝Tetsu の low_sweep 等・Task 33）。crouchGuard を立てて update へ crouchHeld として渡す。
            // 乱数なし＝相手の観測状態のみで決定的。NORMAL は従来どおり立ちガード一辺倒（下段に弱い）＝難易度差。
            Move oppMove = opponent.getCurrentMove();
            boolean opponentLow = opponent.isCrouchAttacking()
                    || (oppMove != null && oppMove.getGuardHeight() == GuardHeight.LOW);
            crouchGuard = advanced && opponentLow;
            dashTapStep = 0;
        } else if (defends && opponent.isGuarding() && opponent.isGrounded() && hasThrow
                && distance <= THROW_RANGE && cooldown == 0 && self.canStartAction()) {
            // 投げ崩し：ガード偏重の相手をガード不能の投げで崩す（打撃は防がれるため）。
            // 空中ガード（Task 59）の相手は掴めない（投げは地上のみ・Task 35）ので opponent.isGrounded() で除外する。
            // 空中ガード導入前は isGuarding() が接地を含意していたため、この追加条件は従来挙動に対して no-op。
            throwReq = true;
            cooldown = ATTACK_COOLDOWN;
            dashTapStep = 0;
        } else if (advanced && projectile != null && distance > DASH_APPROACH_RANGE
                && self.isGrounded() && opponent.isGrounded()
                && cooldown == 0 && self.canStartAction()) {
            // 飛び道具牽制（zoner・Task 64・HARD のみ）：遠距離の接地した相手へ飛び道具を撃って牽制する。
            // AI はコマンド検出（updateFighterInput）を通らないので startSpecial を直接呼び、弾生成だけ Core に委ねる
            // （control() 後に consumePendingProjectile() を読んで spawnProjectile する）。打撃必殺技＝対空（Task 55）と違い
            // 飛び道具は弾生成のため Core 連携が要る唯一の必殺技。クールダウン中は下のダッシュ接近へフォールスルー＝
            // 撃ちつ詰めつの zoner 行動になる。乱数なし＝決定的（距離・接地・所持技・クールダウンのみ）。
            if (self.startSpecial(projectile)) {
                pendingProjectile = projectile;
                cooldown = ATTACK_COOLDOWN;
            }
            dashTapStep = 0;
        } else if (advanced && distance > DASH_APPROACH_RANGE && self.canStartAction()) {
            // 遠距離：ダッシュ（二度押し前ステップ）で素早く間合いを詰める（Task 50。HARD のみ／他は下の歩き接近）。
            // Fighter のダッシュ検出（同方向押下エッジ×2 が受付窓内）に合わせ、押下→離す→押下の 3 フレームを生成する。
            if (self.isDashing()) {
                // 既にダッシュ発動中：方向を維持し（向き固定）、パターンを初期化して次の二度押しに備える。
                moveDir = towardDir;
                dashTapStep = 0;
            } else {
                switch (dashTapStep) {
                    case 0: // 1 度目の押下（エッジを立てる）
                        moveDir = towardDir;
                        dashTapStep = 1;
                        break;
                    case 1: // ニュートラル（一度離して次の押下をエッジにする）
                        moveDir = 0;
                        dashTapStep = 2;
                        break;
                    default: // 2 度目の押下（受付窓内ならダッシュ発動）
                        moveDir = towardDir;
                        dashTapStep = 0;
                        break;
                }
            }
        } else if (advanced && opponent.isGrounded() && self.isGrounded()
                && distance > ATTACK_RANGE && distance <= DASH_APPROACH_RANGE
                && cooldown == 0 && self.canStartAction()) {
            // 飛び込み（ジャンプ攻撃・Task 57・HARD のみ）：中距離から前方ジャンプで踏み切り、空中攻撃で攻める。
            // 空中での攻撃発火・ドリフトは先頭の「飛び込み中」分岐が担う。クールダウン明けのみ発動＝歩き接近と
            // 交互になり一辺倒にならない（一定間隔で飛び込む）。対空（Task 55）を持つ相手には落とされる＝対の択。
            jumpReq = true;
            moveDir = towardDir;       // 前方ジャンプ（空中で相手へドリフト）
            jumpingIn = true;
            cooldown = ATTACK_COOLDOWN;
            dashTapStep = 0;
        } else if (distance > ATTACK_RANGE) {
            // 間合いの外（ただしダッシュ距離より内）：歩いて接近する。
            moveDir = towardDir;
            dashTapStep = 0;
        } else if (opponent.isGrounded() && cooldown == 0 && self.canStartAction()) {
            // 間合いの内：通常攻撃を出す（クールダウン明け・行動可能時のみ）。空中の相手には出さない
            // ——地上の通常技は空振りするうえ、クールダウンを浪費して無敵対空（Task 55）の機会を潰すため。
            attack = true;
            cooldown = ATTACK_COOLDOWN;
            dashTapStep = 0;
        }
        self.update(moveDir, jumpReq, attack ? AttackButton.LIGHT : null, crouchGuard, throwReq);
    }

    /**
     * 対空に使える技＝<b>無敵フレーム付きの打撃必殺技</b>（リバーサル・Task 53）を {@code specialMoves[]} から探す（Task 55）。
     * 飛び道具（{@code projectile}）は対空に使わない（縦の無敵迎撃が要るため）。無ければ {@code null}（そのキャラは対空しない）。
     * データ駆動：キャラ JSON に該当技があるキャラ（例：fighter002 の {@code rising_talon}）だけが AI 対空をする。
     */
    private static Move findAntiAirMove(Fighter self) {
        Move[] specials = self.getDef().getSpecialMoves();
        if (specials == null) {
            return null;
        }
        for (Move m : specials) {
            if (m != null && !m.isProjectile() && m.getInvincibleFrames() > 0) {
                return m;
            }
        }
        return null;
    }

    /**
     * 牽制に使える<b>飛び道具</b>（{@code projectile} な必殺技）を {@code specialMoves[]} から探す（Task 64）。
     * 無ければ {@code null}（そのキャラは AI 飛び道具を撃たない＝grappler/charge 専用キャラはこの反応をスキップ）。
     * データ駆動：キャラ JSON に飛び道具技がある（{@code HADOUKEN} など）キャラだけが AI 牽制で弾を撃つ。
     */
    private static Move findProjectileMove(Fighter self) {
        Move[] specials = self.getDef().getSpecialMoves();
        if (specials == null) {
            return null;
        }
        for (Move m : specials) {
            if (m != null && m.isProjectile()) {
                return m;
            }
        }
        return null;
    }
}
