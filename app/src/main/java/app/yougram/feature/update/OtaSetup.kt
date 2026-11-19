package app.yougram.feature.update

import app.yougram.BuildConfig
import app.yougram.core.i18n.tr
import app.yougram.ota.OtaConfig

/** Настройки модуля :ota из BuildConfig приложения и системы переводов. */
fun yougramOtaConfig(): OtaConfig = OtaConfig(
    serverUrl = BuildConfig.OTA_URL,
    versionCode = BuildConfig.VERSION_CODE,
    versionName = BuildConfig.VERSION_NAME,
    isReleaseBuild = BuildConfig.RELEASE_TYPE == "release",
    fileProviderAuthority = "${BuildConfig.APPLICATION_ID}.files",
    translate = { tr(it) },
)
