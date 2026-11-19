package app.yougram.ota

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Запуск системного установщика для скачанного APK. */
internal class ApkInstaller(context: Context, private val config: OtaConfig) {
    private val appContext = context.applicationContext

    /**
     * true — установщик запущен. false — нет разрешения «Установка неизвестных приложений»:
     * открыта его настройка, после выдачи разрешения нужно вызвать ещё раз.
     */
    fun install(file: File): Boolean {
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val authority = config.fileProviderAuthority ?: "${appContext.packageName}.files"
        val uri = FileProvider.getUriForFile(appContext, authority, file)
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }
}
