package com.example

import android.app.Application
import com.example.data.local.SatyaDatabase
import com.example.data.prefs.EnginePreferences
import com.example.data.repository.DownloadRepository
import com.example.download.LanBridgeServer
import com.example.download.LinkInspector
import com.example.download.MultiSegmentDownloadEngine

class SatyaApplication : Application() {

    lateinit var database: SatyaDatabase
        private set
    lateinit var preferences: EnginePreferences
        private set
    lateinit var inspector: LinkInspector
        private set
    lateinit var engine: MultiSegmentDownloadEngine
        private set
    lateinit var repository: DownloadRepository
        private set
    lateinit var lanBridge: LanBridgeServer
        private set

    override fun onCreate() {
        super.onCreate()
        database = SatyaDatabase.getInstance(this)
        preferences = EnginePreferences(this)
        inspector = LinkInspector()
        engine = MultiSegmentDownloadEngine(
            context = this,
            dao = database.downloadDao(),
            preferences = preferences,
            inspector = inspector
        )
        repository = DownloadRepository(
            context = this,
            dao = database.downloadDao(),
            preferences = preferences,
            inspector = inspector,
            engine = engine
        )
        lanBridge = LanBridgeServer(
            context = this,
            dao = database.downloadDao(),
            onEnqueueFromPc = { url, category ->
                repository.enqueueQuickUrl(url, category)
            }
        )
    }
}
