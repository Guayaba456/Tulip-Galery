@file:OptIn(ExperimentalMaterial3Api::class)

package com.tulip.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DEST = "DCIM/Tulip"

@Composable
fun CameraScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> granted = r[Manifest.permission.CAMERA] == true }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }

    Scaffold(topBar = {
        TopAppBar(title = { Column { Text("Cámara"); Text("Se guarda en $DEST/", style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } })
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            // La cámara sólo existe mientras este composable está en pantalla con el permiso concedido.
            if (granted) CameraContent() else Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tulip necesita permiso de cámara para esta pestaña. El resto de la app funciona sin él.")
                Button(onClick = { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }, Modifier.padding(top = 12.dp)) { Text("Conceder permiso") }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun BoxScope.CameraContent() {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var back by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var videoOk by remember { mutableStateOf(true) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val videoCapture = remember {
        VideoCapture.withOutput(
            Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build(),
        )
    }

    DisposableEffect(owner, back) {
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val sel = if (back) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            p.unbindAll()
            try {
                p.bindToLifecycle(owner, sel, preview, imageCapture, videoCapture); videoOk = true
            } catch (e: Exception) {
                try { p.unbindAll(); p.bindToLifecycle(owner, sel, preview, imageCapture); videoOk = false }
                catch (e2: Exception) { status = "No se pudo iniciar la cámara" }
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            recording?.stop()
            provider?.unbindAll()     // libera la cámara al salir de la pantalla
        }
    }

    AndroidView({ previewView }, Modifier.fillMaxSize())
    if (status.isNotEmpty()) Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), shape = MaterialTheme.shapes.medium, tonalElevation = 4.dp) { Text(status, Modifier.padding(10.dp)) }

    Row(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { back = !back }, enabled = recording == null) { Icon(Icons.Rounded.Cameraswitch, "Cambiar cámara") }
        FilledIconButton(onClick = {
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Tulip_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()))
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, DEST)
            }
            val opts = ImageCapture.OutputFileOptions.Builder(ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v).build()
            imageCapture.takePicture(opts, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(r: ImageCapture.OutputFileResults) { status = "Foto guardada en $DEST/" }
                override fun onError(e: ImageCaptureException) { status = "Error al guardar: ${e.message}" }
            })
        }, modifier = Modifier.size(72.dp)) { Icon(Icons.Rounded.PhotoCamera, "Tomar foto", Modifier.size(32.dp)) }
        FilledTonalIconButton(onClick = {
            val rec = recording
            if (rec != null) { rec.stop(); recording = null; return@FilledTonalIconButton }
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Tulip_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()))
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, DEST)
            }
            val out = MediaStoreOutputOptions.Builder(ctx.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(v).build()
            var pending = videoCapture.output.prepareRecording(ctx, out)
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
            recording = pending.start(ContextCompat.getMainExecutor(ctx)) { ev ->
                if (ev is VideoRecordEvent.Finalize) {
                    recording = null
                    status = if (ev.hasError()) "Error de grabación" else "Vídeo guardado en $DEST/"
                }
            }
            status = "Grabando…"
        }, enabled = videoOk) { Icon(if (recording != null) Icons.Rounded.Stop else Icons.Rounded.Videocam, if (recording != null) "Detener grabación" else "Grabar vídeo") }
    }
}
