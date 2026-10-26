@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.tulip.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tulip.app.MainViewModel
import com.tulip.app.data.ConflictPolicy
import com.tulip.app.data.Settings
import com.tulip.app.logic.*
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- formato

fun formatSize(b: Long): String {
    if (b < 1024) return "$b B"
    val u = listOf("KB", "MB", "GB", "TB")
    var v = b.toDouble()
    var i = -1
    while (v >= 1024 && i < u.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.getDefault(), "%.1f %s", v, u[i])
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.getDefault(), "%d:%02d", m, sec)
}

fun formatDate(ms: Long, ctx: Context): String =
    if (ms <= 0) "—" else DateFormat.getMediumDateFormat(ctx).format(Date(ms)) + " " + DateFormat.getTimeFormat(ctx).format(Date(ms))

// ---------------------------------------------------------------- intents

fun openEntry(ctx: Context, e: MediaEntry) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(e.uri), e.mime.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "No hay ninguna app para abrir este archivo", Toast.LENGTH_SHORT).show()
    }
}

fun shareEntries(ctx: Context, list: List<MediaEntry>) {
    if (list.isEmpty()) return
    val uris = ArrayList(list.map { Uri.parse(it.uri) })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).setType(list[0].mime.ifBlank { "*/*" }).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        val tops = list.map { it.mime.substringBefore('/') }.toSet()
        val type = if (tops.size == 1 && tops.first().isNotEmpty()) tops.first() + "/*" else "*/*"
        Intent(Intent.ACTION_SEND_MULTIPLE).setType(type).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(intent, "Compartir"))
}

// ---------------------------------------------------------------- miniaturas

fun thumbPx(settings: Settings, dp: Int, density: Float): Int {
    val q = when (settings.thumbQuality) { "low" -> 0.6f; "high" -> 1.6f; else -> 1f }
    return (dp * density * q).toInt().coerceIn(64, 1024)
}

@Composable
fun Thumb(entry: MediaEntry, sizePx: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val req = remember(entry.uri, sizePx) {
        ImageRequest.Builder(ctx).data(Uri.parse(entry.uri)).size(sizePx).crossfade(true).build()
    }
    AsyncImage(model = req, contentDescription = entry.name, contentScale = scale, modifier = modifier)
}

fun kindIcon(k: MediaKind) = when (k) {
    MediaKind.IMAGE -> Icons.Rounded.Image
    MediaKind.VIDEO -> Icons.Rounded.Videocam
    MediaKind.AUDIO -> Icons.Rounded.MusicNote
    MediaKind.DOCUMENT -> Icons.Rounded.Description
    MediaKind.ARCHIVE -> Icons.Rounded.Inventory2
    MediaKind.OTHER -> Icons.Rounded.InsertDriveFile
}

// ---------------------------------------------------------------- cuadrícula / lista

