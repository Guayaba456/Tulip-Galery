package com.tulip.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Conecta la interfaz con el reproductor del servicio. Sólo se conecta al abrir "Vídeos aleatorios". */
class PlayerConnection(private val context: Context) {
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()
    private var future: ListenableFuture<MediaController>? = null

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            _player.value = try { f.get() } catch (e: Exception) { null }
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
        _player.value = null
    }
}
