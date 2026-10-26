@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.tulip.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulip.app.MainViewModel
import com.tulip.app.data.SettingsKeys
import com.tulip.app.logic.*

private val categories = listOf(
    null to "Todo", MediaKind.IMAGE to "Imágenes", MediaKind.VIDEO to "Vídeos", MediaKind.AUDIO to "Audio",
    MediaKind.DOCUMENT to "Documentos", MediaKind.ARCHIVE to "Comprimidos", MediaKind.OTHER to "Otros",
)

private val sortLabels = mapOf(
    SortField.NAME to "Nombre", SortField.SIZE to "Tamaño", SortField.MODIFIED to "Fecha de modificación",
    SortField.ADDED to "Fecha de incorporación", SortField.TAKEN to "Fecha de captura",
)

@Composable
fun FilesScreen(vm: MainViewModel, openViewer: (List<Long>, Int) -> Unit, onCamera: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val media by vm.media.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val favIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf<MediaKind?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sortField by rememberSaveable { mutableStateOf(settings.sort.field) }
    var ascending by rememberSaveable { mutableStateOf(settings.sort.ascending) }
    var ext by rememberSaveable { mutableStateOf("") }
    var minMb by rememberSaveable { mutableStateOf("") }
    var folderText by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableIntStateOf(0) }
    var showFilters by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    val filter = MediaFilter(
        query = query,
        kinds = category?.let { setOf(it) } ?: emptySet(),
        extensions = ext.split(',', ' ').map { it.trim().trimStart('.').lowercase() }.filter { it.isNotEmpty() }.toSet(),
        minSize = ((minMb.toDoubleOrNull() ?: 0.0) * 1024 * 1024).toLong(),
        folderContains = folderText,
        modifiedAfterMs = if (days > 0) System.currentTimeMillis() - days * 86_400_000L else 0L,
    )
    val shown = remember(media, filter, sortField, ascending) { Sorting.sort(filter.apply(media), SortOrder(sortField, ascending)) }
    val selEntries = remember(selected, media) { vm.entriesOf(selected) }
    val selecting = selected.isNotEmpty()
    BackHandler(selecting) { vm.clearSelection() }

    Scaffold(topBar = {
        if (selecting) SelectionTopBar(vm, selEntries, shown)
        else TopAppBar(
            title = {
                if (searching) OutlinedTextField(query, { query = it }, placeholder = { Text("Buscar por nombre o ruta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                else Column { Text("Todos los archivos"); Text("${shown.size} resultado(s)", style = MaterialTheme.typography.labelSmall) }
            },
            actions = {
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Buscar") }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, "Ordenar") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        sortLabels.forEach { (f, l) ->
                            DropdownMenuItem(
                                text = { Text(l + if (f == sortField) (if (ascending) "  ↑" else "  ↓") else "") },
                                onClick = {
                                    if (f == sortField) ascending = !ascending else { sortField = f; ascending = f == SortField.NAME }
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { showFilters = true }) { Icon(Icons.Rounded.Tune, "Filtros") }
                IconButton(onClick = { vm.updateSetting { it[SettingsKeys.listView] = !settings.listView } }) { Icon(if (settings.listView) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList, "Cambiar vista") }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Ajustes") }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { (k, l) -> FilterChip(selected = category == k, onClick = { category = k }, label = { Text(l) }) }
            }
            Text(
                "Orden: ${sortLabels[sortField]} · " + when (sortField) {
                    SortField.TAKEN -> "las entradas sin fecha de captura van al final (no se inventa)."
                    SortField.ADDED -> "momento en que el archivo entró al almacenamiento."
                    SortField.MODIFIED -> "última modificación del archivo."
                    else -> if (ascending) "ascendente" else "descendente"
                },
                Modifier.padding(horizontal = 16.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (shown.isEmpty()) EmptyState(Icons.Rounded.Description, "Sin resultados", "Android sólo muestra a las apps los archivos de medios y, según la versión, algunos documentos. No se accede a datos privados de otras apps.")
            else MediaGrid(
                shown, settings, selected, favIds,
                onClick = { _, e ->
                    if (selecting) vm.toggleSelect(e.id)
                    else if (e.kind == MediaKind.IMAGE || e.kind == MediaKind.VIDEO) {
                        val visual = shown.filter { it.kind == MediaKind.IMAGE || it.kind == MediaKind.VIDEO }
                        openViewer(visual.map { it.id }, visual.indexOfFirst { it.id == e.id }.coerceAtLeast(0))
                    } else openEntry(ctx, e)
                },
                onLongClick = { vm.toggleSelect(it.id) },
            )
        }
    }

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            title = { Text("Filtros") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(ext, { ext = it }, label = { Text("Extensiones (ej. mp4, pdf)") }, singleLine = true)
                    OutlinedTextField(minMb, { minMb = it }, label = { Text("Tamaño mínimo (MB)") }, singleLine = true)
                    OutlinedTextField(folderText, { folderText = it }, label = { Text("Carpeta contiene…") }, singleLine = true)
                    Text("Modificado en…", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Siempre", 1 to "Hoy", 7 to "7 días", 30 to "30 días", 365 to "1 año").forEach { (d, l) ->
                            FilterChip(selected = days == d, onClick = { days = d }, label = { Text(l) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilters = false }) { Text("Aplicar") } },
            dismissButton = { TextButton(onClick = { ext = ""; minMb = ""; folderText = ""; days = 0 }) { Text("Limpiar") } },
        )
    }
}
