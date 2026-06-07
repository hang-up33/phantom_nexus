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

## Character（MVP 正式版・Task 15）

1 キャラ = 1 JSON ファイル（`Assets/Characters/<id>.json`、UTF-8）。LibGDX `Json` が POJO（`Shared/Types/Character`）へ
フィールド名一致でデシリアライズする（Task 16）。実サンプルは `Assets/Characters/fighter001.json` / `fighter002.json`。

```json
{
  "id": "fighter001",
  "name": "Aoi",
  "hp": 1000,
  "walkSpeed": 4.0,
  "jumpPower": 12.0,
  "width": 100,
  "height": 240,
  "normalAttack": {
    "id": "standing_punch",
    "command": "ATTACK",
    "damage": 80,
    "startup": 8,
    "active": 6,
    "recovery": 16,
    "hitboxOffsetX": 0,
    "hitboxOffsetY": 120,
    "hitboxWidth": 90,
    "hitboxHeight": 40
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
| `width` | float | ✅ | キャラ矩形の横幅（px。描画 / 当たり判定の基準） |
| `height` | float | ✅ | キャラ矩形の高さ（px。描画 / 当たり判定の基準） |
| `normalAttack` | object | ✅ | 通常攻撃 1 技（下記 Move）。MVP は 1 キャラ 1 技 |

> `animations`（スプライト）・複数 `moves[]` は MVP 後の拡張（スプライト導入 / コマンド技 Task 19）。前方互換のため未知フィールドはロード時に無視する。

### Move（`normalAttack`）

| フィールド | 型 | 必須 | 意味 |
|---|---|---|---|
| `id` | string | ✅ | 技 ID |
| `command` | string | ✅ | 発生コマンド（MVP は単キー名。Task 19 で波動拳等に拡張） |
| `damage` | int | ✅ | ダメージ |
| `startup` | int | ✅ | 発生フレーム（攻撃判定が出るまで） |
| `active` | int | ✅ | 攻撃判定の持続フレーム |
| `recovery` | int | ✅ | 硬直フレーム |
| `hitboxOffsetX` | float | ✅ | hitbox の前方オフセット（キャラ前面からの距離, px） |
| `hitboxOffsetY` | float | ✅ | hitbox の足元からの高さ（px） |
| `hitboxWidth` | float | ✅ | hitbox の横幅（px） |
| `hitboxHeight` | float | ✅ | hitbox の高さ（px） |

> hitbox 矩形は「前方の前面・足元」を原点とする相対座標で、向きに応じて左右反転する（実装は `Shared/Types.Move`）。hurtbox / pushbox は MVP ではキャラ矩形（`width`/`height`）を用いる（`Shared/Types.Hurtbox`/`PushBox`、Task 12）。

---

## Stage（MVP 正式版・Task 17）

1 ステージ = 1 JSON（`Assets/Stages/<id>.json`）。`Shared/Types/Stage` へデシリアライズ（`StageLoader`）。
地面の高さ（物理基準）は `Shared/Constants.GROUND_Y` 固定で、本型は**見た目（背景）のみ**を担う（MVP）。

```json
{
  "id": "stage001",
  "name": "Twilight Arena",
  "skyTop": [0.07, 0.08, 0.18],
  "skyBottom": [0.42, 0.28, 0.40],
  "groundColor": [0.16, 0.14, 0.20]
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

- (Bootstrap) 第一設計書の共通データ仕様に基づく初版ドラフトを作成。
- (Task 6) `Shared/Types/Character` POJO を新設し、描画 / 当たり判定の基準となる `width` / `height` を追加（実装は id/name/hp/walkSpeed/jumpPower/width/height のサブセット。animations/moves/hitbox は Task 15 で正式化）。
- (Task 11) `Shared/Types/Move` POJO を新設（id/command/damage/startup/active/recovery + hitbox 矩形）。`Character` に `normalAttack`（通常攻撃 1 技）を追加。MVP はコード生成で供給し、Task 15/16 で JSON の moves[] から供給する。
- (Task 12) `Shared/Types` に `Hitbox`/`Hurtbox`/`PushBox`（ワールド座標 AABB）を新設（当たり判定の実行時矩形）。
- (Task 14) `Shared/Types/BattleRules` POJO を新設（timeLimitSeconds / rounds）。MVP はコード生成、将来 JSON 化。
- (Task 15) Character JSON の MVP 正式版を確定（flat な Character + `normalAttack` オブジェクト）。`Assets/Characters/fighter001.json`・`fighter002.json` を追加。読み込みは Task 16。
- (Task 16) `Shared/Schema/CharacterLoader`（LibGDX `Json`・未知フィールド無視）と `SchemaException`（原因ファイル/フィールド明示）を新設。Core はコード生成をやめ `CharacterLoader.load(id)` から供給。必須欠落・非正値・total フレーム 0 などを検証。
- (Task 17) `Shared/Types/Stage` と `Shared/Schema/StageLoader` を新設。`Assets/Stages/stage001.json` を追加し、背景（空グラデ + 地面色）を JSON 駆動で描画。色は RGB float[3]（0..1）。
