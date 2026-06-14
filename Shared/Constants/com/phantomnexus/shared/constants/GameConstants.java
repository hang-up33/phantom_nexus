package com.phantomnexus.shared.constants;

/**
 * 画面・描画まわりの定数（単一の真実）。
 *
 * <p>CLAUDE.md「アーキテクチャ」のとおり、画面サイズ・フレームレート・レイヤ順などの
 * 定数は {@code Shared/Constants} に集約する。各モジュールはハードコードせず本クラス経由で参照する。
 */
public final class GameConstants {

    private GameConstants() {
        // インスタンス化禁止（定数ホルダー）
    }

    /** 仮想解像度（ワールド座標）の幅。単位はピクセル。 */
    public static final int WORLD_WIDTH = 1280;

    /** 仮想解像度（ワールド座標）の高さ。単位はピクセル。 */
    public static final int WORLD_HEIGHT = 720;

    /** ウィンドウタイトル（OS のタイトルバー / 初期画面の表示名）。 */
    public static final String WINDOW_TITLE = "Phantom Nexus";

    /** 背景クリア色（RGBA, 各 0.0〜1.0）。 */
    public static final float BG_R = 0.07f;
    public static final float BG_G = 0.08f;
    public static final float BG_B = 0.12f;
    public static final float BG_A = 1.0f;

    /**
     * 目標フレームレート。BattleSystem.md の「60fps 固定ステップ」基準であり、
     * 同時にウィンドウの前景 FPS 上限としても使用する。
     */
    public static final int TARGET_FPS = 60;

    /** 垂直同期。ティアリング防止のため既定で有効。 */
    public static final boolean VSYNC = true;

    /**
     * ウィンドウのリサイズ可否。MVP は固定解像度の格闘ゲームのため固定窓（false）。
     * 仮想解像度（{@link #WORLD_WIDTH}×{@link #WORLD_HEIGHT}）はビューポートで維持する。
     */
    public static final boolean WINDOW_RESIZABLE = false;

    /** 地面（床）のワールド Y 座標。キャラクターの足元（描画原点の下端）が乗る基準線。 */
    public static final float GROUND_Y = 120f;

    /** プレイヤー 1 の初期 X 座標（キャラクターの中心 X）。画面中央から左に配置。 */
    public static final float P1_SPAWN_X = 420f;

    /** プレイヤー 2 の初期 X 座標（キャラクターの中心 X）。画面中央から右に配置。 */
    public static final float P2_SPAWN_X = 860f;

    /** 重力加速度（px/frame^2）。ジャンプの落下に毎フレーム適用する（60fps 基準）。 */
    public static final float GRAVITY = 0.6f;

    /** のけぞり（hitstun）フレーム数。被弾側がこの間だけ行動不能になる（Task 13）。 */
    public static final int HITSTUN_FRAMES = 18;

    /** 被弾時の初速 knockback（px/frame, 後方へ）。毎フレーム {@link #KNOCKBACK_FRICTION} で減衰（Task 13）。 */
    public static final float KNOCKBACK_SPEED = 7f;

    /** knockback 速度の毎フレーム減衰率（0〜1）。小さいほど早く止まる（Task 13）。 */
    public static final float KNOCKBACK_FRICTION = 0.6f;

    /**
     * 下段（しゃがみ）攻撃の hitbox を置く Y オフセット（足元基準, px）。Task 31。
     *
     * <p>しゃがみ攻撃は技定義の {@code hitboxOffsetY}（立ち姿勢用で 90px 以上）を使わず、脚部のこの低位に
     * hitbox を出す。これによりしゃがみ食らい判定（{@code height/3} ≒ 80px, 足元〜80px）にも届く下段技になり、
     * 立っている相手の脚にも当たる。0 = 足元（地面ライン）。
     */
    public static final float LOW_ATTACK_HITBOX_OFFSET_Y = 0f;

