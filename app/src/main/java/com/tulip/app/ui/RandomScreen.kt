@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.tulip.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tulip.app.MainViewModel
import com.tulip.app.logic.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.random.Random

@Composable
fun RandomScreen(
    vm: MainViewModel, openViewer: (List<Long>, Int) -> Unit, onEdit: (MediaEntry) -> Unit,
    onExit: () -> Unit, onSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Vídeos aleatorios") }, icon = { Icon(Icons.Rounded.Videocam, null) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Fotos aleatorias") }, icon = { Icon(Icons.Rounded.Image, null) })
        }
        if (tab == 0) RandomVideos(vm, onSettings) else RandomPhotos(vm, onEdit, onExit)
    }
}

// =========================================================================================
//  Vídeos aleatorios: cola reproducida por el servicio Media3 (sigue con la pantalla apagada)
// =========================================================================================

@Stable
class PlayerUi {
    var playing by mutableStateOf(false)
    var title by mutableStateOf("")
    var index by mutableIntStateOf(0)
    var count by mutableIntStateOf(0)
    var duration by mutableLongStateOf(0L)
    var position by mutableLongStateOf(0L)
}

@Composable
fun rememberPlayerUi(player: Player?): PlayerUi {
    val st = remember { PlayerUi() }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        fun sync() {
            st.playing = player.isPlaying
            st.title = player.mediaMetadata.title?.toString().orEmpty()
            st.index = player.currentMediaItemIndex
            st.count = player.mediaItemCount
            st.duration = player.duration.coerceAtLeast(0L)
            st.position = player.currentPosition.coerceAtLeast(0L)
        }
        val l = object : Player.Listener { override fun onEvents(p: Player, events: Player.Events) = sync() }
        player.addListener(l)
        sync()
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(player, st.playing) {
        while (player != null && st.playing) { st.position = player.currentPosition.coerceAtLeast(0L); delay(500) }
    }
    return st
}

private fun toMediaItem(e: MediaEntry): MediaItem = MediaItem.Builder()
    .setMediaId(e.uri).setUri(e.uri)
    .setMediaMetadata(MediaMetadata.Builder().setTitle(e.name).build())
    .build()

private fun startQueue(player: Player, entries: List<MediaEntry>, startIndex: Int, startPos: Long, loop: Boolean) {
    player.setMediaItems(entries.map(::toMediaItem), startIndex.coerceIn(0, entries.lastIndex), startPos)
    player.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    player.shuffleModeEnabled = false      // el orden aleatorio lo calcula Tulip (sin repetir hasta agotar la cola)
    player.prepare()
    player.play()
}

