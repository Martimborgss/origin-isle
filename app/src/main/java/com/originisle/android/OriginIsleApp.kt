package com.originisle.android

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.originisle.android.island.OriginIslandBuilder

class OriginIsleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Origin Isle is dark-mode only — force it regardless of the system setting. themes.xml and
        // ui/Theme.kt already fix everything to dark; this additionally covers any AppCompat-driven
        // system UI (e.g. permission dialogs styled by the app's theme).
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        // Whitelist this package for all SuperX scenes as early as possible.
        OriginIslandBuilder.grantScenes(this)
    }
}