    /**
     * 投げ（Task 35）の hitstun フレーム数。通常被弾（{@link #HITSTUN_FRAMES}）より長く、掴みの拘束を表す。
     * ガード不能の近接掴みが成立したときに被弾側へ適用する。
     */
    public static final int THROW_HITSTUN_FRAMES = 30;

    /**
     * 投げの knockback 倍率（{@link #KNOCKBACK_SPEED} に乗算）。通常被弾より強く吹き飛ばし、掴みからの放り投げを表す（Task 35）。
     */
    public static final float THROW_KNOCKBACK_SCALE = 1.6f;

    /**
     * 投げ抜け（throw tech, Task 36）の猶予窓（フレーム数）。投げボタンを押すとこのフレーム数だけ「抜け可能」状態になり、
     * その間に相手の投げを掴まれると投げ抜け（相互に弾かれ・ノーダメージ）になる。掴みの発生（startup+α）を跨げる長さにする。
     */
    public static final int THROW_TECH_WINDOW = 10;

    /**
     * 投げ抜け成立後の硬直フレーム数（Task 36）。両者がこの間だけ行動不能になり、{@link #THROW_TECH_PUSHBACK} で弾かれる。
     * のけぞり（{@link #HITSTUN_FRAMES}）より短く、ノーダメージのため読み合いがすぐ再開する。
     */
    public static final int THROW_TECH_FRAMES = 14;

    /** 投げ抜け時に両者へ与える相互 knockback の初速（px/frame）。互いに反対方向へ弾く（Task 36）。 */
    public static final float THROW_TECH_PUSHBACK = KNOCKBACK_SPEED;

    /**
     * ダウン（knockdown, Task 60）の行動不能フレーム数。のけぞり（{@link #HITSTUN_FRAMES}）より長く、
     * {@code Move.knockdown=true} の技を非ガードで食らうと適用される。ダウン中は被弾無敵（起き攻め＝OTG なし）で、
     * このフレーム数が尽きると起き上がる。
     */
    public static final int KNOCKDOWN_FRAMES = 60;

    /** ダウンの knockback 倍率（{@link #KNOCKBACK_SPEED} に乗算）。通常被弾より強く吹き飛ばして転ばせる（Task 60）。 */
    public static final float KNOCKDOWN_KNOCKBACK_SCALE = 1.4f;

    /**
     * 受け身（ukemi・クイック起き上がり, Task 66）の入力受付窓（フレーム）。ダウン（{@link #KNOCKDOWN_FRAMES}）開始から
     * この経過フレーム以内に行動入力（攻撃 / ジャンプ / 投げ）があれば、残りダウンフレームを {@link #UKEMI_RISE_FRAMES} に
     * 短縮して早く起き上がる（起き攻めへの対抗択）。窓を過ぎてからの入力は無効（フル {@link #KNOCKDOWN_FRAMES} 待つ）。
     */
    public static final int UKEMI_WINDOW = 12;

    /**
     * 受け身成立時の残りダウンフレーム（Task 66）。{@link #UKEMI_WINDOW} 内に受け身入力すると残りがこの値に短縮される。
     * フル {@link #KNOCKDOWN_FRAMES}(60) より大幅に短いが、その分**起き上がりが早い＝無敵が早く切れる**（メアリーへの隙）。
     */
    public static final int UKEMI_RISE_FRAMES = 20;

    /**
     * 空中受け身（air recovery / air tech, Task 126）が成立するための「空中やられ最小経過フレーム」。
     * 浮かせ（{@link Fighter#applyLaunch} 等）で空中やられになってからこのフレーム数が経過したとき、行動入力で受け身できる。
     * 最小窓により**打ち上げ直後の確定ジャグル**（攻撃側の保証）を残しつつ、その後は防御側が受け身で空中やられを抜けられる
     * （バーストに似た攻防）。被弾（{@link Fighter#applyHit}）のたびに経過がリセットされるため、多段ジャグルの最中
     * （連続被弾中）は受け身できず、コンボが途切れて初めて受け身可能になる（単発浮かせの hitstun ≈18f 内に収まる短い窓）。
     */
    public static final int AIR_TECH_MIN_FRAMES = 8;