@Composable
fun MediaGrid(
    entries: List<MediaEntry>,
    settings: Settings,
    selected: Set<Long>,
    favorites: Set<Long>,
    onClick: (Int, MediaEntry) -> Unit,
    onLongClick: (MediaEntry) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    if (settings.listView) {
        LazyColumn(modifier, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (header != null) item { header() }
            itemsIndexed(entries, key = { _, e -> e.id }) { i, e ->
                val sel = e.id in selected
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .combinedClickable(onClick = { onClick(i, e) }, onLongClick = { onLongClick(e) })
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))) {
                        if (e.kind == MediaKind.IMAGE || e.kind == MediaKind.VIDEO) Thumb(e, thumbPx(settings, 64, density), Modifier.fillMaxSize())
                        else Icon(kindIcon(e.kind), null, Modifier.align(Alignment.Center))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        val extra = if (e.kind == MediaKind.VIDEO && e.durationMs > 0) " · ${formatDuration(e.durationMs)}" else ""
                        Text(
                            "${formatSize(e.size)}$extra · ${formatDate(e.dateModifiedMs, ctx)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (e.id in favorites) Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFE58BA8), modifier = Modifier.size(18.dp))
                    if (sel) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(settings.thumbDp.dp),
            modifier = modifier,
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
            gridItemsIndexed(entries, key = { _, e -> e.id }) { i, e ->
                val sel = e.id in selected
                Box(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickable(onClick = { onClick(i, e) }, onLongClick = { onLongClick(e) }),
                ) {
                    if (e.kind == MediaKind.IMAGE || e.kind == MediaKind.VIDEO) Thumb(e, thumbPx(settings, settings.thumbDp, density), Modifier.fillMaxSize())
                    else Icon(kindIcon(e.kind), null, Modifier.align(Alignment.Center).size(32.dp))
                    if (e.kind == MediaKind.VIDEO) {
                        Row(
                            Modifier.align(Alignment.BottomStart).padding(6.dp)
                                .background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            if (e.durationMs > 0) Text(formatDuration(e.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (e.id in favorites) Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFF2A9C0), modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
                    if (sel) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)))
                        Icon(Icons.Rounded.CheckCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String = "", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint.isNotEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- diálogos reutilizables

@Composable
fun TextInputDialog(title: String, label: String, initial: String = "", confirm: String = "Aceptar", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text); onDismiss() }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Elige una carpeta real de destino (o crea una nueva dentro de otra). */
@Composable
fun DestinationDialog(
    title: String, confirm: String, folders: List<FolderInfo>,
    onDismiss: () -> Unit, onPick: (String) -> Unit,
    requireNew: Boolean = false, initial: String = "",
) {
    val paths = remember(folders) { (NameRules.StandardRoots.sorted().map { "$it/" } + folders.map { it.path }).distinct().sorted() }
    var chosen by remember { mutableStateOf(initial) }
    var newName by remember { mutableStateOf("") }
    val finalPath = if (newName.isBlank()) chosen else chosen + (NameRules.sanitizeFolderName(newName) ?: "") + "/"
    val valid = chosen.isNotEmpty() &&
        (if (requireNew) NameRules.sanitizeFolderName(newName) != null else (newName.isBlank() || NameRules.sanitizeFolderName(newName) != null))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Carpeta de destino (real, en el almacenamiento):", style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(paths) { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (p == chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { chosen = p }.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(p, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                OutlinedTextField(newName, { newName = it }, label = { Text(if (requireNew) "Nombre de la carpeta nueva" else "Nueva subcarpeta aquí (opcional)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                if (valid) Text("Destino: $finalPath", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onPick(finalPath); onDismiss() }, enabled = valid) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ConflictDialog(vm: MainViewModel) {
    val c by vm.conflict.collectAsStateWithLifecycle()
    val p = c ?: return
    AlertDialog(
        onDismissRequest = { vm.resolveConflict(null) },
        title = { Text("Nombres repetidos") },
        text = {
            Text(
                "${p.clashes.size} archivo(s) ya existen en «${p.dest}»: ${p.clashes.take(3).joinToString(", ")}${if (p.clashes.size > 3) "…" else ""}.\n\n" +
                    "• Conservar ambos: el nuevo se renombra («nombre (1)»).\n• Omitir: los repetidos no se tocan.\n\n" +
                    "No se sobrescribe nada sin tu autorización explícita.",
            )
        },
        confirmButton = { TextButton(onClick = { vm.resolveConflict(ConflictPolicy.KEEP_BOTH) }) { Text("Conservar ambos") } },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.resolveConflict(ConflictPolicy.SKIP) }) { Text("Omitir") }
                TextButton(onClick = { vm.resolveConflict(null) }) { Text("Cancelar") }
            }
        },
    )
}

@Composable
fun AddToCollectionDialog(vm: MainViewModel, entries: List<MediaEntry>, onDismiss: () -> Unit) {
    val cols by vm.collections.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        TextInputDialog("Nueva colección virtual", "Nombre", onDismiss = onDismiss) { vm.createCollection(it, entries) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a colección") },
        text = {
            LazyColumn {
                items(cols) { col ->
                    ListItem(
                        headlineContent = { Text(col.name) },
                        leadingContent = { Icon(Icons.Rounded.Layers, null) },
                        modifier = Modifier.clickable { vm.addToCollection(col.id, entries); onDismiss() },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Nueva colección…") },
                        leadingContent = { Icon(Icons.Rounded.Add, null) },
                        modifier = Modifier.clickable { creating = true },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

// ---------------------------------------------------------------- barra de selección múltiple

@Composable
fun SelectionTopBar(vm: MainViewModel, entries: List<MediaEntry>, allVisible: List<MediaEntry>, onDone: () -> Unit = {}) {
    val ctx = LocalContext.current
    val media by vm.media.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf("") }   // delete | move | copy | collection | rename
    val folders = remember(media) { FolderTree.flat(media) }
    TopAppBar(
        title = { Text("${entries.size} seleccionado(s)") },
        navigationIcon = { IconButton(onClick = { vm.clearSelection(); onDone() }) { Icon(Icons.Rounded.Close, "Cancelar selección") } },
        actions = {
            IconButton(onClick = { vm.selectAll(allVisible.map { it.id }) }) { Icon(Icons.Rounded.SelectAll, "Seleccionar todo") }
            val media2 = entries.filter { it.kind == MediaKind.IMAGE || it.kind == MediaKind.VIDEO }
            if (media2.isNotEmpty()) IconButton(onClick = { vm.addFavorites(media2); vm.message("${media2.size} añadido(s) a favoritos") }) { Icon(Icons.Rounded.FavoriteBorder, "Añadir a favoritos") }
            IconButton(onClick = { shareEntries(ctx, entries) }) { Icon(Icons.Rounded.Share, "Compartir") }
            IconButton(onClick = { dialog = "move" }) { Icon(Icons.Rounded.DriveFileMove, "Mover a carpeta") }
            var menu by remember { mutableStateOf(false) }
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Más") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Copiar a carpeta") }, onClick = { menu = false; dialog = "copy" })
                DropdownMenuItem(text = { Text("Añadir a colección") }, onClick = { menu = false; dialog = "collection" })
                if (entries.size == 1) DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; dialog = "rename" })
                DropdownMenuItem(text = { Text("Eliminar…") }, onClick = { menu = false; dialog = "delete" })
            }
        },
    )
    when (dialog) {
        "delete" -> ConfirmDialog("¿Eliminar ${entries.size} archivo(s)?", "Se borrarán de forma real del teléfono, previa confirmación del sistema.", "Continuar", { dialog = "" }) { vm.delete(entries); vm.clearSelection(); onDone() }
        "move" -> DestinationDialog("Mover a carpeta", "Mover aquí", folders, { dialog = "" }) { vm.requestTransfer(entries, it, copy = false); vm.clearSelection(); onDone() }
        "copy" -> DestinationDialog("Copiar a carpeta", "Copiar aquí", folders, { dialog = "" }) { vm.requestTransfer(entries, it, copy = true); vm.clearSelection(); onDone() }
        "collection" -> AddToCollectionDialog(vm, entries) { dialog = ""; vm.clearSelection(); onDone() }
        "rename" -> TextInputDialog("Renombrar", "Nuevo nombre", entries.first().name, onDismiss = { dialog = "" }) { vm.rename(entries.first(), it); vm.clearSelection(); onDone() }
    }
}

