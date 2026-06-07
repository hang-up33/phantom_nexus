<#
.SYNOPSIS
  Windows で起動中アプリのウィンドウだけを PNG にキャプチャする。
  （macOS 用 capture-app-window.sh の Windows 版。両方を残置する。）

.DESCRIPTION
  Win32 API で対象ウィンドウのハンドルと矩形を取得し、System.Drawing で
  そのウィンドウ領域のみをビットマップに転写して PNG 保存する。
  ウィンドウは -WindowTitle（部分一致）または -ProcessName で特定する。

.PARAMETER WindowTitle
  キャプチャ対象ウィンドウのタイトル（部分一致, 大文字小文字無視）。

.PARAMETER ProcessName
  キャプチャ対象のプロセス名（拡張子なし。例: java）。WindowTitle 未指定時に使用。

.PARAMETER OutPath
  出力先 PNG パス（例: docs/screenshots/4-window-display.png）。

.EXAMPLE
  Start-Process -FilePath ".\gradlew.bat" -ArgumentList "run"
  Start-Sleep -Seconds 8
  powershell -ExecutionPolicy Bypass -File scripts/capture-app-window.ps1 -WindowTitle "Phantom Nexus" -OutPath "docs/screenshots/4-window-display.png"

.NOTES
  終了コード 0=成功 / 1=対象ウィンドウ無し / 2=引数不正。
  対象ウィンドウは最前面に出してからキャプチャするのが確実（最小化中は不可）。
#>
param(
    [string]$WindowTitle = "",
    [string]$ProcessName = "",
    [Parameter(Mandatory = $true)][string]$OutPath
)

$ErrorActionPreference = "Stop"

if (-not $WindowTitle -and -not $ProcessName) {
    Write-Error "WindowTitle か ProcessName のいずれかを指定してください。"
    exit 2
}

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms

$sig = @"
using System;
using System.Runtime.InteropServices;
public class Win32Capture {
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
}
"@
if (-not ("Win32Capture" -as [type])) { Add-Type -TypeDefinition $sig }

# --- 対象ウィンドウのハンドルを特定 ---
$target = $null
$procs = Get-Process | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle }
foreach ($p in $procs) {
    if ($WindowTitle -and ($p.MainWindowTitle -like "*$WindowTitle*")) { $target = $p; break }
    if (-not $WindowTitle -and $ProcessName -and ($p.ProcessName -ieq $ProcessName)) { $target = $p; break }
}

if (-not $target) {
    Write-Error "対象ウィンドウが見つかりません (WindowTitle='$WindowTitle' ProcessName='$ProcessName')。"
    exit 1
}

$hWnd = $target.MainWindowHandle

# 最前面化（最小化なら復元）してから少し待つ
[Win32Capture]::ShowWindow($hWnd, 9) | Out-Null   # SW_RESTORE
[Win32Capture]::SetForegroundWindow($hWnd) | Out-Null
Start-Sleep -Milliseconds 600

$rect = New-Object Win32Capture+RECT
if (-not [Win32Capture]::GetWindowRect($hWnd, [ref]$rect)) {
    Write-Error "GetWindowRect に失敗しました。"
    exit 1
}

$width  = $rect.Right - $rect.Left
$height = $rect.Bottom - $rect.Top
if ($width -le 0 -or $height -le 0) {
    Write-Error "ウィンドウサイズが不正です ($width x $height)。最小化されていないか確認してください。"
    exit 1
}

# --- 出力先ディレクトリを用意 ---
$dir = Split-Path -Parent $OutPath
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }

# --- キャプチャ ---
$bmp = New-Object System.Drawing.Bitmap($width, $height)
$gfx = [System.Drawing.Graphics]::FromImage($bmp)
$gfx.CopyFromScreen($rect.Left, $rect.Top, 0, 0, (New-Object System.Drawing.Size($width, $height)))
$bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
$gfx.Dispose()
$bmp.Dispose()

Write-Output $OutPath
exit 0