    /**
     * 空中受け身の成立後リカバリ（Task 126）。受け身で空中やられを抜けた直後、このフレーム数は滞空したまま行動不能
     * （攻撃 / ダッシュ / ジャンプ等を出せない）で落下する。受け身は**被弾無敵ではない**ため、相手が受け身を釣って
     * 再度浮かせ直せる＝ committal なリスク（受け身狩り）。リカバリ後は通常の滞空行動（空中ガード / 空中攻撃等）が可能。
     */
    public static final int AIR_TECH_RECOVERY_FRAMES = 16;

    /**
     * ダメージ数値ポップアップの表示フレーム数（被弾 / ガード時に与ダメージ量を命中位置から浮かび上がらせる演出）。
     *
     * <p>命中位置からこのフレーム数だけ上昇しながら表示し、終盤でフェードアウトして消える。60fps 基準で
     * 約 0.67 秒。純粋な視覚演出のため戦闘結果には影響しない（HP 計算とは独立）。
     */
    public static final int DAMAGE_POPUP_FRAMES = 40;

    /**
     * ヒットスパーク（命中時の火花エフェクト）の表示フレーム数（Task 38）。命中位置で放射状の火花が
     * このフレーム数だけ拡大＋フェードして消える。60fps 基準で約 0.2 秒の短い手応え演出。純粋な視覚演出で
     * 戦闘結果には影響しない。
     */
    public static final int HIT_SPARK_FRAMES = 12;

    /**
     * 着地の砂煙（landing dust）の表示フレーム数（Task 131）。ジャンプ / 浮かせからの着地（滞空→接地の
     * 遷移）で足元に出す土埃の寿命。純粋な視覚演出で戦闘結果には影響しない（決定的・乱数なし）。
     */
    public static final int LANDING_DUST_FRAMES = 16;

    /**
     * 画面の微振動（hit shake）の持続フレーム数と振幅（Task 132）。打撃 / 飛び道具 / 投げの接触時に
     * カメラをわずかに揺らして衝撃を演出する。決定的（乱数なし）＝カメラオフセットのみで戦闘結果に影響しない。
     */
    public static final int SHAKE_FRAMES = 9;
    public static final float HIT_SHAKE_MAGNITUDE = 7f;   // クリーンヒットの揺れ幅（px）
    public static final float GUARD_SHAKE_MAGNITUDE = 3f; // ガード成立の揺れ幅（px・控えめ）

    /**
     * ラウンド開始イントロ（"ROUND N" → "FIGHT!" 演出）の総フレーム数（Task 42）。各ラウンド開始時、
     * この間はファイター操作・判定・タイマーを停止して開始演出を表示し、0 になった瞬間から戦闘開始。
     * 60fps 基準で約 1.5 秒。撮影モードでは既定でスキップ（{@code -x intro=true} で有効化）し、
     * 既存スクショレシピ（frame1 から戦闘前提）の後方互換を保つ。
     */
    public static final int ROUND_INTRO_FRAMES = 90;

    /**
     * ガードゲージの最大値（Task 43）。ガード成立（chip 被弾）のたびに攻撃力に応じて減り、0 になると
     * ガードクラッシュ（{@link #GUARD_BREAK_FRAMES} の行動不能＋ガード不能）。非ガード時は毎フレーム
     * {@link #GUARD_REGEN_PER_FRAME} 回復する。連続ガードは安全ではない＝崩しの読み合いを成立させる。
     */
    public static final float GUARD_GAUGE_MAX = 100f;

    /**
     * ガード 1 回あたりのゲージ減少量の除数（Task 43）。減少量 = {@code max(1, 攻撃力 / GUARD_DRAIN_DIVISOR)}。
     * 強い技ほど大きく削る（例：80 ダメージの中攻撃で 20 減＝5 回ガードで崩れる）。
     */
    public static final int GUARD_DRAIN_DIVISOR = 4;

