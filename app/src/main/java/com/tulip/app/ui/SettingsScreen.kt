@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tulip.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import com.tulip.app.MainViewModel
import com.tulip.app.data.SettingsKeys
import com.tulip.app.logic.*

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val rows by vm.excludedRows.collectAsStateWithLifecycle()
    var addExcl by remember { mutableStateOf(false) }
    val granted = remember { hasMediaPermission(ctx) }

    Scaffold(topBar = { TopAppBar(title = { Text("Ajustes") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Tema")
            Chips(listOf("system" to "Automático", "light" to "Claro", "dark" to "Oscuro"), s.theme) { v -> vm.updateSetting { it[SettingsKeys.theme] = v } }
            SectionLabel("Colores pastel")
            Swatches("Principal", s.primary) { v -> vm.updateSetting { it[SettingsKeys.primary] = v } }
            Swatches("Acento", s.accent) { v -> vm.updateSetting { it[SettingsKeys.accent] = v } }
            Swatches("Fondo", s.background) { v -> vm.updateSetting { it[SettingsKeys.background] = v } }

            SectionLabel("Galería")
            Text("Tamaño de miniaturas: ${s.thumbDp} dp")
            Slider(s.thumbDp.toFloat(), { v -> vm.updateSetting { it[SettingsKeys.thumbDp] = v.toInt() } }, valueRange = 80f..200f)
            Text("Calidad de miniaturas")
            Chips(listOf("low" to "Baja", "medium" to "Media", "high" to "Alta"), s.thumbQuality) { v -> vm.updateSetting { it[SettingsKeys.thumbQuality] = v } }
            Text("Vista predeterminada")
            Chips(listOf("grid" to "Cuadrícula", "list" to "Lista"), if (s.listView) "list" else "grid") { v -> vm.updateSetting { it[SettingsKeys.listView] = v == "list" } }
            Text("Orden predeterminado")
            val fields = listOf(SortField.NAME to "Nombre", SortField.SIZE to "Tamaño", SortField.MODIFIED to "Modificación", SortField.ADDED to "Incorporación", SortField.TAKEN to "Captura")
            Chips(fields.map { it.first.name to it.second }, s.sort.field.name) { v -> vm.updateSetting { it[SettingsKeys.sort] = SortOrder(SortField.valueOf(v), s.sort.ascending).encode() } }
            Chips(listOf("asc" to "Ascendente", "desc" to "Descendente"), if (s.sort.ascending) "asc" else "desc") { v -> vm.updateSetting { it[SettingsKeys.sort] = SortOrder(s.sort.field, v == "asc").encode() } }

            SectionLabel("Reproducción aleatoria")
            SwitchRow("Repetir la cola al terminarla", s.loopQueue) { v -> vm.updateSetting { it[SettingsKeys.loopQueue] = v } }
            SwitchRow("Seguir reproduciendo en segundo plano (pantalla apagada u otra app)", s.bgPlayback) { v -> vm.updateSetting { it[SettingsKeys.bgPlayback] = v } }
            SectionLabel("Carpetas excluidas de la reproducción aleatoria")
            Text("Siguen visibles en la galería; sólo quedan fuera de la cola aleatoria (incluidas sus subcarpetas).", style = MaterialTheme.typography.bodySmall)
            if (rows.isEmpty()) Text("Ninguna carpeta excluida.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(r.enabled, { v -> vm.setExcludedEnabled(r.key, v) })
                    Text(r.label, Modifier.weight(1f).padding(horizontal = 12.dp))
                    IconButton(onClick = { vm.removeExclusion(r.key) }) { Icon(Icons.Rounded.Delete, "Quitar de la lista") }
                }
            }
            OutlinedButton(onClick = { addExcl = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Excluir una carpeta") }

            SectionLabel("Caché")
            Button(onClick = {
                ctx.imageLoader.memoryCache?.clear()
                ctx.imageLoader.diskCache?.clear()
                vm.message("Caché de miniaturas limpiada (tus archivos no se tocan)")
            }) { Text("Limpiar caché de miniaturas") }

            SectionLabel("Permisos y almacenamiento accesible")
            Text(if (granted) "Lectura de medios: concedida" else "Lectura de medios: no concedida")
            val total = media.sumOf { it.size }
            Text("Tulip ve ${media.size} archivos (${formatSize(total)}): " + MediaKind.entries.joinToString(", ") { k -> "${k.name.lowercase()} ${media.count { it.kind == k }}" }, style = MaterialTheme.typography.bodySmall)
            Text("No se usa acceso total al almacenamiento ni internet. Mover, renombrar y borrar archivos pide la confirmación del sistema de Android.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) { Text("Abrir permisos de la app") }
        }
    }

    if (addExcl) {
        val options = remember(media, rows) {
            media.filter { it.kind == MediaKind.VIDEO }.associateBy { it.folderKey }.values
                .filter { e -> rows.none { it.key == e.folderKey } && e.relativePath.isNotEmpty() }
                .sortedBy { it.relativePath.lowercase() }
        }
        AlertDialog(
            onDismissRequest = { addExcl = false },
            title = { Text("Carpetas con vídeos") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (options.isEmpty()) Text("No hay más carpetas con vídeos.")
                    options.forEach { e ->
                        Text(e.relativePath, Modifier.fillMaxWidth().clickable { vm.setExcluded(e.volume, e.relativePath, true); addExcl = false }.padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addExcl = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun Chips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (k, l) -> FilterChip(selected = selected == k, onClick = { onSelect(k) }, label = { Text(l) }) }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun Swatches(label: String, current: Int, onPick: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Pastel.choices.forEach { (name, c) ->
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                    .border(if (c == current) 3.dp else 1.dp, if (c == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}
