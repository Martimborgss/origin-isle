package com.originisle.android

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.originisle.android.island.OriginIslandBuilder

class OriginIsleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        OriginIslandBuilder.grantScenes(this)
    }
}