    /** 非ガード時のガードゲージ回復量（フレームあたり・Task 43）。約 250f（≒4 秒）で満タンに戻る。 */
    public static final float GUARD_REGEN_PER_FRAME = 0.4f;

    /**
     * ガードクラッシュ時の行動不能フレーム数（Task 43）。ゲージが尽きると防御側はこのフレーム数だけ
     * ガード不能・行動不能になり（hitstun を流用）、攻撃側のフル確定反撃を許す。60fps 基準で約 0.67 秒。
     */
    public static final int GUARD_BREAK_FRAMES = 40;

    /**
     * ラウンド開始イントロのうち末尾の "FIGHT!" を表示するフレーム数（Task 42）。{@link #ROUND_INTRO_FRAMES}
     * の残りがこの値以下になったら "ROUND N" から "FIGHT!" 表示へ切り替える。60fps 基準で約 0.5 秒。
     */
    public static final int FIGHT_FLASH_FRAMES = 30;

    /**
     * 必殺技ゲージ（スーパーメーター・Task 44）の最大値。攻撃を当てる / 受ける / ガードで貯まり、
     * 満タンで必殺技（飛び道具）を撃つと消費して EX 版（{@link #EX_DAMAGE_MULTIPLIER} 倍・大型）になる。
     * ガードゲージ（防御リソース）と対になる攻撃リソース。乱数なしで貯まる（入力リプレイと両立）。
     */
    public static final float SUPER_METER_MAX = 100f;

    /** 攻撃を当てた側（攻撃側）のメーター増加量（Task 44）。 */
    public static final float METER_GAIN_ON_HIT = 14f;

    /** 攻撃を受けた側（防御側）のメーター増加量（Task 44）。攻めるより受けるほうが少なく貯まる。 */
    public static final float METER_GAIN_ON_TAKE = 8f;

    /** ガード成立時に攻防両者へ入るメーター増加量（Task 44）。 */
    public static final float METER_GAIN_ON_GUARD = 5f;

    /** EX 必殺技のダメージ倍率（Task 44）。満タンのメーターを消費して撃つと通常の {@code damage} に乗算される。 */
    public static final float EX_DAMAGE_MULTIPLIER = 1.6f;

    /** EX 飛び道具の見た目・判定の拡大率（Task 44）。判定矩形・描画ともこの倍率で大型化する。 */
    public static final float EX_PROJECTILE_SCALE = 1.5f;

    /**
     * コンボダメージ補正（ダメージスケーリング・Task 46）の 1 ヒットあたりの減衰量。コンボ 2 ヒット目以降、
     * ヒット数が 1 増えるごとに与ダメージ倍率をこの値だけ下げる（1 ヒット目は等倍）。長いコンボほど
     * 後続の伸びが鈍り、無限・即死コンボを抑える。倍率は {@link #COMBO_SCALE_MIN} で下限を打つ。
     */
    public static final float COMBO_SCALE_STEP = 0.1f;

    /** コンボダメージ補正の最小倍率（Task 46）。これ以上は減らない下限（与ダメージが 0 にならないよう保証）。 */
    public static final float COMBO_SCALE_MIN = 0.3f;

    /**
     * ダッシュ（二度押しステップ・Task 49）の二度押し受付窓（フレーム数）。同じ方向の押下が前回の押下から
     * このフレーム数以内なら「二度押し」と見なしてダッシュを開始する。60fps 基準で 0.2 秒。
     */
    public static final int DASH_TAP_WINDOW = 12;

    /** ダッシュの継続フレーム数（Task 49）。この間は通常歩行より速く前進/後退し、攻撃/被弾でキャンセルされる。 */
    public static final int DASH_FRAMES = 12;

    /** ダッシュ速度の倍率（Task 49）。ダッシュ中の移動量 = `walkSpeed × DASH_SPEED_MULTIPLIER`。 */
    public static final float DASH_SPEED_MULTIPLIER = 2.4f;

