@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.tulip.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulip.app.MainViewModel
import com.tulip.app.logic.*

@Composable
fun AlbumsScreen(vm: MainViewModel, onOpenFolder: (String) -> Unit, onOpenCollection: (Long) -> Unit, onCamera: () -> Unit, onSettings: () -> Unit) {
    val media by vm.media.collectAsStateWithLifecycle()
    val cols by vm.collections.collectAsStateWithLifecycle()
    val colItems by vm.collectionItems.collectAsStateWithLifecycle()
    val excluded by vm.excludedKeys.collectAsStateWithLifecycle()
    val visual = remember(media) { media.filter { it.kind == MediaKind.IMAGE || it.kind == MediaKind.VIDEO } }
    val folders = remember(visual) { FolderTree.flat(visual) }
    val volumes = remember(visual) { visual.associate { FolderExclusions.normalize(it.relativePath) to it.volume } }
    var menuFor by remember { mutableStateOf<FolderInfo?>(null) }
    var dialog by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<FolderInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Álbumes y carpetas") }, actions = {
                IconButton(onClick = onCamera) { Icon(Icons.Rounded.PhotoCamera, "Cámara") }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Ajustes") }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { target = null; dialog = "newFolder" }, icon = { Icon(Icons.Rounded.CreateNewFolder, null) }, text = { Text("Nueva carpeta") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionLabel("Carpetas reales del teléfono") }
            if (folders.isEmpty()) item { Text("Aún no hay carpetas con fotos o vídeos.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(folders, key = { it.path }) { f ->
                val isExcluded = FolderExclusions.isExcluded(volumes[f.path] ?: "external_primary", f.path, excluded)
                ElevatedCard(Modifier.fillMaxWidth().clickable { onOpenFolder(f.path) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.titleSmall)
                            Text(f.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (isExcluded) Text("Excluida de la reproducción aleatoria", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Text("${f.count}", Modifier.padding(end = 4.dp))
                        Box {
                            IconButton(onClick = { menuFor = f }) { Icon(Icons.Rounded.MoreVert, "Opciones de carpeta") }
                            DropdownMenu(expanded = menuFor == f, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(text = { Text("Nueva subcarpeta") }, onClick = { target = f; menuFor = null; dialog = "newFolder" })
                                DropdownMenuItem(text = { Text("Renombrar") }, onClick = { target = f; menuFor = null; dialog = "rename" })
                                DropdownMenuItem(
                                    text = { Text(if (isExcluded) "Incluir en aleatorio" else "Excluir de aleatorio") },
                                    onClick = { vm.setExcluded(volumes[f.path] ?: "external_primary", f.path, !isExcluded); menuFor = null },
                                )
                                DropdownMenuItem(text = { Text("Eliminar carpeta…") }, onClick = { target = f; menuFor = null; dialog = "delete" })
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)); SectionLabel("Colecciones (virtuales: no mueven ni duplican archivos)") }
            items(cols, key = { "c${it.id}" }) { col ->
                val n = colItems.count { it.collectionId == col.id }
                ElevatedCard(Modifier.fillMaxWidth().clickable { onOpenCollection(col.id) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Layers, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(col.name, style = MaterialTheme.typography.titleSmall)
                            Text("Colección virtual · $n archivo(s)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { OutlinedButton(onClick = { dialog = "newCollection" }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Nueva colección") } }
        }
    }

    val t = target
    when (dialog) {
        "newFolder" -> DestinationDialog(if (t == null) "Crear carpeta" else "Nueva subcarpeta en ${t.path}", "Crear", folders, { dialog = "" }, requireNew = true, initial = t?.path ?: "") { path ->
            // path = ubicación elegida (+ subcarpeta nueva opcional)
            val clean = FolderExclusions.normalize(path)
            val parent = clean.trimEnd('/').substringBeforeLast('/', "")
            val name = clean.trimEnd('/').substringAfterLast('/')
            vm.createFolder(if (parent.isEmpty()) "" else "$parent/", name)
        }
        "rename" -> if (t != null) TextInputDialog("Renombrar carpeta", "Nuevo nombre", t.name, onDismiss = { dialog = "" }) { vm.renameFolder(t.path, it) }
        "delete" -> if (t != null) ConfirmDialog("¿Eliminar «${t.name}»?", "Se eliminarán los ${t.count} archivo(s) de medios que contiene (incluidas subcarpetas) tras la confirmación del sistema.", "Continuar", { dialog = "" }) { vm.deleteFolder(t.path) }
        "newCollection" -> TextInputDialog("Nueva colección virtual", "Nombre", onDismiss = { dialog = "" }) { vm.createCollection(it) }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

@Composable
fun CollectionScreen(vm: MainViewModel, id: Long, openViewer: (List<Long>, Int) -> Unit, onBack: () -> Unit) {
    val media by vm.media.collectAsStateWithLifecycle()
    val cols by vm.collections.collectAsStateWithLifecycle()
    val items by vm.collectionItems.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val favIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val col = cols.firstOrNull { it.id == id }
    val byId = remember(media) { media.associateBy { it.id } }
    val mine = items.filter { it.collectionId == id }
    val available = mine.mapNotNull { byId[it.mediaId] }
    val missing = mine.filter { byId[it.mediaId] == null }
    var dialog by remember { mutableStateOf("") }
    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text(col?.name ?: "Colección"); Text("Colección virtual", style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } },
            actions = {
                IconButton(onClick = { dialog = "rename" }) { Icon(Icons.Rounded.Edit, "Renombrar colección") }
                IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Rounded.Delete, "Eliminar colección") }
            },
        )
    }) { pad ->
        Column(Modifier.padding(pad)) {
            if (missing.isNotEmpty()) {
                Card(Modifier.padding(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${missing.size} archivo(s) ya no están disponibles", style = MaterialTheme.typography.titleSmall)
                        missing.take(5).forEach { m ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.lastName, Modifier.weight(1f), maxLines = 1)
                                TextButton(onClick = { vm.removeFromCollection(id, m.mediaId) }) { Text("Quitar") }
                            }
                        }
                    }
                }
            }
            if (available.isEmpty()) EmptyState(Icons.Rounded.Layers, "Colección vacía", "Selecciona archivos en la galería y usa «Añadir a colección».")
            else MediaGrid(available, settings, emptySet(), favIds, onClick = { i, _ -> openViewer(available.map { it.id }, i) }, onLongClick = { vm.removeFromCollection(id, it.id); vm.message("Quitado de la colección (el archivo sigue en su carpeta)") })
        }
    }
    when (dialog) {
        "rename" -> TextInputDialog("Renombrar colección", "Nombre", col?.name ?: "", onDismiss = { dialog = "" }) { vm.renameCollection(id, it) }
        "delete" -> ConfirmDialog("¿Eliminar la colección?", "Sólo se elimina la agrupación; los archivos permanecen en sus carpetas.", "Eliminar", { dialog = "" }) { vm.deleteCollection(id); onBack() }
    }
}
