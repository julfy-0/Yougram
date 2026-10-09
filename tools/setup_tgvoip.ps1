# Копирует нативные исходники tgcalls и Java-слой WebRTC из Telegram-Android в проект Yougram.
# Запуск из корня Yougram (PowerShell):
#   powershell -ExecutionPolicy Bypass -File tools\setup_tgvoip.ps1
# Нужен установленный git. Клон Telegram кладётся в короткий путь, чтобы не упереться в лимит 260 символов.
param([string]$Tg = "C:\tg")

$ErrorActionPreference = "Stop"
$Y = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$D = Join-Path $Y "app\src\main\cpp\tgvoip"

function Run($exe, $argList) {
    & $exe @argList
    if ($LASTEXITCODE -ne 0) { throw "$exe $($argList -join ' ') завершился с кодом $LASTEXITCODE" }
}

git config --global core.longpaths true

if (-not (Test-Path (Join-Path $Tg ".git"))) {
    Run git @("clone", "--depth", "1", "https://github.com/DrKLO/Telegram", $Tg)
}
Push-Location $Tg
Run git @("submodule", "update", "--init", "--depth", "1",
    "TMessagesProj/jni/third_party/absl", "TMessagesProj/jni/third_party/libyuv",
    "TMessagesProj/jni/third_party/boringssl", "TMessagesProj/jni/third_party/dav1d",
    "TMessagesProj/jni/third_party/libvpx", "TMessagesProj/jni/third_party/xiph/opus",
    "TMessagesProj/jni/td")
Pop-Location

$J = Join-Path $Tg "TMessagesProj\jni"

# robocopy возвращает коды 0-7 при успехе.
function Copy-Tree($src, $dst, $excludeDirs = @()) {
    $a = @($src, $dst, "/E", "/NFL", "/NDL", "/NJH", "/NJS", "/NP")
    if ($excludeDirs.Count -gt 0) { $a += "/XD"; $a += $excludeDirs }
    & robocopy @a | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy $src -> $dst завершился с кодом $LASTEXITCODE" }
}

New-Item -ItemType Directory -Force -Path (Join-Path $D "prebuild\lib\arm64-v8a") | Out-Null

Copy-Tree (Join-Path $J "voip")    (Join-Path $D "voip")
Copy-Tree (Join-Path $J "openssl") (Join-Path $D "openssl")
Copy-Tree (Join-Path $J "third_party") (Join-Path $D "third_party") @(".git", "breakpad", "ffmpeg", "openh264", "wamr")
Copy-Tree (Join-Path $J "td")      (Join-Path $D "td") @(".git")
Copy-Tree (Join-Path $J "prebuild\include") (Join-Path $D "prebuild\include")

foreach ($l in "crypto","ssl","vpx","dav1d","opus","openh264","iwasm","tde2e","tdutils") {
    Copy-Item (Join-Path $J "prebuild\lib\arm64-v8a\lib$l.a") (Join-Path $D "prebuild\lib\arm64-v8a\") -Force
}

# Замена переменной в CMake voip (без BOM, переводы строк сохраняются).
$cm = Join-Path $D "voip\CMakeLists.txt"
$text = [System.IO.File]::ReadAllText($cm)
$text = $text.Replace('${CMAKE_HOME_DIRECTORY}', '${CMAKE_CURRENT_SOURCE_DIR}')
[System.IO.File]::WriteAllText($cm, $text, (New-Object System.Text.UTF8Encoding($false)))

# Java-слой WebRTC (нужен звуку во время выполнения).
$web = Join-Path $Y "app\src\main\java\org\webrtc"
if (Test-Path $web) { Remove-Item -Recurse -Force $web }
Copy-Tree (Join-Path $Tg "TMessagesProj\src\main\java\org\webrtc") $web
Remove-Item -Force (Join-Path $web "TextureViewRenderer.java") -ErrorAction SilentlyContinue

# Старый движок на NTgCalls больше не нужен.
Remove-Item -Force (Join-Path $Y "app\src\main\java\app\yougram\feature\calls\data\NTgCallsEngine.kt") -ErrorAction SilentlyContinue

Write-Host "Готово. Клон Telegram можно удалить: $Tg"