    /**
     * ダッシュ攻撃（Task 65）の突進初速（px/frame, 前方へ）。ダッシュ中に攻撃を出すと、ダッシュの勢いを
     * 引き継いだ突進としてこの初速を {@code velocityX} へ与える。毎フレーム {@link #KNOCKBACK_FRICTION} で減衰し、
     * 攻撃の startup〜active 間に前方へスライドしてから止まる（既存の velocityX 適用経路を流用）。
     */
    public static final float DASH_ATTACK_LUNGE_SPEED = 14f;

    /**
     * カウンターヒット（Task 71）のダメージ倍率。相手の攻撃 <b>startup 中</b>（技を出しきる前）に打撃を当てると
     * 「差し返し（counter hit）」として与ダメージをこの倍率で増やす。攻撃を振る側のリスクを表現し、
     * 置き / 差し込みの読み合いに価値を与える。倍率のみで乱数なし（入力リプレイの決定性を保つ）。
     */
    public static final float COUNTER_HIT_DAMAGE_SCALE = 1.3f;

    /**
     * カウンターヒット成立時に上乗せするのけぞり（hitstun）フレーム数（Task 71）。通常 {@link #HITSTUN_FRAMES} に
     * 加算し、カウンターから追撃（コンボ）が繋がりやすくする。ダウン技のカウンターはダウンが既に長いため
     * ダメージ倍率のみ適用しこのボーナスは加えない。
     */
    public static final int COUNTER_HIT_BONUS_HITSTUN = 8;

    /**
     * カウンターヒットを受けた側の表示フレーム数（Task 71）。被弾ラベルに {@code (CH)} を付して識別する表示専用カウンタの寿命。
     * のけぞりと同程度で十分（カウンター被弾＝差し返された証跡をスクショで読めるようにする）。
     */
    public static final int COUNTER_HIT_LABEL_FRAMES = HITSTUN_FRAMES + COUNTER_HIT_BONUS_HITSTUN;

    /**
     * めまい（dizzy / stun・Task 79）の行動不能フレーム数。スタン値が {@code Character.stunThreshold} を超えると
     * この長さだけ<b>無防備</b>に硬直する（のけぞりと違い長く、ダウンと違い被弾無敵ではない＝フルコンボ確定の隙）。
     * 60fps 基準で約 1.7 秒。スタンを蓄積させる連係への大きな見返りになる。
     */
    public static final int DIZZY_FRAMES = 100;

    /**
     * スタン値の毎フレーム自然減衰量（Task 79）。被弾していない（のけぞり / ダウン / めまいでない）間にスタンが
     * この量ずつ抜けていく。これにより「短時間に畳みかけて」蓄積した時だけめまいに至り、間合いを離せば回復する。
     */
    public static final int STUN_DECAY_PER_FRAME = 2;

    /**
     * ジャストガード（Task 81）の受付窓（フレーム数）。後退方向を保持し始めてからこのフレーム数以内に攻撃を
     * ガードすると「ジャストガード」成立＝chip ダメージなし・ガードゲージを削らない・メーター獲得・最小 knockback。
     * 押しっぱなしのガード（ターン）では成立せず、ヒット直前に合わせて入力した反応ガードのみ成立する（小さく＝シビア）。
     */
    public static final int JUST_GUARD_WINDOW = 4;

    /** ジャストガード成立時に獲得する必殺技ゲージ量（Task 81）。リスクを取った反応ガードへの見返り。 */
    public static final float JUST_GUARD_METER = 12f;

    /** ジャストガード成立の表示フレーム数（Task 81）。状態ラベルに {@code [JUST]} を付す表示専用カウンタの寿命。 */
    public static final int JUST_GUARD_LABEL_FRAMES = 16;

