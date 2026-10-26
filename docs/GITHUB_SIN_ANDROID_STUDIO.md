# Obtener el APK sin instalar Android Studio (GitHub Actions)

Necesitas: una cuenta gratuita de GitHub y un navegador (en computadora es lo más cómodo).

## 1. Crear la cuenta y el repositorio
1. Entra a https://github.com y crea tu cuenta (o inicia sesión).
2. Arriba a la derecha: **+ → New repository**.
3. Nombre: `Tulip`. Déjalo **Private** si no quieres que sea público. **No** marques «Add a README».
4. **Create repository**.

## 2. Subir el proyecto

### Opción A — desde el navegador (sin instalar nada)
1. Descomprime `Tulip.zip` en tu computadora.
2. En el repositorio nuevo, pulsa **uploading an existing file**.
3. Abre la carpeta `Tulip` ya descomprimida, selecciona **todo su contenido** (no la carpeta en sí) y arrástralo a la página.
   - Debe subirse también la carpeta oculta **`.github`** (contiene el flujo). En Mac: `Cmd + Shift + .` muestra las ocultas.
   - Comprueba que aparezcan `gradlew`, `gradle/wrapper/gradle-wrapper.jar`, `app/` y `.github/workflows/build-apk.yml`.
4. Abajo: **Commit changes** (rama `main`).

### Opción B — con Git (si lo tienes)
```bash
cd Tulip
git init -b main
git add .
git commit -m "Tulip: primera versión"
git remote add origin https://github.com/TU_USUARIO/Tulip.git
git push -u origin main
```

## 3. Compilar
1. Entra a la pestaña **Actions** del repositorio. Si GitHub pide confirmar, pulsa **I understand my workflows, enable them**.
2. El flujo **«Compilar APK de depuración»** arranca solo al subir. Si no, elígelo y pulsa **Run workflow**.
3. Tarda unos 6–12 minutos la primera vez. Verás un ✅ verde si salió bien o ❌ rojo si falló.

## 4. Descargar el APK
1. Abre la ejecución terminada (clic en su nombre).
2. Abajo, en **Artifacts**, descarga **`Tulip-debug-apk`** (es un .zip; necesitas haber iniciado sesión).
3. Descomprímelo: dentro está `app-debug.apk`.
4. (Opcional) El artifact **`informe-pruebas`** trae el resultado de las pruebas unitarias.

## 5. Instalar en el teléfono
1. Pasa `app-debug.apk` al teléfono (cable, Drive, correo…).
2. Ábrelo desde el administrador de archivos. Android pedirá **permitir instalar apps de esta fuente**: actívalo para esa app.
3. Instala y abre **Tulip**; concede el permiso de fotos y vídeos.

## Si sale ❌
Abre la ejecución fallida → paso **Compilar APK de depuración** → copia las líneas que empiezan por `e:` (errores de Kotlin).
Esas líneas indican archivo y línea exactos; con ellas se corrige el código. Es esperable que el primer intento tenga errores
porque el código no pudo compilarse donde fue escrito (ver `LIMITACIONES.md`).