// ---------------------------------------------------------------- información y acciones de un archivo

@Composable
fun EntryActionsSheet(
    vm: MainViewModel, entry: MediaEntry, onDismiss: () -> Unit,
    onEdit: ((MediaEntry) -> Unit)? = null, onOpenInGallery: ((MediaEntry) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val media by vm.media.collectAsStateWithLifecycle()
    val favIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val folders = remember(media) { FolderTree.flat(media) }
    var dialog by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            InfoLine("Extensión", entry.extension.ifEmpty { "—" })
            InfoLine("Tamaño", formatSize(entry.size))
            InfoLine("Ubicación", entry.relativePath.ifEmpty { "(raíz)" })
            if (entry.width > 0) InfoLine("Dimensiones", "${entry.width} × ${entry.height} px")
            if (entry.kind == MediaKind.VIDEO && entry.durationMs > 0) InfoLine("Duración", formatDuration(entry.durationMs))
            InfoLine("Tipo", entry.mime)
            InfoLine("Fecha de captura", entry.dateTakenMs?.let { formatDate(it, ctx) } ?: "no proporcionada por el archivo")
            InfoLine("Modificado", formatDate(entry.dateModifiedMs, ctx))
            InfoLine("Añadido al almacenamiento", formatDate(entry.dateAddedMs, ctx))
            Spacer(Modifier.height(12.dp))
            FlowActions {
                val fav = entry.id in favIds
                AssistChip(onClick = { vm.toggleFavorite(entry) }, label = { Text(if (fav) "Quitar favorito" else "Favorito") }, leadingIcon = { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { shareEntries(ctx, listOf(entry)) }, label = { Text("Compartir") }, leadingIcon = { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { dialog = "rename" }, label = { Text("Renombrar") }, leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { dialog = "move" }, label = { Text("Mover") }, leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { dialog = "copy" }, label = { Text("Copiar") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { openEntry(ctx, entry) }, label = { Text("Abrir con…") }, leadingIcon = { Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp)) })
                if (onOpenInGallery != null) AssistChip(onClick = { onOpenInGallery(entry); onDismiss() }, label = { Text("Abrir en galería") }, leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp)) })
                if (onEdit != null && entry.kind == MediaKind.IMAGE) AssistChip(onClick = { onEdit(entry); onDismiss() }, label = { Text("Filtros") }, leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { dialog = "delete" }, label = { Text("Eliminar") }, leadingIcon = { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)) })
            }
        }
    }
    when (dialog) {
        "rename" -> TextInputDialog("Renombrar", "Nuevo nombre", entry.name, onDismiss = { dialog = "" }) { vm.rename(entry, it); onDismiss() }
        "delete" -> ConfirmDialog("¿Eliminar «${entry.name}»?", "Se borrará de forma real del teléfono, previa confirmación del sistema.", "Continuar", { dialog = "" }) { vm.delete(listOf(entry)); onDismiss() }
        "move" -> DestinationDialog("Mover a carpeta", "Mover aquí", folders, { dialog = "" }) { vm.requestTransfer(listOf(entry), it, copy = false); onDismiss() }
        "copy" -> DestinationDialog("Copiar a carpeta", "Copiar aquí", folders, { dialog = "" }) { vm.requestTransfer(listOf(entry), it, copy = true); onDismiss() }
    }
}

@Composable
private fun InfoLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowActions(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}
