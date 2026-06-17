# ステージ背景 デザイン仕様書

外部デザインツール（ClaudeDesign / Canva 等）で作成したステージアートを Phantom Nexus に組み込むための仕様書。キャラクターの [SpriteDesignSpec.md](SpriteDesignSpec.md) に対応する「ステージ版」。

ステージ背景には 2 方式がある：

1. **全画面 1 枚絵**（本書の主題）— 描いたアートを PNG 1 枚で背景に敷く。最も手軽で「絵を置くだけ」。
2. **手続き的な多層シルエット**（`layers`）— JSON のシェイプ（山/都市/柱/雲/雪 等）で組む奥行き背景。詳細は [DataFormat.md](DataFormat.md) の StageLayer 節。

外部デザインを取り込むなら **方式 1（全画面 1 枚絵）** を使う。

---

## 1. 出力ファイル形式

| 項目 | 値 |
|---|---|
| ファイル形式 | PNG（背景は不透明でよい。透過は不要） |
| 推奨ファイル名 | `stageNNN_bg.png`（NNN = ステージ番号） |
| 配置先 | `Assets/Stages/stageNNN_bg.png` |
| 解像度 | **1280 × 720 px**（ワールド解像度＝`GameConstants.WORLD_WIDTH`×`WORLD_HEIGHT`） |
| アスペクト比 | 16:9（他比率は変換スクリプトが cover で合わせる） |

---

## 2. 構図の目安

画像は全画面（1280×720）にそのまま引き伸ばして描かれる。レイアウトの目安：

```
┌──────────────────────────────────────────┐  y=720（上端）
│                  空・遠景                  │
│                                           │
│            （キャラはこの帯に立つ）         │
│  ─────────── 地平線 ≒ 画面下から 120px ──── │  ← 地面の基準（GROUND_Y=120）
│                  地面・床                  │
└──────────────────────────────────────────┘  y=0（下端）
```

- **地面ライン**：キャラの足元（接地）はワールド座標 `y=120`＝**画面下端から約 120px（全体の約 1/6）** の高さ。地面・床はこのラインより下に描くと自然。
- キャラクターは画面中央〜やや下に立つので、**中央下部にキャラを隠す細かい要素を置きすぎない**（読みづらくなる）。
- 上部（空・遠景）には HP バー等の HUD が重なる（上端の左右）。重要なモチーフは中央〜中段に。

---

## 3. 取り込み手順

### 方法 A：変換スクリプトを使う（推奨）

任意サイズの 1 枚絵を 1280×720 へ自動変換（cover＝縦横比維持で全面を埋め、はみ出しは中央トリミング）する：

```sh
scripts/make-stage-background.sh <元画像.png> Assets/Stages/stageNNN_bg.png
```

### 方法 B：すでに 1280×720 で書き出した場合

そのまま `Assets/Stages/stageNNN_bg.png` に配置すればよい（変換不要）。

### JSON への配線

ステージ JSON（`Assets/Stages/stageNNN.json`）に `background` を追加する：

```json
{
  "id": "stage011",
  "name": "Custom Art Arena",
  "skyTop": [0.11, 0.08, 0.22],
  "skyBottom": [0.91, 0.50, 0.28],
  "groundColor": [0.13, 0.09, 0.16],
  "background": "Stages/stage011_bg.png"
}
```

- `background` のパスは **`Assets/` ルートからのクラスパス相対**（先頭に `Assets/` は付けない）。
- `skyTop`/`skyBottom`/`groundColor` は**必須**（画像が読めなかった場合のフォールバック背景に使われる）。画像が主役でもダミー値でよいので必ず入れる。
- `layers` は `background` 指定時は描かれない（1 枚絵が前景込みで完結する想定）。手続き背景に戻したいときは `background` を消す。

---

## 4. 既存ステージにアートを付ける

既存の `stageNNN.json` に `background` 行を足すだけで、そのステージの背景が手続き描画から 1 枚絵に切り替わる。`background` を消せば元の手続き背景に戻る（非破壊）。

---

## 5. アセット組み込みチェックリスト

新しいステージアートを受け取ったら：

- [ ] `scripts/make-stage-background.sh <元画像> Assets/Stages/stageNNN_bg.png` で 1280×720 に変換
- [ ] `Assets/Stages/stageNNN.json` に `"background": "Stages/stageNNN_bg.png"` を追加（色フィールドは残す）
- [ ] `./gradlew build` でリソースが取り込まれることを確認
- [ ] `scripts/capture-app-screenshot-linux.sh -x stage=stageNNN -f 60 -o docs/screenshots/xxx.png` で目視確認
- [ ] 選択ロスターに出すなら `PhantomNexusGame.STAGE_IDS` に id を追記（出さず撮影だけなら不要）

---

## 6. 仕組み（参考）

- データ（背景パス）の単一の真実は `Shared/Types/Stage.background`。`StageLoader` は `setIgnoreUnknownFields` で吸収し追加検証しない（実在チェックは描画層に委ねる）。
- 描画は `GameRuntime/Rendering/StageBackgroundLibrary` が PNG を `Texture` として 1 度だけ読み込みキャッシュ（線形フィルタで全画面に拡大）。読み込み失敗・欠落・未指定は `null` を返し、`GameRenderer` が手続き背景へフォールバックする（キャラの `SpriteLibrary` と同方針）。
- バトル・ステージ選択プレビューの両方で 1 枚絵が表示される。足元の影・必殺技ゲージのオーラは画像の上にも重なる（キャラが地に足を着けて見える）。
