# scripts/

テンプレを実プロジェクトに展開するためのヘルパスクリプト群。

| ファイル | 役割 | 対象 OS |
|---|---|---|
| `apply-template.sh` | `{{...}}` placeholder の一括置換 + 仕上げ | macOS / Linux（bash 4+ 推奨） |
| `capture-app-window.sh` | 起動中アプリのウィンドウキャプチャ | macOS のみ（Swift + screencapture を使用） |

## apply-template.sh

詳細は [../docs/setup.md](../docs/setup.md) と [../docs/customize.md](../docs/customize.md) を参照。

対話モードと非対話モード（CLI フラグ）の両方をサポート。完了後に placeholder 残存検査と仕上げプロンプト（`CLAUDE.md.template` のリネーム）が走る。

## capture-app-window.sh

```sh
scripts/capture-app-window.sh <process-name> <output-path>
```

例：

```sh
./build/app &
sleep 2
scripts/capture-app-window.sh app docs/screenshots/task-1-init.png
kill %1
```

- macOS の **画面収録権限** が Claude Code 親プロセス（VS Code / ターミナル）に付与されている必要がある
- アクセシビリティ権限は不要（System Events は使わない）
- Retina 倍率は `screencapture -l<wid>` が自動考慮

Windows / Linux 版は本テンプレには含まれていない。必要になったプロジェクトで `scripts/windows/` 等を追加すること。
