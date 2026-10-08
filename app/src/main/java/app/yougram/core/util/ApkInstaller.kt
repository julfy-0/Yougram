package app.yougram.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {
    fun isApk(fileName: String?, mime: String?) =
        fileName?.endsWith(".apk", true) == true || mime == "application/vnd.android.package-archive"

    /** Копирует файл TDLib в cache и запускает системный установщик. */
    fun install(context: Context, tdlibFilePath: String) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            android.widget.Toast.makeText(context, "Разрешите установку из этого приложения и нажмите на файл ещё раз", android.widget.Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val apk = File(context.cacheDir, "install.apk")
        File(tdlibFilePath).copyTo(apk, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            android.widget.Toast.makeText(context, "Не удалось запустить установку", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