@Composable
private fun RandomVideos(vm: MainViewModel, onSettings: () -> Unit) {
    val playerState by vm.player.collectAsStateWithLifecycle()
    val player: Player? = playerState
    val settings by vm.settings.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val excluded by vm.excludedKeys.collectAsStateWithLifecycle()
    val ui = rememberPlayerUi(player)
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) { vm.connectPlayer() }
    BackHandler(fullscreen) { fullscreen = false }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (!settings.bgPlayback) player?.pause() }
    // Recordar la cola y la posición para poder continuar luego.
    LaunchedEffect(ui.index, ui.count) { saveQueue(vm, player) }
    DisposableEffect(player) { onDispose { saveQueue(vm, player) } }

    val videosAvailable = remember(media, excluded) { media.count { it.kind == MediaKind.VIDEO && !FolderExclusions.isExcluded(it.volume, it.relativePath, excluded) } }
    val saved = remember(settings.savedQueue) { QueueState.decode(settings.savedQueue) }

    fun begin() {
        val p = player ?: run { vm.message("Conectando con el reproductor… inténtalo de nuevo"); return }
        val q = vm.buildVideoQueue()
        if (q.isEmpty()) { vm.message("No hay vídeos disponibles (revisa las carpetas excluidas)"); return }
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        startQueue(p, q, 0, 0L, settings.loopQueue)
    }

    fun resume() {
        val p = player ?: return
        val s = saved ?: return
        val byUri = media.associateBy { it.uri }
        val entries = s.uris.mapNotNull { byUri[it] }
        if (entries.isEmpty()) { vm.message("Los vídeos de la sesión anterior ya no están disponibles"); return }
        val idx = if (s.index < s.uris.size) entries.indexOfFirst { it.uri == s.uris[s.index] }.coerceAtLeast(0) else 0
        startQueue(p, entries, idx, s.positionMs, settings.loopQueue)
    }

    if (fullscreen && player != null) {
        Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                VideoSurface(player, Modifier.fillMaxSize())
                Row(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Controls(player, ui, light = true, onShuffle = null) }
                IconButton(onClick = { fullscreen = false }, Modifier.align(Alignment.TopEnd).padding(12.dp)) { Icon(Icons.Rounded.FullscreenExit, "Salir de pantalla completa", tint = Color.White) }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
            if (!fullscreen && player != null && ui.count > 0) VideoSurface(player, Modifier.fillMaxSize())
            else Icon(Icons.Rounded.Videocam, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
            if (ui.count > 0) IconButton(onClick = { fullscreen = true }, Modifier.align(Alignment.BottomEnd)) { Icon(Icons.Rounded.Fullscreen, "Pantalla completa", tint = Color.White) }
        }
        if (ui.count > 0) {
            Text(ui.title.ifEmpty { "Reproduciendo…" }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text("Vídeo ${ui.index + 1} de ${ui.count} · ${formatDuration(ui.position)} / ${formatDuration(ui.duration)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val p = player
            if (p != null) {
                Slider(
                    value = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { v -> if (ui.duration > 0) { ui.position = (v * ui.duration).toLong() } },
                    onValueChangeFinished = { p.seekTo(ui.position) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Controls(p, ui, light = false, onShuffle = { begin() }) }
            }
        } else {
            Text("Reproducción continua y aleatoria de tus vídeos, como una lista de canciones.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { begin() }, enabled = player != null && videosAvailable > 0) {
                Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(8.dp)); Text("Iniciar aleatorio ($videosAvailable vídeos disponibles)")
            }
            if (saved != null) OutlinedButton(onClick = { resume() }, enabled = player != null) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Continuar sesión anterior") }
        }
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Con la pantalla apagada se seguirá escuchando el audio, pero no se verá la imagen. Al encenderla, vuelve a esta pestaña para recuperar el vídeo.", style = MaterialTheme.typography.bodySmall)
                Text("Cada tanda reúne hasta ${Shuffler.MAX_QUEUE} vídeos sin repetir; pulsa «Mezclar» para otra tanda. ${excluded.size} carpeta(s) excluida(s).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, null); Spacer(Modifier.width(6.dp)); Text("Carpetas excluidas y preferencias") }
            }
        }
    }
}

private fun saveQueue(vm: MainViewModel, player: Player?) {
    val p = player ?: return
    if (p.mediaItemCount == 0) return
    val uris = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }
    vm.saveQueue(QueueState(uris, p.currentMediaItemIndex, p.currentPosition.coerceAtLeast(0L)))
}

@Composable
private fun VideoSurface(player: Player?, modifier: Modifier) {
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
        update = { it.player = player },
        onRelease = { it.player = null },   // suelta la superficie: el audio sigue en el servicio
        modifier = modifier,
    )
}

@Composable
private fun Controls(player: Player, ui: PlayerUi, light: Boolean, onShuffle: (() -> Unit)?) {
    val tint = if (light) Color.White else MaterialTheme.colorScheme.onSurface
    IconButton(onClick = { player.seekToPreviousMediaItem() }) { Icon(Icons.Rounded.SkipPrevious, "Anterior", tint = tint) }
    FilledIconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(56.dp)) {
        Icon(if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (ui.playing) "Pausar" else "Reproducir")
    }
    IconButton(onClick = { player.seekToNextMediaItem() }) { Icon(Icons.Rounded.SkipNext, "Siguiente", tint = tint) }
    if (onShuffle != null) TextButton(onClick = onShuffle) { Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(4.dp)); Text("Mezclar") }
}

// =========================================================================================
//  Fotos aleatorias: tarjetas deslizables
// =========================================================================================

