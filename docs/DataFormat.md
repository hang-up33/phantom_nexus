# DataFormat — データ仕様（唯一の真実は `Shared/Schema`）

本書は Phantom Nexus の外部データ（JSON）仕様。**実体の真実は `Shared/Schema` / `Shared/Types`** にあり、本書はその人間向けの説明。
データ仕様を変える PR では本書を同時に更新する（[CLAUDE.md](../CLAUDE.md) のルール）。

- 形式：**JSON**（LibGDX 組込み `com.badlogic.gdx.utils.Json` で読み込む。追加の JSON ライブラリは入れない）
- 文字コード：UTF-8
- 配置：キャラは `Assets/Characters/<id>.json`、ステージは `Assets/Stages/<id>.json`
- 座標系・単位：別途 `Shared/Constants` に定義（ピクセル / フレーム[60fps 基準]）

> **Task 15 で MVP 正式版を確定**（以下）。実体は `Shared/Types`（POJO）と `Assets/Characters/<id>.json`。
> 概念上の全体像（下記「トップレベル構造」）は将来像で、MVP は 1 キャラ = 1 JSON に必要要素を内包する。

---

## トップレベル構造（FightingGame）

```
FightingGame
├─ Characters    キャラクター定義
├─ Animations    アニメーション（スプライト/フレーム）
├─ States        ステート（待機/歩き/ジャンプ/攻撃/のけぞり 等）
├─ Commands      コマンド入力（波動拳/溜め/同時押し）
├─ Hitboxes      攻撃判定
├─ Hurtboxes     食らい判定
├─ Stages        ステージ
├─ Sounds        BGM / SE
└─ BattleRules   HP・タイマー・ラウンド等の対戦ルール
```

MVP では 1 キャラ = 1 JSON ファイルに必要要素を内包する形を基本とする（上記は概念上の全体像）。

---

## Character（Task 24 改訂版）

1 キャラ = 1 JSON ファイル（`Assets/Characters/<id>.json`、UTF-8）。LibGDX `Json` が POJO（`Shared/Types/Character`）へ
フィールド名一致でデシリアライズする（Task 16）。Task 24 で技定義を **配列（`normalMoves[]` / `specialMoves[]`）** に拡張。
実サンプルは `Assets/Characters/fighter001.json`（Aoi）/ `fighter002.json`（Akane）/ `fighter003.json`（Tetsu）/ `fighter004.json`（Rai）/ `fighter005.json`（Sora・遠距離 zoner 型：長い射程の通常技と高速飛び道具・低 HP）/ `fighter006.json`（Iwao・**飛び道具を持たない純グラップラー型**：最高 HP1200・最遅・短リーチ高火力の通常技・最大火力の投げと無敵リバーサル `rising_hammer`＝`specialMoves` が CHARGE_SHOT 1 件のみ＝**`projectile` を 1 つも持たないキャラ**）/ `fighter007.json`（Kaede・**飛び道具＋ knockdown heavy・無敵リバーサルなしの footsies 型**：中リーチの poke・強攻撃 `roundhouse` に `knockdown:true`＝**新キャラの技に knockdown フラグを足すだけでダウンを奪える**実例（Task 60）・飛び道具 `wind_shot`）。
撮影時は **`phantom.screenshot.p1char=<id>` / `p2char=<id>`（`-x p1char=<id>` / `-x p2char=<id>`）** で読み込むキャラを差し替えられる（新キャラの撮り分け用。`PhantomNexusGame` が `ScreenshotController.charId(player, fallback)` 経由で選択。`stageId(fallback)` のキャラ版）。

```json
{
  "id": "fighter001",
  "name": "Aoi",
  "hp": 1000,
  "walkSpeed": 4.0,
  "jumpPower": 12.0,
  "width": 100,
  "height": 240,
  "color": [0.30, 0.55, 0.92],
  "sprite": {
    "path": "Characters/fighter001.png",
    "frameWidth": 64,
    "frameHeight": 128,
    "stateRows": [
      { "state": "idle", "row": 0 },
      { "state": "walk", "row": 1 },
      { "state": "jump", "row": 2 },
      { "state": "attack", "row": 3 },
      { "state": "jump_attack", "row": 3 },
      { "state": "throw", "row": 3 },
      { "state": "hitstun", "row": 4 },
      { "state": "guard", "row": 5 },
      { "state": "crouch", "row": 6 },
      { "state": "crouch_walk", "row": 6 },
      { "state": "crouch_attack", "row": 6 },
      { "state": "crouch_guard", "row": 6 }
    ]
  },
  "normalMoves": [
    {
      "id": "light_punch",
      "button": "light",
      "damage": 50,
      "startup": 5,
      "active": 4,
      "recovery": 10,
      "hitboxOffsetX": 0,
      "hitboxOffsetY": 130,
      "hitboxWidth": 80,
      "hitboxHeight": 34
    },
    {
      "id": "medium_kick",
      "button": "medium",
      "damage": 80,
      "startup": 8,
      "active": 6,
      "recovery": 16,
      "hitboxOffsetX": 0,
      "hitboxOffsetY": 100,
      "hitboxWidth": 90,
      "hitboxHeight": 40
    },
    {
      "id": "heavy_slam",
      "button": "heavy",
      "guardHeight": "overhead",
      "damage": 130,
      "startup": 14,
      "active": 5,
      "recovery": 28,
      "hitboxOffsetX": 0,
      "hitboxOffsetY": 60,
      "hitboxWidth": 110,
      "hitboxHeight": 90,
      "knockdown": true
    }
  ],
  "specialMoves": [
    {
      "id": "fireball",
      "command": "HADOUKEN",
      "damage": 120,
      "startup": 10,
      "active": 4,
      "recovery": 26,
      "hitboxOffsetX": 0,
      "hitboxOffsetY": 110,
      "hitboxWidth": 56,
      "hitboxHeight": 56,
      "projectile": true,
      "projectileSpeed": 9.0
    },
    {
      "id": "rising_dragon",
      "command": "CHARGE_SHOT",
      "damage": 110,
      "startup": 4,
      "active": 6,
      "recovery": 30,
      "invincibleFrames": 9,
      "hitboxOffsetX": 0,
      "hitboxOffsetY": 60,
      "hitboxWidth": 80,
      "hitboxHeight": 150
    }
  ],
  "throwMove": {
    "id": "shoulder_throw",
    "damage": 150,
    "startup": 3,
    "active": 2,
    "recovery": 22,
    "hitboxOffsetX": 0,
    "hitboxOffsetY": 40,
    "hitboxWidth": 50,
    "hitboxHeight": 150
  }
}
```

