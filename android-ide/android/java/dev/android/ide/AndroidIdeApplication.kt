package dev.android.ide

import android.app.Application

class AndroidIdeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter(this).install()
    }
}
