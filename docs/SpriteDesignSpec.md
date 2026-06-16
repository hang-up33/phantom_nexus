# キャラクタースプライト デザイン仕様書

外部デザインツール（ClaudeDesign / Canva 等）で作成したキャラクターアートを Phantom Nexus に組み込むための仕様書。

---

## 1. 出力ファイル形式

| 項目 | 値 |
|---|---|
| ファイル形式 | PNG（透過背景）|
| ファイル名 | `fighter00N.png`（N = 1〜10）|
| 配置先 | `Assets/Characters/fighter00N.png` |

---

## 2. スプライトシート形式（推奨）

キャラクターアニメーションはグリッド状の **スプライトシート** 1 枚で管理する。

```
┌──────────────────────────────────────────┐
│  [F0]     [F1]     [F2]     [F3]         │  ← Row 0: idle（待機）
│  [F0]     [F1]     [F2]     [F3]         │  ← Row 1: walk（歩き）
│  [F0]     [F1]     [F2]     [F3]         │  ← Row 2: jump（ジャンプ）
│  [F0]     [F1]     [F2]     [F3]         │  ← Row 3: attack（攻撃）
│  [F0]     [F1]     [F2]     [F3]         │  ← Row 4: hitstun（被弾）
│  [F0]     [F1]     [F2]     [F3]         │  ← Row 5: guard（ガード）
│  [F0]     [F1]     [F2]     [F3]         │  ← Row 6: crouch（しゃがみ）
└──────────────────────────────────────────┘
```

| 項目 | 既定値 | 備考 |
|---|---|---|
| シート全体サイズ | 256 × 896 px | 変更可（JSON で指定） |
| 1セル（フレーム）サイズ | 64 × 128 px | 変更可（JSON で指定） |
| 列数（フレーム数） | 4列 | 1アニメーションあたりのコマ数 |
| 行数（状態数） | 7行 | idle/walk/jump/attack/hitstun/guard/crouch |
| キャラクターの向き | **右向き**（左向きはエンジン側で反転） |  |
| 背景 | **透過**（アルファチャンネル必須） | |

### 各状態のポーズ目安

| Row | 状態 | ポーズ目安 |
|---|---|---|
| 0 | idle | 直立・構え（4コマで呼吸など微動） |
| 1 | walk | 歩行（4コマで脚の交互） |
| 2 | jump | 滞空（4コマで体の傾き変化） |
| 3 | attack | 打撃（溜め→伸ばし→戻し） |
| 4 | hitstun | のけぞり（4コマで揺れ） |
| 5 | guard | 防御（腕を前に立てる） |
| 6 | crouch | しゃがみ（低姿勢） |

---

## 3. 簡易版（1枚絵）の受け入れ

スプライトシート全7行が用意できない場合、**単一のキャラクター立ち絵 1 枚**でも組み込み可能。

### 方法

1. キャラクターの正面立ち絵（透過 PNG）を用意する
2. `scripts/make-sprite-sheet.sh`（後述）で 7 行 × 4 列のシートに変換する
3. `Assets/Characters/fighter00N.png` として配置する

### 変換スクリプト

```sh
# 1枚絵からスプライトシートを生成（サイズを 64×128 に自動リサイズ）
scripts/make-sprite-sheet.sh <元画像.png> Assets/Characters/fighter00N.png
```

---

## 4. セルサイズを変える場合

より高解像度（例：128×256）にする場合、キャラ JSON の `sprite` を更新する：

```json
"sprite": {
  "path": "Characters/fighter001.png",
  "frameWidth": 128,
  "frameHeight": 256,
  "stateRows": [
    { "state": "idle",   "row": 0 },
    { "state": "walk",   "row": 1 },
    { "state": "jump",   "row": 2 },
    { "state": "attack", "row": 3 },
    { "state": "jump_attack", "row": 3 },
    { "state": "throw",  "row": 3 },
    { "state": "hitstun","row": 4 },
    { "state": "guard",  "row": 5 },
    { "state": "crouch", "row": 6 },
    { "state": "crouch_walk",   "row": 6 },
    { "state": "crouch_attack", "row": 6 },
    { "state": "crouch_guard",  "row": 6 }
  ]
}
```

コードは変更不要。JSON の `frameWidth`/`frameHeight` をシートに合わせるだけ。

---

## 5. アセット組み込みチェックリスト

新しいデザインを受け取ったら：

- [ ] `Assets/Characters/fighter00N.png` を配置
- [ ] 必要に応じて `fighter00N.json` の `sprite.frameWidth`/`frameHeight` を更新
- [ ] `./gradlew build` でコンパイルが通ることを確認
- [ ] `scripts/capture-app-screenshot-linux.sh -x p1char=fighter00N -f 60` でスプライトを目視確認

---

## 6. 現在のロスター（10体）

| ID | 名前 | アーキタイプ |
|---|---|---|
| fighter001 | Aoi | 万能 Shoto |
| fighter002 | Akane | 高速 Shoto |
| fighter003 | Tetsu | 重量級 |
| fighter004 | Rai | 空中機動 |
| fighter005 | Sora | 遠距離 Zoner |
| fighter006 | Iwao | 純グラップラー |
| fighter007 | Kaede | バランス型 |
| fighter008 | Ren | ダッシュ Rushdown |
| fighter009 | Hayato | 多段・アーマー型 |
| fighter010 | Yuki | 高速多段 Rushdown |

fighter011〜020 の JSON/PNG はリポジトリに残っているが、ロスター非掲載（必要時に `ROSTER_IDS` に追記すれば即復活）。
