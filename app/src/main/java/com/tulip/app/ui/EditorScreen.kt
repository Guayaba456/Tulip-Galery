@file:OptIn(ExperimentalMaterial3Api::class)

package com.tulip.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulip.app.MainViewModel
import com.tulip.app.data.PhotoFilters
import com.tulip.app.logic.FilterPreset
import com.tulip.app.logic.PhotoFilterMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val media by vm.media.collectAsStateWithLifecycle()
    val entry = remember(media, id) { media.firstOrNull { it.id == id } }
    val scope = rememberCoroutineScope()
    var preset by remember { mutableStateOf(FilterPreset.NONE) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var saving by remember { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, entry?.uri) {
        value = entry?.let { e -> withContext(Dispatchers.IO) { PhotoFilters.decode(ctx, Uri.parse(e.uri), 1600) } }
    }
    val matrix = remember(preset, brightness, contrast, saturation) { PhotoFilterMath.build(preset, brightness, contrast, saturation) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Filtros (copia nueva)") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } })
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val bmp = bitmap
            if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 380.dp), contentScale = ContentScale.Fit, colorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix)))
            else Box(Modifier.fillMaxWidth().height(240.dp)) { CircularProgressIndicator(Modifier.padding(24.dp)) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPreset.entries.forEach { p -> FilterChip(selected = preset == p, onClick = { preset = p }, label = { Text(p.label) }) }
            }
            Text("Brillo"); Slider(brightness, { brightness = it }, valueRange = -0.5f..0.5f)
            Text("Contraste"); Slider(contrast, { contrast = it }, valueRange = 0.5f..1.8f)
            Text("Saturación"); Slider(saturation, { saturation = it }, valueRange = 0f..2f)
            Text("La original no se modifica. Se guardará una copia nueva en ${PhotoFilters.DEST}.", style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = entry != null && !saving,
                onClick = {
                    val e = entry ?: return@Button
                    saving = true
                    scope.launch {
                        val r = PhotoFilters.saveCopy(ctx, e, matrix)
                        saving = false
                        vm.message(r.fold({ "Copia guardada en $it" }, { "No se pudo guardar: ${it.message}" }))
                        if (r.isSuccess) { vm.refreshMedia(); onBack() }
                    }
                },
            ) { Text(if (saving) "Guardando…" else "Guardar como copia") }
        }
    }
}