    /**
     * 削り（chip）ダメージで KO できるか（Task 82）。{@code false}（既定）なら、ガード中の chip ダメージは
     * HP を 1 未満にしない（最低 1 残す）＝「削り殺し」を禁止する格闘ゲーム定番ルール。打撃 / 投げの<b>非ガード</b>
     * ヒット（{@code applyHit}/{@code applyThrow}/{@code applyKnockdown}）は通常どおり 0 まで削れる（KO 可能）。
     */
    public static final boolean CHIP_DAMAGE_CAN_KO = false;

    /**
     * ヒットストップ（hit-stop・Task 86）：打撃 / 投げ / 飛び道具が<b>命中</b>したとき、両ファイターを
     * このフレーム数だけ凍結して衝撃を演出する（攻守ともに一瞬止まり手応えが増す）。固定値で乱数なし＝決定的。
     */
    public static final int HITSTOP_FRAMES = 4;

    /** ガード成立時のヒットストップ（Task 86）。命中より短く、ガードの「いなし」感を出す。 */
    public static final int HITSTOP_BLOCK_FRAMES = 2;

    /**
     * 壁バウンド（wall bounce・Task 101）の水平吹き飛ばし初速（px/frame, 後方へ）。{@code Move.wallBounce=true} の技を
     * 非ガードヒットさせると相手にこの初速を与えて横へ飛ばす。画面端（壁）に達すると {@link #WALL_BOUNCE_REBOUND_SCALE}
     * 倍の速度で跳ね返り、{@link #WALL_BOUNCE_POP} で再び浮いて追撃可能になる（画面端ジャグルの延長）。
     */
    public static final float WALL_BOUNCE_SPEED = 11f;

    /** 壁に当たって跳ね返るときの水平速度の倍率（Task 101）。元の吹き飛ばし速度にこの倍率を掛けて反対方向へ戻す。 */
    public static final float WALL_BOUNCE_REBOUND_SCALE = 0.6f;

    /** 壁バウンドで跳ね返る瞬間に与える上方初速（px/frame・Task 101）。再び浮かせて追撃の猶予を作る。 */
    public static final float WALL_BOUNCE_POP = 8f;

    /** 壁バウンド成立（跳ね返り）時に上乗せするのけぞりフレーム数（Task 101）。跳ね返り際の追撃が間に合う猶予。 */
    public static final int WALL_BOUNCE_BONUS_HITSTUN = 16;

    /** 壁バウンド成立の表示フレーム数（Task 101）。状態ラベルに wall_bounce を出す表示専用カウンタの寿命。 */
    public static final int WALL_BOUNCE_LABEL_FRAMES = 20;

    /**
     * 床バウンド（ground bounce・Task 102）の初期打ち上げ初速（px/frame・上向き）。{@code Move.groundBounce=true} の技を
     * 非ガードヒットさせると相手をこの初速で打ち上げ、落下して着地した瞬間に {@link #GROUND_BOUNCE_POP} で一度だけ跳ね返る。
     */
    public static final float GROUND_BOUNCE_LAUNCH = 10f;

    /** 床バウンドで着地時に跳ね返る上方初速（px/frame・Task 102）。再び浮かせて追撃の猶予を作る。 */
    public static final float GROUND_BOUNCE_POP = 9f;

    /** 床バウンド成立（跳ね返り）時に上乗せするのけぞりフレーム数（Task 102）。跳ね返り際の追撃が間に合う猶予。 */
    public static final int GROUND_BOUNCE_BONUS_HITSTUN = 18;

    /** 床バウンド成立の表示フレーム数（Task 102）。状態ラベルに ground_bounce を出す表示専用カウンタの寿命。 */
    public static final int GROUND_BOUNCE_LABEL_FRAMES = 20;

    /**
     * 回復可能ダメージ（レッドライフ・Task 104）の回復開始までの遅延フレーム数。ガードの chip 被弾分は「赤ゲージ」として
     * 一時的に失われ、最後に被弾（chip 含む）してからこのフレーム数だけ無被弾が続くと白 HP へ回復し始める。60fps 基準で約 1.5 秒。
     */
    public static final int RECOVERABLE_HP_DELAY_FRAMES = 90;

