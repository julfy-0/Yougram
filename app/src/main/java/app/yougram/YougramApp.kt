package app.yougram

import android.app.Application
import app.yougram.data.AppContainer

class YougramApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
