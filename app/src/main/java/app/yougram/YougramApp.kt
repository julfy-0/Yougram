package app.yougram

import android.app.Application
import app.yougram.data.AppContainer

class YougramApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Служебный процесс перезапуска не должен поднимать TDLib: база аккаунта занята основным процессом.
        if (getProcessName().endsWith(":restart")) return
        container = AppContainer(this)
        container.start()
    }
}