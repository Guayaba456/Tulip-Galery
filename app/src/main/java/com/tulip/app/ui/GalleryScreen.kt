@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.tulip.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulip.app.MainViewModel
import com.tulip.app.logic.*

private enum class VisualFilter(val label: String) { ALL("Todo"), PHOTOS("Fotos"), VIDEOS("Vídeos") }

@Composable
fun GalleryScreen(vm: MainViewModel, openViewer: (List<Long>, Int) -> Unit, onCamera: () -> Unit, onSettings: () -> Unit) {
    val media by vm.media.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val favIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val path by vm.galleryPath.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(VisualFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }

    val visual = remember(media, filter) {
        media.filter { it.kind == MediaKind.IMAGE || it.kind == MediaKind.VIDEO }
            .filter { when (filter) { VisualFilter.ALL -> true; VisualFilter.PHOTOS -> it.kind == MediaKind.IMAGE; VisualFilter.VIDEOS -> it.kind == MediaKind.VIDEO } }
    }
    val searchResults = remember(visual, query) { if (query.isBlank()) null else Sorting.sort(MediaFilter(query = query).apply(visual), settings.sort) }
    val browse = remember(visual, path, settings.sort) {
        val b = FolderTree.browse(visual, path)
        Browse(b.folders, Sorting.sort(b.items, settings.sort))
    }
    val listShown = searchResults ?: browse.items
    val selEntries = remember(selected, media) { vm.entriesOf(selected) }
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = selecting) { vm.clearSelection() }
    BackHandler(enabled = !selecting && path.isNotEmpty()) { vm.galleryPath.value = FolderExclusions.normalize(path).trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } }

    Scaffold(
        topBar = {
            if (selecting) SelectionTopBar(vm, selEntries, listShown)
            else TopAppBar(
                title = {
                    if (searching) OutlinedTextField(query, { query = it }, placeholder = { Text("Buscar por nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    else Column {
                        Text("Galería")
                        Text(if (path.isEmpty()) "Todas las carpetas" else path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (path.isNotEmpty() && !searching) IconButton(onClick = {
                        vm.galleryPath.value = FolderExclusions.normalize(path).trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
                    }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Subir de carpeta") }
                },
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Buscar") }
                    IconButton(onClick = { vm.updateSetting { it[com.tulip.app.data.SettingsKeys.listView] = !settings.listView } }) {
                        Icon(if (settings.listView) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList, "Cambiar vista")
                    }
                    IconButton(onClick = onCamera) { Icon(Icons.Rounded.PhotoCamera, "Cámara") }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Ajustes") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VisualFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
            }
            when {
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                listShown.isEmpty() && browse.folders.isEmpty() -> EmptyState(Icons.Rounded.PhotoLibrary, "No hay fotos ni vídeos aquí", "Haz una foto con la cámara de Tulip o cambia el filtro.")
                else -> {
                val folderHeader: @Composable () -> Unit = { FolderRows(browse.folders) { vm.galleryPath.value = it } }
                MediaGrid(
                    entries = listShown, settings = settings, selected = selected, favorites = favIds,
                    onClick = { i, e -> if (selecting) vm.toggleSelect(e.id) else openViewer(listShown.map { it.id }, i) },
                    onLongClick = { vm.toggleSelect(it.id) },
                    header = if (searchResults == null && browse.folders.isNotEmpty()) folderHeader else null,
                )
                }
            }
        }
    }
}

@Composable
private fun FolderRows(folders: List<FolderInfo>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        folders.forEach { f ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpen(f.path) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.bodyLarge)
                    Text(f.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${f.count}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
