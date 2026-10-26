@file:OptIn(ExperimentalMaterial3Api::class)

package com.tulip.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tulip.app.MainViewModel
import com.tulip.app.ViewerRequest

enum class TopDest(val route: String, val label: String, val icon: ImageVector) {
    Gallery("gallery", "Galería", Icons.Rounded.PhotoLibrary),
    Albums("albums", "Álbumes", Icons.Rounded.Folder),
    Random("random", "Aleatorio", Icons.Rounded.Shuffle),
    Files("files", "Archivos", Icons.Rounded.Description),
    Favorites("favorites", "Favoritos", Icons.Rounded.Favorite),
}

fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

fun hasMediaPermission(ctx: Context): Boolean =
    mediaPermissions().all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

@Composable
fun TulipRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaPermission(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = hasMediaPermission(ctx)
        if (now != granted) { granted = now; vm.refreshMedia() }
    }
    if (!granted) {
        PermissionGate(onResult = { granted = hasMediaPermission(ctx); vm.refreshMedia() })
        return
    }

    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val topLevel = TopDest.entries.any { it.route == route }

    val openViewer: (List<Long>, Int) -> Unit = { ids, i ->
        vm.viewerRequest.value = ViewerRequest(ids, i)
        nav.navigate("viewer")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (topLevel) NavigationBar {
                TopDest.entries.forEach { d ->
                    NavigationBarItem(
                        selected = route == d.route,
                        onClick = {
                            vm.clearSelection()
                            nav.navigate(d.route) { popUpTo("gallery") { saveState = true }; launchSingleTop = true; restoreState = true }
                        },
                        icon = { Icon(d.icon, d.label) },
                        label = { Text(d.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "gallery", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable("gallery") { GalleryScreen(vm, openViewer, { nav.navigate("camera") }, { nav.navigate("settings") }) }
            composable("albums") {
                AlbumsScreen(vm, { vm.galleryPath.value = it; nav.navigate("gallery") { popUpTo("gallery") { inclusive = true } } },
                    { nav.navigate("collection/$it") }, { nav.navigate("camera") }, { nav.navigate("settings") })
            }
            composable("random") { RandomScreen(vm, openViewer = openViewer, onEdit = { nav.navigate("edit/${it.id}") }, onExit = { nav.navigate("gallery") }, onSettings = { nav.navigate("settings") }) }
            composable("files") { FilesScreen(vm, openViewer, { nav.navigate("camera") }, { nav.navigate("settings") }) }
            composable("favorites") { FavoritesScreen(vm, openViewer, { nav.navigate("settings") }) }
            composable("camera") { CameraScreen(onBack = { nav.popBackStack() }) }
            composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
            composable("viewer") { ViewerScreen(vm, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("edit/${it.id}") }) }
            composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                EditorScreen(vm, entry.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() })
            }
            composable("collection/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                CollectionScreen(vm, entry.arguments?.getLong("id") ?: -1L, openViewer, onBack = { nav.popBackStack() })
            }
        }
    }
    ConflictDialog(vm)
}

@Composable
private fun PermissionGate(onResult: () -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    var asked by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Bienvenido a Tulip", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Para mostrar tus fotos, vídeos y archivos, Tulip necesita permiso de lectura de medios. " +
                    "Todo se queda en tu teléfono: la app no tiene permiso de internet. " +
                    "No pide acceso total al almacenamiento; para mover o borrar archivos, Android te mostrará su propio diálogo de confirmación.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { asked = true; launcher.launch(mediaPermissions()) }) { Text("Conceder permiso") }
            if (asked) {
                TextButton(onClick = {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                }) { Text("Abrir ajustes de la app") }
            }
        }
    }
}
