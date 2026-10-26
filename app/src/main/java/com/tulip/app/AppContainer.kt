package com.tulip.app

import android.content.Context
import androidx.room.Room
import com.tulip.app.data.FileOps
import com.tulip.app.data.MediaRepository
import com.tulip.app.data.SettingsRepository
import com.tulip.app.data.TulipDb
import com.tulip.app.playback.PlayerConnection

class AppContainer(context: Context) {
    val media = MediaRepository(context)
    val fileOps = FileOps(context, media)
    val settingsRepo = SettingsRepository(context)
    val db: TulipDb = Room.databaseBuilder(context, TulipDb::class.java, "tulip.db").build()
    val player = PlayerConnection(context)
}