    /**
     * 回復可能ダメージの回復間隔（フレーム・Task 104）。{@link #RECOVERABLE_HP_DELAY_FRAMES} 経過後、このフレーム間隔ごとに
     * 赤ゲージ 1 ポイントを白 HP へ戻す。小さいほど速く回復する（3＝約 20HP/秒）。
     */
    public static final int RECOVERABLE_HP_REGEN_INTERVAL = 3;

    /**
     * パリィ（parry・Task 105）の受付窓（フレーム数）。<b>前方</b>（相手方向）を押し始めてからこのフレーム数以内に
     * 攻撃が当たると「パリィ」成立＝ダメージ・chip・のけぞりなしで完全に弾き、攻撃側の硬直を反撃確定にできる。
     * ジャストガード（後退入力・Task 81）と対になる前方入力の防御テク。前方を押しっぱなし（前進）では成立せず、
     * ヒット直前に合わせて<b>タップし直した</b>反応のみ成立する（小さく＝シビア）。投げはパリィできない。
     */
    public static final int PARRY_WINDOW = 5;

    /** パリィ成立時に獲得する必殺技ゲージ量（Task 105）。リスクの高い前方反応への見返り（ジャストガードより多め）。 */
    public static final float PARRY_METER = 14f;

    /** パリィ成立の表示フレーム数（Task 105）。状態ラベル "parry" を出す表示専用カウンタの寿命（行動はロックしない＝即反撃可）。 */
    public static final int PARRY_LABEL_FRAMES = 14;

    /**
     * スーパーフラッシュ（super flash・Task 108）の凍結フレーム数。スーパー必殺技の発動時、両ファイター・判定・タイマーを
     * このフレーム数だけ凍結して発動演出（暗転フラッシュ）を見せる。ヒットストップ（{@link #HITSTOP_FRAMES}）の長尺版。
     * 固定値で乱数なし＝決定的。60fps 基準で約 0.5 秒。
     */
    public static final int SUPER_FLASH_FRAMES = 30;

    /**
     * プッシュブロック（guard pushback・Task 111）：攻撃がガードされたとき、<b>攻撃側</b>も相手と反対方向へこの初速で
     * 押し戻される（防御側は従来どおり {@code applyGuard} で後退）。両者が離れて間合いがリセットされ、ガードからの仕切り直しが
     * しやすくなる＝固め（押し付け）への対抗。毎フレーム {@link #KNOCKBACK_FRICTION} で減衰。固定値で乱数なし＝決定的。
     */
    public static final float GUARD_PUSHBACK_SPEED = 4f;

    /**
     * KO スローモーション（Task 115）の演出フレーム数（実フレーム）。決着の一撃でどちらかが KO したとき、ラウンド確定の
     * 直前にこの実フレーム数だけスロー再生する。スロー中は戦闘更新を {@link #KO_SLOW_FACTOR} フレームに 1 回へ間引く。
     * 固定値で乱数なし＝決定的。60fps 基準で約 0.6 秒。
     */
    public static final int KO_SLOW_FRAMES = 36;

    /** KO スローモーション（Task 115）の間引き係数。スロー中はこのフレーム数に 1 回だけ実体更新する（4＝1/4 速）。 */
    public static final int KO_SLOW_FACTOR = 4;

    /**
     * ディレイ起き上がり（delayed wakeup・Task 122）の最大遅延フレーム数。ダウン中に<b>下（しゃがみ方向）を押し続ける</b>と
     * 起き上がりタイマーを最大このフレーム数ぶん凍結し、起き上がりを遅らせる＝相手の起き攻め（重ね）のタイミングをずらす
     * 防御択。受け身（ukemi＝早く起きる・Task 66）と対の「遅く起きる」択。ダウン中無敵は凍結中も維持される。
     */
    public static final int DELAY_WAKEUP_MAX = 24;
}
