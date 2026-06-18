# macOS でのローカル実行・スクショ撮影

macOS でのゲーム起動とスクリーンショット撮影の手順。

## 前提：JAVA_HOME

macOS にデフォルト JDK が無いと `./gradlew` が `Unable to locate a Java Runtime` で落ちる。毎回先頭で指定する：

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
```

（無ければ `brew install openjdk@17`。arm64 mac で確認済み。）

## ゲームを普通に起動

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew run
```

`-XstartOnFirstThread` は run タスクが macOS で自動付与するため追加設定不要。

## スクショをローカル macOS で撮る（Xvfb 不要・アプリ自身が PNG 書き出し）

**重要：macOS では `scripts/capture-app-screenshot-linux.sh`（Xvfb）が動かない**——XQuartz の Xvfb は `/tmp/.X11-unix` の所有者が root であることを要求し、sudo 無しでは起動できない（`Owner of /tmp/.X11-unix must be set to root`）。Xvfb 方式は Linux / web 専用。

代わりに、**ネイティブ実行に `-Dphantom.screenshot.*` を渡す**だけでよい。`ScreenshotController` が指定フレームで GL フレームバッファを PNG に書き出し `Gdx.app.exit()` で自動終了する（一瞬ウィンドウが開いて閉じる）。出力は retina で 2560×1440。

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew run \
  -Dphantom.screenshot.path=/tmp/shot.png \
  -Dphantom.screenshot.frame=90
```

- `-Dphantom.screenshot.path` を指定した時だけ撮影モード（未指定なら普通に遊べる）。
- `-Dphantom.screenshot.frame=N` で撮るタイミング（60fps＝N/60 秒）。過渡状態（ジャンプ頂点・攻撃 active 等）は N をずらして合わせる。
- 撮影後は Read ツールで PNG を目視確認する。

主な `-Dphantom.screenshot.<key>`（`Infra/Build/build.gradle` の run タスク転送リストに登録済みのもの）：`p1x`/`p2x`（初期中心 X）、`p1hp`/`p2hp`（初期 HP）、`timelimit`（秒）、`ai=false`（P2 静止）、`debug=true`（当たり判定表示）、`p1char`/`p2char`、`stage`、`hold`（起動時から押下）、`script`（タイムド入力 `start-end:tok+tok;...`）など。新しいキーを足したら同転送リストにも追記する。

### 例：パーフェクト KO（README ショーケース 127-perfect.png の撮影）

既定間隔のまま波動拳で KO させ、名前ラベルが中央バナーに被らないクリーンな構図：

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew run \
  -Dphantom.screenshot.path=/tmp/perfect.png -Dphantom.screenshot.frame=170 \
  -Dphantom.screenshot.timelimit=2 -Dphantom.screenshot.ai=false -Dphantom.screenshot.p2hp=60 \
  -Dphantom.screenshot.script="1-10:p1.down;8-16:p1.down+p1.right;14-26:p1.right;18-18:p1.attack_light"
```

→ Aoi 満タン(1000/1000) vs Akane KO(0/850) で PERFECT! + K.O. + Aoi WINS。

> 解像度メモ：README の既存ショーケースは 1280×720。macOS ネイティブ撮影は retina で 2560×1440 になる（GitHub のテーブル表示ではセル幅にスケールされ見た目は揃う。厳密に統一したいなら撮影後に縮小する）。
