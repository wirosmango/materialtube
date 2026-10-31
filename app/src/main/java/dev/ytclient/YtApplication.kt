package dev.ytclient

import android.app.Application

class YtApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