### フィールド

| フィールド | 型 | 必須 | 意味 |
|---|---|---|---|
| `id` | string | ✅ | キャラ一意 ID（ファイル名と一致推奨） |
| `name` | string | ✅ | 表示名 |
| `hp` | int | ✅ | 最大 HP |
| `walkSpeed` | float | ✅ | 歩行速度（px/frame） |
| `jumpPower` | float | ✅ | ジャンプ初速（px/frame, 上向き正） |
| `airJumps` | int | 任意 | 空中での追加ジャンプ回数（二段ジャンプ, Task 68）。省略時 `0`＝空中ジャンプなし（後方互換）。`1` で地上ジャンプ後に空中でもう一度跳べる（接地で回復）。負値は 0 に丸め |
| `airDashes` | int | 任意 | 空中ダッシュの回数（air dash, Task 69）。省略時 `0`＝空中ダッシュなし（後方互換）。`1` で滞空中の方向二度押しで水平バーストダッシュ（接地で回復）。負値は 0 に丸め |
| `width` | float | ✅ | キャラ矩形の横幅（px。描画 / 当たり判定の基準） |
| `height` | float | ✅ | キャラ矩形の高さ（px。描画 / 当たり判定の基準） |
| `color` | float[3] | 任意 | 表示色 RGB（0..1）。`sprite` 未指定時のプレースホルダ矩形色（未設定なら描画側の既定色） |
| `sprite` | Sprite | 任意 | スプライト（描画用画像）定義（Task 34）。省略時は従来どおりプレースホルダ矩形で描画（後方互換） |
| `normalMoves` | Move[] | ✅ | 通常技配列（1 件以上）。各技の `button` で弱/中/強を区別 |
| `specialMoves` | Move[] | 任意 | 必殺技配列（省略可）。各技の `command` でコマンド種別を指定 |
| `throwMove` | Move | 任意 | 投げ技（ガード不能の近接掴み, Task 35）。省略時はそのキャラは投げを持たない（後方互換）。`button` / `command` / `guardHeight` は不要 |
| `airThrowMove` | Move | 任意 | 空中投げ（滞空中の相手専用のガード不能掴み, Task 70）。省略時はそのキャラは空中投げを持たない（後方互換）。`throwMove` と同型の grab box で、地上投げが地上の相手のみ掴めるのに対し空中投げは滞空中の相手のみ掴める。`button` / `command` / `guardHeight` は不要 |
| `dashAttack` | Move | 任意 | ダッシュ攻撃（ダッシュ中の攻撃で出る突進打撃, Task 65）。省略時はそのキャラはダッシュ攻撃を持たず、ダッシュ中の攻撃は従来どおり通常技へキャンセルされる（後方互換）。`button` / `command` は不要（発動はダッシュ＋攻撃入力）。打撃なので `guardHeight`（既定 `mid`）は有効 |

### Sprite（`sprite` オブジェクト・Task 34）

キャラの描画用スプライトシート（格子状に並んだフレーム画像 1 枚の PNG）を指定する。`Texture` の読み込み・フレーム
切り出しは描画側（`GameRuntime/Rendering/SpriteLibrary`）が担い、本定義は**画像パスとレイアウトの単一の真実**のみを持つ。
シートは `frameWidth`×`frameHeight` の等間隔グリッドとして解釈し、各アニメーション状態を `stateRows` で行に、フレーム
（列）番号を実行時のアニメーション進行（`FighterAnimator`）に対応づける。PNG 欠落・読み込み失敗時はプレースホルダ
矩形へフォールバックする（バリデーションは形状のみ検証し、実在チェックは描画側に委ねる）。

| フィールド | 型 | 必須 | 意味 |
|---|---|---|---|
| `path` | string | ✅ | スプライトシート PNG のパス（`Assets/` ルート＝クラスパス相対。例 `"Characters/fighter001.png"`） |
| `frameWidth` | int | ✅ | 1 フレーム（セル）の横幅（px, 正） |
| `frameHeight` | int | ✅ | 1 フレーム（セル）の高さ（px, 正） |
| `stateRows` | SpriteStateRow[] | 任意 | アニメーション状態 → シート行番号の対応。未マップ状態は行 0（待機）へフォールバック |

