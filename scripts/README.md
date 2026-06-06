# scripts/

テンプレを実プロジェクトに展開するためのヘルパスクリプト群。

| ファイル | 役割 | 対象 OS |
|---|---|---|
| `apply-template.sh` | `{{...}}` placeholder の一括置換 + 仕上げ | macOS / Linux（bash 4+ 推奨） |
| `capture-app-window.ps1` | 起動中アプリのウィンドウキャプチャ | **Windows（本プロジェクト既定）**。.NET（System.Drawing）を使用 |
| `capture-app-window.sh` | 起動中アプリのウィンドウキャプチャ | macOS のみ（Swift + screencapture を使用） |

## apply-template.sh

詳細は [../docs/setup.md](../docs/setup.md) と [../docs/customize.md](../docs/customize.md) を参照。

対話モードと非対話モード（CLI フラグ）の両方をサポート。完了後に placeholder 残存検査と仕上げプロンプト（`CLAUDE.md.template` のリネーム）が走る。

## capture-app-window.ps1（Windows・本プロジェクト既定）

```powershell
powershell -ExecutionPolicy Bypass -File scripts/capture-app-window.ps1 -WindowTitle "Phantom Nexus" -OutPath "docs/screenshots/<N>-<短い名>.png"
```

例：

```powershell
Start-Process -FilePath ".\gradlew.bat" -ArgumentList "run"
Start-Sleep -Seconds 8
powershell -ExecutionPolicy Bypass -File scripts/capture-app-window.ps1 -WindowTitle "Phantom Nexus" -OutPath "docs/screenshots/4-window-display.png"
```

- `-WindowTitle`（部分一致）または `-ProcessName` で対象ウィンドウを特定する
- Win32 API でウィンドウ矩形を取得し、`System.Drawing` で当該領域のみ PNG 保存する
- 対象ウィンドウは最前面化してからキャプチャする（最小化中は不可）
- 追加権限は不要

## capture-app-window.sh（macOS 用 / 将来対応のため残置）

```sh
scripts/capture-app-window.sh <process-name> <output-path>
```

例：

```sh
./gradlew run &
sleep 8
scripts/capture-app-window.sh java docs/screenshots/4-window-display.png
kill %1
```

- macOS の **画面収録権限** が Claude Code 親プロセス（VS Code / ターミナル）に付与されている必要がある
- アクセシビリティ権限は不要（System Events は使わない）
- Retina 倍率は `screencapture -l<wid>` が自動考慮
- **Windows では動作しないため `.ps1` を使う**

Linux 版は本テンプレには含まれていない。必要になったプロジェクトで追加すること。
