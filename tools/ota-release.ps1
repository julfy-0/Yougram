\xEF\xBB\xBF<#
  Публикация сборки Yougram в OTA (Cloudflare) одной командой.

  .\tools\ota-release.ps1                          # последний собранный APK, заметки из notes.txt
  .\tools\ota-release.ps1 -Rollout 10              # раздать ~10% устройств
  .\tools\ota-release.ps1 -Rollout 100 -ManifestOnly -Apk путь\app.apk   # докрутить до всех, APK не заливать заново
  .\tools\ota-release.ps1 -Apk C:\path\app.apk -Notes "- Исправления" -Banner banner.png
  .\tools\ota-release.ps1 -DryRun                  # показать, что будет залито, ничего не записывая
#>
param(
  [string]$Apk,
  [string]$Notes = "notes.txt",
  [string]$Banner,
  [ValidateRange(1, 100)][int]$Rollout = 100,
  [switch]$ManifestOnly,
  [switch]$DryRun,
  [switch]$Yes
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw "Не найден Node.js (https://nodejs.org)" }

# 1. Какой APK публикуем
if (-not $Apk) {
  $out = Join-Path $root "app\build\outputs\apk"
  $found = Get-ChildItem $out -Recurse -Filter *.apk -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch "androidTest" } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if (-not $found) { throw "APK не найден в $out. Соберите проект или укажите -Apk" }
  $Apk = $found.FullName
}
if (-not (Test-Path $Apk)) { throw "Файл не найден: $Apk" }
$apkItem = Get-Item $Apk

# 2. Заметки: файл notes.txt по умолчанию, либо текст прямо в параметре
if ($Notes -eq "notes.txt" -and -not (Test-Path $Notes)) {
  Write-Warning "notes.txt не найден: релиз уйдёт без списка изменений"
  $Notes = $null
}
if ($Banner -and -not (Test-Path $Banner)) { throw "Баннер не найден: $Banner" }

# 3. Подтверждение
Write-Host ""
Write-Host "APK:      $($apkItem.FullName)"
Write-Host ("Размер:   {0:N1} МБ, собран {1}" -f ($apkItem.Length / 1MB), $apkItem.LastWriteTime)
Write-Host "Раздача:  $Rollout%"
if ($Notes) { Write-Host "Заметки:  $Notes" }
if ($Banner) { Write-Host "Баннер:   $Banner" }
if ($apkItem.LastWriteTime -lt (Get-Date).AddDays(-1)) { Write-Warning "этому APK больше суток. Это точно свежая сборка?" }
if (-not $Yes -and -not $DryRun) {
  $answer = Read-Host "Опубликовать? (y/N)"
  if ($answer -notmatch '^(y|д)') { Write-Host "Отменено."; exit 0 }
}

# 4. Публикация
$publishArgs = @("tools\ota-publish.mjs", $apkItem.FullName, "--rollout", $Rollout)
if ($Notes) { $publishArgs += @("--notes", $Notes) }
if ($Banner) { $publishArgs += @("--banner", $Banner) }
if ($ManifestOnly) { $publishArgs += "--manifest-only" }
if ($DryRun) { $publishArgs += "--dry-run" }
& node @publishArgs
if ($LASTEXITCODE -ne 0) { throw "Публикация не удалась (код $LASTEXITCODE)" }
if ($DryRun) { exit 0 }

# 5. Контрольная проверка: что отвечает сервер старому клиенту (versionCode 1)
$prop = if (Test-Path "local.properties") { Select-String -Path "local.properties" -Pattern '^OTA_URL=(.+)$' } else { $null }
if ($prop) {
  $base = $prop.Matches[0].Groups[1].Value.Trim().TrimEnd('/')
  try {
    $r = Invoke-RestMethod "$base/v1/check?channel=beta&code=1&did=$([guid]::NewGuid())"
    if ($r.update) { Write-Host "Сервер предлагает: $($r.versionName) (код $($r.versionCode))" -ForegroundColor Green }
    else { Write-Host "Сервер пока ничего не предлагает (при раздаче меньше 100% так и должно быть для части устройств)." -ForegroundColor Yellow }
  } catch {
    Write-Warning "Не удалось проверить $base : $($_.Exception.Message)"
  }
}