SpriteStateRow 要素：`state`（string・必須・アニメ状態の小文字ラベル）/ `row`（int・0 以上・シート上の行番号）。
状態ラベルは `idle` / `walk` / `jump` / `attack` / `jump_attack` / `throw` / `hitstun` / `guard` / `crouch` / `crouch_walk` /
`crouch_attack` / `crouch_guard`（実装の真実は `GameRuntime/Rendering/AnimationState`）。向きは右向きを基準とし、
左向きは描画側が水平反転する（シートには右向きフレームのみ用意する）。

> 前方互換のため未知フィールドはロード時に無視する（`setIgnoreUnknownFields(true)`）。

### Move（`normalMoves[]` 要素）

| フィールド | 型 | 必須 | 意味 |
|---|---|---|---|
| `id` | string | ✅ | 技 ID |
| `button` | string | ✅ | ボタン種別：`"light"` / `"medium"` / `"heavy"` |
| `damage` | int | ✅ | ダメージ |
| `startup` | int | ✅ | 発生フレーム（攻撃判定が出るまで） |
| `active` | int | ✅ | 攻撃判定の持続フレーム |
| `recovery` | int | ✅ | 硬直フレーム |
| `hitboxOffsetX` | float | ✅ | hitbox の前方オフセット（キャラ前面からの距離, px） |
| `hitboxOffsetY` | float | ✅ | hitbox の足元からの高さ（px） |
| `hitboxWidth` | float | ✅ | hitbox の横幅（px） |
| `hitboxHeight` | float | ✅ | hitbox の高さ（px） |
| `guardHeight` | string | 任意 | ガード高さ属性：`"overhead"`（上段・立ちガードのみ可）/ `"mid"`（中段・両ガード可, **既定**）/ `"low"`（下段・しゃがみガードのみ可）。省略時 `"mid"`（Task 33） |
| `knockdown` | bool | 任意 | `true` の技を**非ガード**でヒットさせると相手をダウンさせる（通常のけぞりの代わり・ダウン中は被弾無敵＝OTG なし・Task 60）。省略時 `false`（後方互換）。投げ・飛び道具は対象外（打撃ヒットのみ） |
| `hits` | int | 任意 | 多段ヒット数（Task 74）。active 区間中に最大何回ヒットさせるか。省略時 `1`（単発・後方互換）。2 以上で `hitGap` 間隔の多段技になる（各サブヒットはコンボに加算＝コンボ補正 Task 46 が乗る） |
| `hitGap` | int | 任意 | 多段ヒットのサブヒット間隔（フレーム数・Task 74）。省略時 `4`。`hits == 1`（単発）では無視。`active` は `hitGap × (hits-1)` 以上が必要（全段当てるため） |

### Move（`specialMoves[]` 要素）

| フィールド | 型 | 必須 | 意味 |
|---|---|---|---|
| `id` | string | ✅ | 技 ID |
| `command` | string | ✅ | コマンド種別：`"HADOUKEN"` / `"CHARGE_SHOT"` / `"DOWN_ATTACK"`（`Command` enum の name） |
| `damage` | int | ✅ | ダメージ |
| `startup` | int | ✅ | 発生フレーム |
| `active` | int | ✅ | 持続フレーム |
| `recovery` | int | ✅ | 硬直フレーム |
| `hitboxOffsetX` | float | ✅ | hitbox の前方オフセット（px） |
| `hitboxOffsetY` | float | ✅ | hitbox の足元高さ（px） |
| `hitboxWidth` | float | ✅ | hitbox 横幅（px）。飛び道具は弾サイズ兼用 |
| `hitboxHeight` | float | ✅ | hitbox 高さ（px） |
| `projectile` | bool | 任意 | 飛び道具として発射するか（既定 false）。`false`（既定）の必殺技は **打撃必殺技**＝発生時に近接 hitbox を出す（昇龍拳タイプ。Task 53） |
| `projectileSpeed` | float | 任意* | 飛び道具の速度（px/frame）。`projectile=true` なら必須 |
| `invincibleFrames` | int | 任意 | 技の発生からこのフレーム数だけ食らい判定を失う（リバーサル / 対空・**Task 53**）。0（既定）＝無敵なし。打撃必殺技に付けると無敵対空になる（被弾・被弾飛び道具を無効化）。旧 JSON はキー無しで 0（後方互換） |
| `guardHeight` | string | 任意 | ガード高さ属性（`overhead` / `mid` / `low`、既定 `mid`）。飛び道具は既定の `mid` 運用（Task 33） |
| `knockdown` | bool | 任意 | `true` で非ガードヒット時に相手をダウンさせる（Task 60・既定 false）。打撃必殺技に付けると有効（飛び道具のダウンは将来対応＝現状は `resolveHit` の打撃ヒットのみ参照） |

> hitbox 矩形は「前方の前面・足元」を原点とする相対座標で、向きに応じて左右反転する（実装は `Shared/Types.Move`）。飛び道具技は hitbox 寸法を弾サイズとして使い、body 付随判定は持たない（ダメージは弾が運ぶ）。hurtbox / pushbox は MVP ではキャラ矩形（`width`/`height`）を用いる（`Shared/Types.Hurtbox`/`PushBox`、Task 12）。

### Move（`throwMove` オブジェクト・Task 35）

投げ技（**ガード不能の近接掴み**）の定義。`Move` を再利用するが、投げは専用の投げボタンで起動しガードを無視するため
**`button` / `command` / `guardHeight` は不要**（あっても無視される）。`hitboxWidth`/`hitboxHeight`/`hitboxOffsetX/Y` は
「掴み判定（grab box）」を表す。狭い `hitboxWidth` で近接限定にする。投げは中段/下段の区別を持たず、立ち・しゃがみどちらの
ガードでも貫通する（成立は active 区間中に grab box が相手 hurtbox に重なるか・かつ相手が地上にいるか）。

