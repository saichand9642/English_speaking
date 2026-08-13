package com.speak.app

import android.app.Application
import com.speak.app.di.AppContainer

class SpeakApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
