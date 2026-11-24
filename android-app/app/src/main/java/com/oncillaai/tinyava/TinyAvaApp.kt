package com.oncillaai.tinyava

import android.app.Application
import com.oncillaai.tinyava.data.SettingsStore
import com.oncillaai.tinyava.engine.TinyAvaEngine

class TinyAvaApp : Application() {
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var engine: TinyAvaEngine
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        engine = TinyAvaEngine.getInstance(this)
    }
}