| フィールド | 型 | 必須 | 意味 |
|---|---|---|---|
| `id` | string | ✅ | 技 ID |
| `damage` | int | ✅ | ダメージ（ガード不能のためフルダメージが通る） |
| `startup` | int | ✅ | 発生フレーム（掴みが出るまで） |
| `active` | int | ✅ | 掴み判定の持続フレーム |
| `recovery` | int | ✅ | 硬直フレーム（空振り時の隙） |
| `hitboxOffsetX` | float | ✅ | grab box の前方オフセット（px） |
| `hitboxOffsetY` | float | ✅ | grab box の足元からの高さ（px） |
| `hitboxWidth` | float | ✅ | grab box 横幅（px。狭くして近接限定に） |
| `hitboxHeight` | float | ✅ | grab box 高さ（px） |

> 投げは空中の相手を掴めない（相手がジャンプ中なら不成立 = 隙）。`button`/`command`/`guardHeight` を持たない点が `normalMoves`/`specialMoves` との違い。

### Move（`dashAttack` オブジェクト・Task 65）

ダッシュ攻撃（**ダッシュ中の攻撃で出る突進打撃**）の定義。`Move` を再利用する。`button` では選択されず（発動は
ダッシュ＝二度押しステップ・Task 49 の最中に攻撃ボタンを押すこと）ため **`button` / `command` は不要**だが、
通常の打撃と同じ当たり判定なので **`guardHeight`（既定 `mid`）は有効**。発動するとダッシュの勢いを引き継ぐ前方への突進
（初速 `DASH_ATTACK_LUNGE_SPEED`＝14px/frame・`KNOCKBACK_FRICTION` で減衰）が乗り、攻撃しながら踏み込む。

| フィールド | 型 | 必須 | 意味 |
|---|---|---|---|
| `id` | string | ✅ | 技 ID |
| `guardHeight` | string | 任意 | ガード高さ属性（`overhead`/`mid`/`low`、既定 `mid`） |
| `damage` | int | ✅ | ダメージ |
| `startup` | int | ✅ | 発生フレーム |
| `active` | int | ✅ | 持続フレーム |
| `recovery` | int | ✅ | 硬直フレーム |
| `hitboxOffsetX` | float | ✅ | hitbox の前方オフセット（px。突進なので前方に取ると届きやすい） |
| `hitboxOffsetY` | float | ✅ | hitbox の足元からの高さ（px） |
| `hitboxWidth` | float | ✅ | hitbox 横幅（px） |
| `hitboxHeight` | float | ✅ | hitbox 高さ（px） |

> ダッシュ攻撃はダッシュ中の攻撃入力でのみ発動し、通常技のチェーン/特殊キャンセル元にはならない（`button` を持たないため）。
> 例：fighter004 Rai の `dash_shoulder`（damage 80・startup 7・active 5・前方 hitbox）。

### キー割当（Task 24 / Task 35）

| ボタン | P1 | P2 |
|---|---|---|
| 弱（light） | F | Numpad 1 |
| 中（medium） | G | Numpad 2 |
| 強（heavy） | H | Numpad 3 |
| 投げ（throw） | T | Numpad 0 |

---

## Stage（MVP 正式版・Task 17 / Task 40）

1 ステージ = 1 JSON（`Assets/Stages/<id>.json`）。`Shared/Types/Stage` へデシリアライズ（`StageLoader`）。
地面の高さ（物理基準）は `Shared/Constants.GROUND_Y` 固定で、本型は**見た目（背景）のみ**を担う（MVP）。
**ステージは加算式**で、コードを変えず JSON を追加するだけで増やせる（Task 40 で検証）。現状の収録ステージ：

| ID | 表示名 | 雰囲気 |
|---|---|---|
| `stage001`（既定） | Twilight Arena | 紫の夕暮れ |
| `stage002` | Verdant Glade | 青空＋緑地（Task 40） |

読み込むステージは既定で `stage001`。撮影時は **`phantom.screenshot.stage=<id>`（`-x stage=<id>`）** でオーバーライドできる（背景の撮り分け用。`PhantomNexusGame` が `ScreenshotController.stageId()` 経由で選択）。

サンプル（`Assets/Stages/stage002.json`）：

```json
{
  "id": "stage002",
  "name": "Verdant Glade",
  "skyTop": [0.18, 0.46, 0.78],
  "skyBottom": [0.62, 0.82, 0.94],
  "groundColor": [0.16, 0.40, 0.22]
}
```

| フィールド | 型 | 必須 | 意味 |
|---|---|---|---|
| `id` | string | ✅ | ステージ一意 ID |
| `name` | string | ✅ | 表示名 |
| `skyTop` | float[3] | ✅ | 空の上端色 RGB（各 0..1） |
| `skyBottom` | float[3] | ✅ | 空の下端（地平線側）色 RGB |
| `groundColor` | float[3] | ✅ | 地面色 RGB |

> 描画は空を下端→上端のグラデーションで塗り、地面を `groundColor` で塗る。スプライト背景 / パララックスは将来拡張。

---

## バリデーション方針（`Shared/Schema`）

