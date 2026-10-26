# Limitaciones conocidas y estado de pruebas

## Qué se ejecutó de verdad
- **Lógica pura** (`logic/`): compilada con `kotlinc 2.0.20` y ejecutada con un arnés mínimo → 18/18 comprobaciones correctas:
  ordenación (incl. fecha de captura sin inventar), filtros y búsqueda, exclusiones por volumen+ruta (misma nombre en otra ruta no se excluye),
  cola aleatoria sin repetidos y con tope, ciclo sin repetir el último, serialización de la cola, navegación por carpetas, nombres, clasificación y matrices de filtros.
  Las pruebas JUnit equivalentes están en `app/src/test` (no se ejecutaron con Gradle/JUnit real aquí).
- Revisión de **sintaxis** de todo el Kotlin con `kotlinc` (sin errores de sintaxis ni declaraciones duplicadas).

## Qué NO se pudo compilar ni probar
- El proyecto Android completo **no se compiló** (sin Android SDK ni acceso a Google Maven en el entorno). No existe garantía de que `assembleDebug` pase a la primera.
- **Nada se probó en emulador ni teléfono.** Sin verificar en dispositivo: lectura real de MediaStore, creación de carpetas, mover/renombrar/borrar con los diálogos del sistema,
  reproducción en segundo plano con pantalla apagada, notificación y controles de bloqueo, cámara y su liberación, rendimiento con bibliotecas grandes.
- **No hay pruebas instrumentadas.**
- Los números de versión de bibliotecas (AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09.00, Media3 1.4.1, CameraX 1.3.4, Room 2.6.1) son los que se conocen como compatibles, pero no se resolvieron contra el repositorio.

## Limitaciones de diseño / Android
- **Android 11+ (API 30+)** es el mínimo: las peticiones de escritura/borrado del sistema (`createWriteRequest`/`createDeleteRequest`) empiezan ahí.
- **Crear carpeta:** sólo dentro de DCIM, Pictures, Movies, Music, Download, Documents, Podcasts (regla de Android). Se intenta crear con la API de archivos; si el sistema no lo permite, la carpeta aparece al mover el primer archivo.
  Una carpeta **vacía** no aparece en Tulip (MediaStore sólo conoce carpetas con archivos), aunque sí en el administrador de archivos.
- **Renombrar carpeta** = mover todos sus archivos de medios a la ruta nueva. Archivos no indexados dentro de la carpeta y la carpeta vacía antigua pueden quedar; Tulip no borra carpetas de origen automáticamente.
- **Tipo ↔ carpeta raíz:** Android limita dónde puede ir cada tipo (p. ej. una imagen no puede ir a `Movies`). Si ocurre, se informa del error por archivo.
- **Entre volúmenes** (tarjeta SD ↔ memoria interna) no se mueve: la ruta se interpreta en el volumen de cada archivo.
- **«Reemplazar con confirmación» en conflictos de nombre: pendiente.** Se ofrece *Conservar ambos*, *Omitir* o *Cancelar*; nunca se sobrescribe.
- **Todos los archivos:** en Android 13+ sólo se ven medios (imágenes, vídeo, audio) a través de MediaStore; documentos y comprimidos dependen de lo que el sistema indexe. No se accede a datos privados de otras apps ni se usa acceso total al almacenamiento. Navegación por SAF para documentos: **pendiente**.
- **Filtro de fechas** en Archivos: rangos rápidos (hoy/7/30/365 días), no calendario libre.
- **Cola aleatoria:** máx. 500 vídeos por tanda (para no saturar el IPC con el servicio); «Mezclar» crea otra tanda. Al agotarla se repite el mismo orden si «Repetir la cola» está activo.
- **Favoritos** se reenlazan por nombre+tamaño si desaparece el id; **colecciones** sólo conservan el id (si falta, se avisa y se puede quitar).
- **Memoria:** sólo se guardan metadatos de todos los archivos (no bitmaps); las miniaturas se cargan bajo demanda. Con decenas de miles de archivos la primera carga puede tardar; no hay paginación a nivel de consulta.
- Cámara: si el dispositivo no soporta foto+vídeo simultáneos, se desactiva la grabación y se mantiene la foto.
- Los filtros usan el decodificador del sistema (JPEG de salida); no hay edición avanzada (a propósito).

## Funciones opcionales NO implementadas (sección 13 del encargo)
Detección de duplicados y estadísticas de almacenamiento (sólo hay un resumen de conteo/tamaño en Ajustes). «Compartir varios», «Revisión rápida» y «Continuar reproducción» **sí** están.
