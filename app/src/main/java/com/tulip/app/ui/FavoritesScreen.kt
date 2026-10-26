@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.tulip.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulip.app.MainViewModel
import com.tulip.app.logic.MediaKind

@Composable
fun FavoritesScreen(vm: MainViewModel, openViewer: (List<Long>, Int) -> Unit, onSettings: () -> Unit) {
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }   // 0 todo, 1 imágenes, 2 vídeos
    val byId = remember(media) { media.associateBy { it.id } }
    val available = remember(favs, byId, filter) {
        favs.mapNotNull { byId[it.mediaId] }.filter { filter == 0 || (filter == 1 && it.kind == MediaKind.IMAGE) || (filter == 2 && it.kind == MediaKind.VIDEO) }
    }
    val missing = if (loaded && media.isNotEmpty()) favs.filter { byId[it.mediaId] == null } else emptyList()

    Scaffold(topBar = { TopAppBar(title = { Text("Favoritos") }, actions = { IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Ajustes") } }) }) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todo", "Imágenes", "Vídeos").forEachIndexed { i, l -> FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(l) }) }
            }
            if (missing.isNotEmpty()) {
                Card(Modifier.padding(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${missing.size} favorito(s) ya no están disponibles", style = MaterialTheme.typography.titleSmall)
                        Text("Se movieron fuera de Android o se borraron. Puedes retirar su referencia.", style = MaterialTheme.typography.bodySmall)
                        missing.take(5).forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${m.lastName}", Modifier.weight(1f), maxLines = 1)
                                TextButton(onClick = { vm.removeFavoriteRef(m.mediaId) }) { Text("Quitar") }
                            }
                        }
                        if (missing.size > 5) TextButton(onClick = { missing.forEach { vm.removeFavoriteRef(it.mediaId) } }) { Text("Quitar todos") }
                    }
                }
            }
            if (available.isEmpty()) EmptyState(Icons.Rounded.Favorite, "Aún no hay favoritos", "Marca fotos y vídeos con el corazón sin moverlos de su carpeta.")
            else MediaGrid(available, settings, emptySet(), favs.map { it.mediaId }.toSet(), onClick = { i, _ -> openViewer(available.map { it.id }, i) }, onLongClick = { vm.toggleFavorite(it); vm.message("Quitado de favoritos") })
        }
    }
}