- 必須フィールド欠落・型不一致・負値などはロード時にエラーとし、**どのファイル/フィールドが原因か**をログ出力する（第一設計書「JSON/YAML バリデーション」）。
- 不明な追加フィールドは将来拡張のため無視（前方互換）。

## 変更履歴

- (Task 74) `Move` に任意 int `hits`（既定 1・後方互換）・`hitGap`（既定 4）を追加。`hits >= 2` の技は active 区間中に `hitGap` フレーム間隔で複数回ヒットする多段技になる（各サブヒットはコンボに加算＝コンボ補正 Task 46 が乗る）。`getHits()` は最小 1・`getHitGap()` は最小 0 に丸める。任意フィールドのためローダ追加検証なし（旧 JSON はキー無しで単発）。例示として fighter009 の中攻撃を 2 段技 `twin_thrust`（`hits:2`・`hitGap:4`・active9）に。戦闘仕様は BattleSystem.md「多段ヒット技（Task 74）」節を参照。`Move` の normal/special 両フィールド表に `hits`/`hitGap` を追加。
- (Task 72) 9 体目キャラ `Assets/Characters/fighter009.json`（"Hayato"・HP1050・**高 HP の charge zoner 型**の gold）＋プレースホルダ・スプライト `fighter009.png` を追加。`Character` の JSON 仕様は不変で、JSON＋PNG の追加だけでキャラが動作する（8 体目 fighter008 に続く 9 体目）。アーキタイプ：遅い `walkSpeed4.2`・長射程の通常技（`jab`/`twin_thrust`/`heavy_lance`）・`CHARGE_SHOT` 飛び道具 `charge_beam`（溜め式の弾＝HADOUKEN とは別モーション）・強攻撃 `heavy_lance` に `knockdown:true`。AI はデータ駆動の飛び道具牽制（Task 64）でこの弾を撃って zoning する。撮影は `-x p1char=fighter009` / `-x p2char=fighter009`。

