package app.yougram.core.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Иконка приложения на рабочем столе. Каждая иконка — это отдельный activity-alias в манифесте;
 * включён всегда ровно один, остальные выключены. Выбор хранится в самом PackageManager.
 */
enum class AppIcon(val alias: String, val title: String, val subtitle: String) {
    Classic("app.yougram.LauncherClassic", "Классическая", "Серая с белой стрелкой, поддерживает тематическую иконку"),
    Neon("app.yougram.LauncherNeon", "Неон", "Светящийся градиентный контур"),
    Blue("app.yougram.LauncherBlue", "Голубая", "Голубой градиент и прозрачный значок"),
    Green("app.yougram.LauncherGreen", "Сине-зелёная", "Градиент от голубого к зелёному"),
}

object AppIconManager {
    /** Какая иконка включена сейчас. Если ни одна из дополнительных не включена явно, считается классическая. */
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull {
            it != AppIcon.Classic &&
                pm.getComponentEnabledSetting(component(context, it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: AppIcon.Classic
    }

    /** Включает выбранную иконку и выключает остальные. Приложение при этом не перезапускается. */
    fun set(context: Context, icon: AppIcon) {
        if (current(context) == icon) return
        val pm = context.packageManager
        // Сначала включаем новую, потом выключаем старую, чтобы на экране не оказалось ни одной иконки.
        pm.setComponentEnabledSetting(
            component(context, icon),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(
                component(context, it),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private fun component(context: Context, icon: AppIcon) = ComponentName(context.packageName, icon.alias)
}
