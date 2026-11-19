# :ota — клиент OTA-обновлений Yougram

Gradle-модуль (`app.yougram.ota`) без UI и без зависимостей от остального приложения: сеть, проверка APK, установка и состояние обновления.
Сервер (Cloudflare Worker + R2) и публикация релизов живут в репозитории Yougram-ota.

| Файл | Роль |
|---|---|
| `OtaConfig.kt` | настройки, которые передаёт приложение: адрес сервера, версия, канал по умолчанию, перевод строк |
| `UpdateModels.kt` | `UpdateInfo`, `UpdateChannel`, `UpdateState` |
| `OtaClient.kt` | `/v1/check`, скачивание APK с докачкой (Range), загрузка баннера |
| `ApkVerifier.kt` | размер, sha256, имя пакета, версия, подпись |
| `ApkInstaller.kt` | запуск системного установщика через FileProvider |
| `AppUpdater.kt` | публичный API: `state`, `channel`, `check()`, `download()`, `install()`, `postpone()`, `loadBanner()` |

## Подключение

```kotlin
// settings.gradle.kts
include(":ota")

// app/build.gradle.kts
implementation(project(":ota"))
```

```kotlin
val updater = AppUpdater(
    context = applicationContext,
    scope = processScope,
    config = OtaConfig(
        serverUrl = BuildConfig.OTA_URL,
        versionCode = BuildConfig.VERSION_CODE,
        versionName = BuildConfig.VERSION_NAME,
        isReleaseBuild = BuildConfig.RELEASE_TYPE == "release",
        translate = { tr(it) },
    ),
)
```

В приложении конфиг собирает `feature/update/OtaSetup.kt` (`yougramOtaConfig()`).

## Что остаётся на стороне приложения

- `REQUEST_INSTALL_PACKAGES` и `FileProvider` (`${applicationId}.files`, путь `cache-path`) в `AndroidManifest.xml`;
- `OTA_URL` в `local.properties` → `BuildConfig.OTA_URL`;
- UI: `UpdateScreen`, `UpdateAvailableDialog`, баннер.
