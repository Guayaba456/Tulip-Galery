# Tulip — galería multimedia personal (Android, Kotlin + Jetpack Compose)

App **local y privada**: sin internet, sin cuentas, sin anuncios, sin analítica. Trabaja sobre tus archivos reales
(MediaStore), no los importa ni los duplica.

> **Estado honesto (léelo primero):**
> el código fue escrito en un entorno **sin Android SDK ni acceso a Google Maven**, por lo que **la app NO ha sido compilada ni probada
> en un teléfono o emulador**. Lo único ejecutado es la lógica pura (ordenación, filtros, exclusiones, cola aleatoria,
> nombres, matrices de filtros): 18 comprobaciones pasaron con `kotlinc`. Es probable que el primer `assembleDebug` muestre
> algún error de compilación menor; el flujo de GitHub Actions te lo enseñará en el log. Ver `docs/LIMITACIONES.md`.

## Estructura

```
app/src/main/java/com/tulip/app/
  logic/      Lógica pura sin Android (probada): ordenación, filtros, exclusiones, cola aleatoria, carpetas, nombres, filtros de foto
  data/       MediaStore (MediaRepository), operaciones reales (FileOps), Room (favoritos, colecciones, exclusiones), DataStore (ajustes)
  playback/   PlaybackService (Media3, servicio mediaPlayback) y PlayerConnection
  ui/         Pantallas Compose: Galería, Álbumes, Aleatorio (vídeos/fotos), Archivos, Favoritos, Cámara, Ajustes, Visor, Filtros
app/src/test/ Pruebas unitarias JVM de la lógica
.github/workflows/build-apk.yml   Compila el APK de depuración en GitHub
```

## Abrir en Android Studio

1. Android Studio **Koala (2024.1.1) o superior**, con JDK 17 (viene incluido).
2. `File > Open` y elige la carpeta `Tulip`. Espera a que termine *Gradle Sync* (descarga Gradle 8.9 y dependencias).
3. Conecta el teléfono (Opciones de desarrollador + Depuración USB) o crea un emulador **Android 11+ (API 30+)**.
4. Pulsa **Run ▶**.

## Compilar por línea de comandos

```bash
./gradlew :app:testDebugUnitTest      # pruebas unitarias
./gradlew :app:assembleDebug          # genera el APK
```
Ruta exacta del APK: `app/build/outputs/apk/debug/app-debug.apk`
Instalar: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Compilar sin Android Studio (GitHub)

Sigue `docs/GITHUB_SIN_ANDROID_STUDIO.md`.

## Funciones

| Sección | Qué hace | Cómo |
|---|---|---|
| Galería | Fotos y vídeos reales, carpetas y subcarpetas, cuadrícula/lista, tamaño de miniatura, selección múltiple, búsqueda, filtro foto/vídeo; se actualiza solo si otra app cambia archivos | MediaStore + ContentObserver |
| Álbumes y carpetas | Lista plana de carpetas reales; **crear, renombrar, eliminar carpetas**; excluir de aleatorio; **colecciones virtuales** (marcadas distinto) | FileOps, Room |
| Mover / copiar | A carpeta real, con detección de nombres repetidos (conservar ambos / omitir / cancelar). No sobrescribe | `RELATIVE_PATH` + `createWriteRequest` |
| Vídeos aleatorios | Cola sin repetir, anterior/siguiente/pausa, mezclar, pantalla completa, continuar sesión, **audio con pantalla apagada**, notificación y controles de bloqueo, auriculares/llamadas | Media3 `MediaSessionService` |
| Carpetas excluidas | Interruptores persistentes; siguen visibles en galería; clave = volumen + ruta completa (no el nombre) | Room |
| Fotos aleatorias | Tarjetas deslizables, zoom, info, renombrar, compartir, favorito, eliminar con confirmación, copiar/mover, anterior, pausar/salir, filtro por carpeta y tipo; sin repetir | Pager + MediaStore |
| Todos los archivos | Categorías, orden (nombre, tamaño, modificación, incorporación, captura), filtros (extensión, tamaño, carpeta, fecha), búsqueda por nombre/ruta | MediaStore.Files |
| Favoritos | Sin mover archivos; filtros; aviso de archivos que desaparecen; reenlace por nombre+tamaño | Room |
| Cámara | CameraX; sólo se abre al entrar y se libera al salir; foto y vídeo en `DCIM/Tulip/` | CameraX |
| Filtros | B/N, sepia, frío, cálido, rosa, salvia, brillo, contraste, saturación; guarda **copia** en `Pictures/Tulip/` | ColorMatrix |
| Ajustes | Tema claro/oscuro/auto, paleta pastel (principal, acento, fondo), miniaturas, vista y orden por defecto, reproducción, exclusiones, limpiar caché, permisos | DataStore |

Navegación: barra inferior con Galería, Álbumes, Aleatorio (pestañas Vídeos/Fotos), Archivos y Favoritos. La Cámara y los Ajustes están en la barra superior.