- (Task 70) `Character` に任意フィールド **`airThrowMove`**（Move・省略可）を追加。空中投げ（滞空中の相手専用のガード不能掴み・Task 70）で、`throwMove`（地上投げ）と同型の grab box を持つ。省略時はそのキャラは空中投げを持たない（後方互換）。検証は `throwMove` と同じ `validateThrowMove` を流用。例示として fighter004 Rai に `airThrowMove`（`sky_grab`・dmg110）を付与（高機動ラッシュ＋空対空の掴み）。戦闘仕様は BattleSystem.md「空中投げ（Task 70）」節を参照。
- (Task 69) `Character` に任意フィールド **`airDashes`**（int・既定 0）を追加。空中ダッシュの回数（air dash・Task 69）で、`1` なら滞空中の方向二度押しで水平バーストダッシュ（接地で回復）。省略時 0＝空中ダッシュなし（後方互換）。`getAirDashes()` が負値を 0 に丸める。例示として fighter004 Rai に `airDashes: 1` を付与（二段ジャンプ＋空中ダッシュ＝高機動ラッシュ型）。戦闘仕様は BattleSystem.md「空中ダッシュ（Task 69）」節を参照。
- (Task 68) `Character` に任意フィールド **`airJumps`**（int・既定 0）を追加。空中での追加ジャンプ回数（二段ジャンプ・Task 68）で、`1` なら地上ジャンプ後に空中でもう一度跳べる（接地で回復）。省略時 0＝空中ジャンプなし（後方互換）。`getAirJumps()` が負値を 0 に丸める。例示として fighter004 Rai に `airJumps: 1` を付与（高速ラッシュ＋空中機動）。戦闘仕様は BattleSystem.md「二段ジャンプ（Task 68）」節を参照。
- (Task 67) 8 体目キャラ `Assets/Characters/fighter008.json`（"Ren"・HP880・**ダッシュ起き攻め rushdown 型**の green）＋プレースホルダ・スプライト `fighter008.png` を追加。`Character` の JSON 仕様は不変で、**ソースコード（Java）は無改変・JSON＋PNG の追加だけでキャラが動作する**ことを再々々々々検証（7 体目 fighter007 に続く 8 体目）。アーキタイプを差別化：速い `walkSpeed5.8` で前進し、**`dashAttack`（Task 65）に `knockdown:true`（Task 60）を載せた `dash_blitz`**（ダッシュ突進でダウンを奪い起き攻めへ移行）を軸に、強攻撃 `rising_crush`・飛び道具 `gale_shot`（HADOUKEN）・投げ `collar_toss` を持つ攻め寄り。**新キャラが `dashAttack`（Task 65）・`knockdown`（Task 60）・飛び道具・投げといった既存機構を、技 JSON にフィールドを足すだけでコード変更なしに享受できる**ことをスクショで実証（Ren の `dash_blitz` 命中で相手 `knockdown`・80＝ダッシュ攻撃＋ダウンの複合がデータ駆動で成立）。撮影は `-x p1char=fighter008` / `-x p2char=fighter008`。
- (Task 65) `Character` に任意フィールド **`dashAttack`**（`Move`）を追加。ダッシュ（二度押しステップ・Task 49）中に攻撃ボタンを押すと出る**突進打撃**で、省略時はそのキャラはダッシュ攻撃を持たず、ダッシュ中の攻撃は従来どおり通常技へキャンセルされる（後方互換）。`button` / `command` は不要（発動はダッシュ＋攻撃入力）だが、打撃なので `guardHeight`（既定 `mid`）は有効。`CharacterLoader.validateDashAttack` がフレーム値・hitbox・guardHeight を検証（`null` は許可）。発動するとダッシュの勢いを引き継ぐ前方突進（初速 `DASH_ATTACK_LUNGE_SPEED`=14px/frame・`KNOCKBACK_FRICTION` 減衰）が乗る。例示として fighter004 Rai に `dash_shoulder`（damage80・startup7・active5・前方 hitbox）を追加。「Move（`dashAttack` オブジェクト・Task 65）」節と `Character` フィールド表を追加。戦闘仕様は BattleSystem.md「ダッシュ攻撃（Task 65）」節を参照。
- (Task 61) 7 体目キャラ `Assets/Characters/fighter007.json`（"Kaede"・HP950・**飛び道具＋ knockdown heavy・無敵リバーサルなしの footsies 型**の magenta）＋プレースホルダ・スプライト `fighter007.png` を追加。`Character` の JSON 仕様は不変で、**ソースコード（Java）は無改変・JSON＋PNG の追加だけでキャラが動作する**ことを再々々々検証（6 体目 fighter006 に続く 7 体目）。アーキタイプを差別化：中リーチの通常技（`jab`/`poke`/`roundhouse`）でスペース管理する footsies 型、強攻撃 `roundhouse` に **`knockdown: true`**（Task 60）を付けて非ガードヒットでダウンを奪う＝**新キャラの技に knockdown フラグを足すだけでダウン技が増える**データ駆動の実例、飛び道具 `wind_shot`（HADOUKEN・`projectileSpeed 12.0`）を持つが**無敵リバーサルは持たない**（飛び道具持ちで初の「リバーサルなし」＝neutral/spacing 偏重）。**新キャラがコード変更なしで飛び道具・投げ・ダウン（Task 60）等の既存機構をそのまま使える**ことをスクショで確認（`roundhouse` 非ガードヒットで相手 `knockdown`・115／236+A で `wind_shot` 発射）。撮影は `-x p1char=fighter007` / `-x p2char=fighter007`。
- (Task 60) ダウン（knockdown）を追記。`Move` に任意 boolean `knockdown`（既定 false・後方互換）を追加：`true` の技を**非ガード**でヒットさせると相手をダウンさせる（通常のけぞりの代わり・`KNOCKDOWN_FRAMES`(60) 行動不能・ダウン中は被弾無敵＝OTG なし）。旧 JSON はキー無しで false＝通常のけぞり（`invincibleFrames` と同じフィールド初期化子による後方互換）。`CharacterLoader` は任意 boolean のため追加検証不要。例示として `fighter001` の `heavy_slam`（overhead 強攻撃）に `knockdown: true` を付与（非ガードヒットでダウン＝強攻撃の見返り）。戦闘仕様は BattleSystem.md「ダウン（knockdown）（Task 60）」節を参照。飛び道具のダウンは将来対応。`Move` の normal/special 両フィールド表に `knockdown` を追加。
- (Task 58) 6 体目キャラ `Assets/Characters/fighter006.json`（"Iwao"・HP1200・**飛び道具を持たない純グラップラー型**の crimson）＋プレースホルダ・スプライト `fighter006.png` を追加。`Character` の JSON 仕様は不変で、**ソースコード（Java）は無改変・JSON＋PNG の追加だけでキャラが動作する**ことを再々々検証（5 体目 fighter005 に続く 6 体目）。アーキタイプを差別化：最高 HP1200・最遅 `walkSpeed3.0`・低ジャンプで重量級、通常技（`hammer_jab`/`shoulder_ram`/`ground_pound`）は短リーチ高火力、投げ `back_breaker` は**ロスター最大の dmg175**、`specialMoves` は無敵リバーサル `rising_hammer`（`CHARGE_SHOT`・`invincibleFrames:10`・dmg115）**1 件のみ**＝**飛び道具（`projectile`）を 1 つも持たない初のキャラ**（既存 5 体は全員 HADOUKEN 飛び道具持ち）。`specialMoves` は任意・CHARGE_SHOT 非飛び道具技は既存検証を通る（rising_talon と同型）ため**ローダ無改修**で成立。**新キャラが飛び道具を持たなくても、データ駆動 AI の無敵対空（Task 55）が `rising_hammer` を無改修で対空に使い、投げ崩し（Task 37）・投げ抜け（Task 51）等の既存反応もそのまま効く**ことをスクショで確認（P1 Iwao の `back_breaker` で 175・AI(P2)Iwao の `rising_hammer` 対空で飛び込みを 115 で迎撃）。撮影は `-x p1char=fighter006` / `-x p2char=fighter006`。
- (Task 55) `fighter002.json`（Akane）に 2 つ目の必殺技 `rising_talon`（`CHARGE_SHOT`・打撃＝`projectile` 無し・`invincibleFrames:8`・dmg95）を追加（無敵打撃必殺技＝Task 53 のスキーマを流用）。これは AI の無敵対空（Task 55・戦闘仕様は BattleSystem.md）で AI が使う技で、`Character` の JSON 仕様は不変。「キャラ JSON に無敵打撃技を足すだけで AI もそれで対空する」データ駆動を実証。
- (Task 53) 無敵リバーサル必殺技を追記。`Move` に任意フィールド `invincibleFrames`（int・既定 0）を追加：技発生からこのフレーム数だけ食らい判定を失う（リバーサル / 対空）。旧 JSON はキー無しで 0（後方互換）。あわせて **打撃必殺技**（`projectile=false` の必殺技＝発生時に近接 hitbox を出す）の運用を明文化（実装上は Task 20 から動作。fighter001 が初の実例）。`fighter001.json`（Aoi）に 2 つ目の必殺技 `rising_dragon`（command `CHARGE_SHOT`・`projectile` 無し＝打撃・`invincibleFrames: 9`・dmg110・startup4/active6/recovery30）を追加し、飛び道具（`fireball`=HADOUKEN）＋無敵対空（`rising_dragon`=CHARGE_SHOT）の 2 系統コマンドを持つキャラを実証（撮影で `<CHARGE (hold 4, 6+A)>` 成立・`special:active [INV]`・相手の攻撃を抜いて 110 反撃）。`Move` のフィールド表に `invincibleFrames` を追加。
- (Task 52) 5 体目キャラ `Assets/Characters/fighter005.json`（"Sora"・HP750・遠距離 zoner 型の青緑）＋プレースホルダ・スプライト `fighter005.png` を追加。`Character` の JSON 仕様は不変で、**ソースコード（Java）は無改変・実行時はデータ資産（JSON＋プレースホルダ PNG）の追加だけでキャラが動作する**ことを再々検証（4 体目 fighter004 に続く 5 体目）。※本 PR には docs（README/CLAUDE/本書）・証跡スクショ等の補助ファイルも含むが、これらは実行時には不要（ランタイムに必要なのは JSON＋PNG の 2 ファイルのみ）。アーキタイプを差別化：通常技（`needle`/`lance`/`skewer`）は **hitboxWidth が 104/124/144 と長射程**でリーチ重視、必殺 `frost_shard`（飛び道具・`projectileSpeed 14.0` と高速）・投げ `spire_toss`（dmg100）、HP は低め（750）。**新キャラがコード変更なしで必殺技・投げ・既存戦闘機構をそのまま使える**ことをスクショで確認（236+A で frost_shard 発射／`skewer` の長い hitbox が 200px の間合いを抜いて 85 ダメージ）。撮影は `-x p1char=fighter005`。
- (Bootstrap) 第一設計書の共通データ仕様に基づく初版ドラフトを作成。
- (Task 6) `Shared/Types/Character` POJO を新設し、描画 / 当たり判定の基準となる `width` / `height` を追加（実装は id/name/hp/walkSpeed/jumpPower/width/height のサブセット。animations/moves/hitbox は Task 15 で正式化）。
- (Task 11) `Shared/Types/Move` POJO を新設（id/command/damage/startup/active/recovery + hitbox 矩形）。`Character` に `normalAttack`（通常攻撃 1 技）を追加。MVP はコード生成で供給し、Task 15/16 で JSON の moves[] から供給する。
- (Task 12) `Shared/Types` に `Hitbox`/`Hurtbox`/`PushBox`（ワールド座標 AABB）を新設（当たり判定の実行時矩形）。
- (Task 14) `Shared/Types/BattleRules` POJO を新設（timeLimitSeconds / rounds）。MVP はコード生成、将来 JSON 化。
- (Task 15) Character JSON の MVP 正式版を確定（flat な Character + `normalAttack` オブジェクト）。`Assets/Characters/fighter001.json`・`fighter002.json` を追加。読み込みは Task 16。
- (Task 16) `Shared/Schema/CharacterLoader`（LibGDX `Json`・未知フィールド無視）と `SchemaException`（原因ファイル/フィールド明示）を新設。Core はコード生成をやめ `CharacterLoader.load(id)` から供給。必須欠落・非正値・total フレーム 0 などを検証。
- (Task 17) `Shared/Types/Stage` と `Shared/Schema/StageLoader` を新設。`Assets/Stages/stage001.json` を追加し、背景（空グラデ + 地面色）を JSON 駆動で描画。色は RGB float[3]（0..1）。
- (Task 19) コマンド入力検出を `GameRuntime/Input`（`InputHistory`/`CommandDetector`/`Command`）に実装（波動拳=236+A・溜め・下+A）。MVP はコマンド定義をコード側に持つ（`Commands` の JSON 化は将来）。撮影用にタイムド入力スクリプト（`phantom.screenshot.script`）を追加。
- (Task 20) `Move` に `projectile`/`projectileSpeed`、`Character` に `specialMove` を追加。fighter JSON に必殺技（fireball, 飛び道具）を追加。`CharacterLoader` は specialMove を任意検証。
- (Task 22) `Character` に表示色 `color`（RGB float[3], 任意）を追加。2 体目（fighter002 Akane）を別ステータス（HP 850・高速・小柄）・別色・別技に再定義し、**コード変更なし・JSON のみでキャラが変わる**ことを検証。
- (Task 24) 技定義を **配列** に拡張。`normalAttack`（単技）→ `normalMoves[]`（弱/中/強 3 種）、`specialMove`（単技）→ `specialMoves[]`（複数必殺技対応）。`Move` に `button` フィールドを追加（通常技のボタン種別）。`command` フィールドを必殺技のコマンド名（`Command.name()`）として正式化。`InputAction` に `ATTACK_LIGHT`/`ATTACK_MEDIUM`/`ATTACK_HEAVY` を追加し旧 `ATTACK` を廃止。
- (Task 33) `Move` に **ガード高さ属性 `guardHeight`**（string, 任意, 既定 `"mid"`）を追加。許可値は `overhead`/`mid`/`low` で、`CharacterLoader` が正規化済み getter 値を検証する。未指定の旧 JSON はフィールド初期化子により `"mid"` 扱い（後方互換）。例示として fighter001 の `heavy_slam` を `overhead` 化し、hitbox を `offsetY 60 / height 90` に下げてしゃがみ hurtbox に届くようにした。
- (Task 34) `Character` に **スプライト定義 `sprite`**（任意・`Sprite` 型）を追加。`path`（PNG・クラスパス相対）/ `frameWidth` / `frameHeight` / `stateRows[]`（アニメ状態→行）を持ち、描画側 `SpriteLibrary` がシートを切り出して `FighterAnimator` の状態・フレームに同期描画する。未指定の旧 JSON はフィールド初期化子（`null`）により従来のプレースホルダ矩形へフォールバック（後方互換）。`Shared/Types` に `Sprite` / `SpriteStateRow` を新設し、`CharacterLoader` で形状（path 非空・寸法正・row 非負）を検証。検証用にプレースホルダ・スプライトシート（`Assets/Characters/fighter001.png` / `fighter002.png`・64×128 セル × 4 列 × 7 行）を同梱し fighter001/002 から参照。
- (refactor) `guardHeight` の正準値を `Shared/Types/GuardHeight` enum（`OVERHEAD`/`MID`/`LOW`）に集約。散在していた文字列リテラルと `CharacterLoader.VALID_GUARD_HEIGHTS` セットを廃し、解釈・既定・検証を `GuardHeight.fromToken(String)` に一元化した。**JSON 形式（小文字トークン `overhead`/`mid`/`low`・未指定は `mid`）は不変・後方互換**で、本書のフィールド仕様に変更はない（内部実装のみのリファクタ）。
- (Task 35) `Character` に **投げ技 `throwMove`**（任意・`Move` 型）を追加。ガード不能の近接掴みで、`button`/`command`/`guardHeight` を持たない（専用の投げボタンで起動しガードを無視する）。`CharacterLoader.validateThrowMove()` が id / フレーム / hitbox 寸法のみ検証（button/command/guardHeight は検証しない）。未指定の旧 JSON はフィールド初期化子（`null`）により投げを持たない（後方互換）。`AnimationState` に `throw` ラベルを追加。`InputAction.THROW`（P1=T / P2=Numpad0）を追加。fighter001（`shoulder_throw`・dmg150）/ fighter002（`arm_toss`・dmg130）に `throwMove` と sprite `throw` 行を追加。
- (refactor) `button` の正準値を `Shared/Types/AttackButton` enum（`LIGHT`/`MEDIUM`/`HEAVY`）に集約（`GuardHeight` と同パターン）。散在していた文字列リテラル（Core のボタン構築・`Fighter.selectNormalMove` の equalsIgnoreCase 照合・`CharacterLoader.VALID_BUTTONS` セット・`AiController` の `"light"` 直書き）を廃し、解釈・検証を `AttackButton.fromToken(String)` に一元化した。`guardHeight`（任意）と異なり `button` は必須のため `fromToken` は未指定で `null` を返し、ローダの必須チェックが弾く。**JSON 形式（小文字トークン `light`/`medium`/`heavy`・必須）は不変・後方互換**で、本書のフィールド仕様に変更はない（内部実装のみのリファクタ）。
- (Task 48) 4 体目キャラ `Assets/Characters/fighter004.json`（"Rai"・HP800・高速・小柄の黄緑ラッシュ型）＋プレースホルダ・スプライト `fighter004.png` を追加。`Character` の JSON 仕様は不変で、**コード変更なし・JSON 追加だけでキャラが増える**ことを再検証（Task 41 fighter003 に続く 4 体目）。技は低リカバリの通常（`jab`/`elbow`/`uppercut`）でチェーンコンボ（Task 45）が繋がりやすく、必殺 `spark_bolt`（飛び道具・高速）・投げ `quick_grab`（dmg120）。**新キャラがコード変更なしでチェーンコンボ／特殊キャンセル／コンボ補正／必殺技ゲージ等の既存機構をそのまま利用できる**（スクショで jab→elbow→uppercut の `3 HITS!`＝35/54/76 を確認）。撮影は `-x p1char=fighter004`。
- (Task 41) 3 体目キャラ `Assets/Characters/fighter003.json`（"Tetsu"・HP1150・低速・大柄の紫グラップラー）＋プレースホルダ・スプライト `fighter003.png` を追加。`Character` の JSON 仕様は不変で、**コード変更なし・JSON 追加だけでキャラが増える**ことを再検証（Task 22 fighter002 に続く 3 体目）。技は通常（`heavy_jab`/`body_blow`/`low_sweep`=`guardHeight: low`）・必殺（`iron_wave` 飛び道具）・投げ（`iron_buster`・dmg160）。撮影時は `phantom.screenshot.p1char=<id>` / `p2char=<id>`（`-x p1char` / `-x p2char`）で読み込むキャラを差し替え可能（`PhantomNexusGame` が `ScreenshotController.charId()` 経由で選択）。
- (Task 40) 第2ステージ `Assets/Stages/stage002.json`（"Verdant Glade"・青空＋緑地）を追加。Stage の JSON 仕様（`id`/`name`/`skyTop`/`skyBottom`/`groundColor`）は不変で、**コード変更なし・JSON 追加だけでステージが増える**ことを検証（Task 22 のキャラ版に相当するステージ版）。読み込むステージは既定 `stage001`、撮影時は `phantom.screenshot.stage=<id>`（`-x stage=<id>`）でオーバーライド可能（`PhantomNexusGame` が `ScreenshotController.stageId()` 経由で選択）。
