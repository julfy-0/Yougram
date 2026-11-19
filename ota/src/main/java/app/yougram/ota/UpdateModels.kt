package app.yougram.ota

import java.io.File

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    val sizeBytes: Long = 0L,
    val sha256: String? = null,
    /** Картинка 1000×400 (banner.png / banner.jpg / banner.webp), если приложена к релизу. */
    val bannerUrl: String? = null,
    val versionCode: Int = 0,
    /** Номер беты (1, 2, …); 0 — стабильный релиз. */
    val beta: Int = 0,
    /** Сборка лежит на сервере в ZIP-архиве с одним APK внутри: сначала скачиваем архив, потом распаковываем. */
    val packed: Boolean = false,
    /** Размер и sha256 самого архива (для докачки и проверки); sizeBytes/sha256 относятся к APK внутри. */
    val packedSizeBytes: Long = 0L,
    val packedSha256: String? = null,
)

enum class UpdateChannel(val key: String) { Release("release"), Beta("beta") }

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}