@Composable
private fun RandomPhotos(vm: MainViewModel, onEdit: (MediaEntry) -> Unit, onExit: () -> Unit) {
    val media by vm.media.collectAsStateWithLifecycle()
    val favIds by vm.favoriteIds.collectAsStateWithLifecycle()
    var folder by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("all") }
    var seed by rememberSaveable { mutableIntStateOf(1) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf<MediaEntry?>(null) }
    var folderMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val images = remember(media) { media.filter { it.kind == MediaKind.IMAGE } }
    val folders = remember(images) { FolderTree.flat(images) }
    fun matches(e: MediaEntry): Boolean {
        val inFolder = folder.isEmpty() || FolderExclusions.normalize(e.relativePath).startsWith(folder)
        val ok = when (type) { "jpg" -> e.extension in setOf("jpg", "jpeg"); "png" -> e.extension == "png"; "webp" -> e.extension == "webp"; "gif" -> e.extension == "gif"; else -> true }
        return inFolder && ok
    }
    // El orden se fija al elegir filtro / mezclar: borrar o añadir fotos no reordena la sesión.
    val deckIds = remember(folder, type, seed, images.isEmpty()) {
        Shuffler.shuffleAvoidingRepeat(images.filter(::matches).map { it.id }, Random(System.nanoTime() + seed))
    }
    val byId = remember(images) { images.associateBy { it.id } }
    val deck = remember(deckIds, byId) { deckIds.mapNotNull { byId[it] } }
    val pager = rememberPagerState(pageCount = { deck.size })

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                FilterChip(selected = folder.isNotEmpty(), onClick = { folderMenu = true }, label = { Text(if (folder.isEmpty()) "Todas las carpetas" else folder.trimEnd('/').substringAfterLast('/')) }, leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)) })
                DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                    DropdownMenuItem(text = { Text("Todas las carpetas") }, onClick = { folder = ""; folderMenu = false })
                    folders.forEach { f -> DropdownMenuItem(text = { Text("${f.path} (${f.count})") }, onClick = { folder = f.path; folderMenu = false }) }
                }
            }
            listOf("all" to "Todas", "jpg" to "JPG", "png" to "PNG", "webp" to "WEBP", "gif" to "GIF").forEach { (k, l) ->
                FilterChip(selected = type == k, onClick = { type = k }, label = { Text(l) })
            }
        }
        if (deck.isEmpty()) {
            EmptyState(Icons.Rounded.Image, "No hay fotos con este filtro")
        } else {
            val current = deck[pager.currentPage.coerceIn(0, deck.lastIndex)]
            Text("${pager.currentPage + 1} / ${deck.size}${if (paused) " · en pausa" else ""}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), pageSpacing = 12.dp, userScrollEnabled = !paused) { page ->
                val e = deck[page]
                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Card(
                    Modifier.fillMaxSize().graphicsLayer {
                        val s = lerp(0.9f, 1f, 1f - offset); scaleX = s; scaleY = s
                        alpha = lerp(0.55f, 1f, 1f - offset)
                        rotationZ = (page - pager.currentPage - pager.currentPageOffsetFraction) * 3f
                    }.clickable { zoom = e },
                    shape = RoundedCornerShape(28.dp),
                ) { Thumb(e, 1600, Modifier.fillMaxSize(), ContentScale.Fit) }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }, enabled = pager.currentPage > 0) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Foto anterior") }
                IconButton(onClick = { vm.toggleFavorite(current) }) {
                    val f = current.id in favIds
                    Icon(if (f) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorito", tint = if (f) Color(0xFFE58BA8) else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { info = true }) { Icon(Icons.Rounded.Info, "Información y acciones") }
                IconButton(onClick = { paused = !paused }) { Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (paused) "Reanudar revisión" else "Pausar revisión") }
                IconButton(onClick = { seed++ }) { Icon(Icons.Rounded.Shuffle, "Volver a mezclar") }
                IconButton(onClick = onExit) { Icon(Icons.Rounded.Close, "Salir de la revisión") }
            }
            if (info) EntryActionsSheet(vm, current, { info = false }, onEdit = onEdit)
        }
    }
    zoom?.let { ZoomDialog(it) { zoom = null } }
}
