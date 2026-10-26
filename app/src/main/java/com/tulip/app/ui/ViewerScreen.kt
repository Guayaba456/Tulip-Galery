@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.tulip.app.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tulip.app.MainViewModel
import com.tulip.app.logic.MediaEntry
import com.tulip.app.logic.MediaKind

@Composable
fun ViewerScreen(vm: MainViewModel, onBack: () -> Unit, onEdit: (MediaEntry) -> Unit) {
    val req by vm.viewerRequest.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val favIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val byId = remember(media) { media.associateBy { it.id } }
    val ids = req?.ids ?: emptyList()
    val list = remember(ids, byId) { ids.mapNotNull { byId[it] } }
    if (list.isEmpty()) {
        Scaffold(topBar = { TopAppBar(title = { Text("Visor") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } }) }) { p ->
            EmptyState(Icons.Rounded.Image, "No hay nada que mostrar", "Los archivos pudieron moverse o borrarse.", Modifier.padding(p))
        }
        return
    }
    val pager = rememberPagerState(initialPage = (req?.start ?: 0).coerceIn(0, list.lastIndex), pageCount = { list.size })
    val current = list[pager.currentPage.coerceIn(0, list.lastIndex)]
    var info by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf<MediaEntry?>(null) }
    var playingPage by remember { mutableIntStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = { vm.toggleFavorite(current) }) {
                        val f = current.id in favIds
                        Icon(if (f) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorito", tint = if (f) Color(0xFFE58BA8) else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { info = true }) { Icon(Icons.Rounded.Info, "Información y acciones") }
                },
            )
        },
    ) { pad ->
        HorizontalPager(pager, Modifier.padding(pad).fillMaxSize(), beyondViewportPageCount = 0) { page ->
            val e = list[page]
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                if (e.kind == MediaKind.VIDEO && playingPage == page && pager.currentPage == page) {
                    InlineVideo(e)
                } else {
                    Thumb(e, 2048, Modifier.fillMaxSize().clickable { if (e.kind == MediaKind.IMAGE) zoom = e else playingPage = page }, ContentScale.Fit)
                    if (e.kind == MediaKind.VIDEO) {
                        FilledIconButton(onClick = { playingPage = page }) { Icon(Icons.Rounded.PlayArrow, "Reproducir") }
                    }
                }
            }
        }
    }
    if (info) EntryActionsSheet(vm, current, { info = false }, onEdit = onEdit)
    zoom?.let { ZoomDialog(it) { zoom = null } }
}

@Composable
private fun InlineVideo(e: MediaEntry) {
    val ctx = LocalContext.current
    val player = remember(e.uri) { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(player) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(e.uri)))
        player.prepare()
        player.playWhenReady = true
        onDispose { player.release() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }   // este visor no reproduce en segundo plano
    AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
}

@Composable
fun ZoomDialog(e: MediaEntry, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ZoomableImage(e, Modifier.fillMaxSize())
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { Icon(Icons.Rounded.Close, "Cerrar", tint = Color.White) }
        }
    }
}

@Composable
private fun ZoomableImage(e: MediaEntry, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) },
    ) {
        Thumb(
            e, 2048,
            Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            ContentScale.Fit,
        )
    }
}
