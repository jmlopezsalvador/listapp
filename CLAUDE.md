# ListApp (Android + iOS)

App de **magia** para móvil en Kotlin Multiplatform + Compose Multiplatform (un único código para Android y iOS).
Nombre `ListApp`, paquete `com.listapp`, repo: https://github.com/jmlopezsalvador/listapp.git
El usuario habla español: responde y comenta el código en español.

## Mapa de carpetas
| Carpeta | Para qué sirve |
|---|---|
| `shared/` | Módulo KMP con TODO el código: lógica pura, interfaz Compose, ajustes. `commonMain` (común), `androidMain` / `iosMain` (solo lo del sistema, `expect`/`actual`), `commonTest` (tests). |
| `app/` | Solo la envoltura Android (`MainActivity`, manifiesto, recursos, icono). |
| `iosApp/` | Envoltura iOS en SwiftUI + `project.yml` (XcodeGen genera el `.xcodeproj` en CI; no se versiona). |
| `.github/workflows/ios.yml` | Compila en un Mac de GitHub y publica un `.ipa` sin firmar. Solo `workflow_dispatch`. |
| `docs/` | `ARQUITECTURA.md` (base técnica, fuente de verdad de versiones y reglas) y `SPEC.md` (especificación funcional, se crea al inicializar). |
| `data/` | Material de referencia que aporta el usuario (capturas, icono, recursos). Se puede leer. |
| `data/android_build/` | APK descargados/generados. No se versionan. |
| `data/ios_build/` | `.ipa` descargado de Actions. No se versiona. |
| `ignore/` | **Ignorar por completo** (ver reglas). |

## Reglas para Claude
- **Ignorar por completo la carpeta `ignore/`**: no leerla, no listarla, no buscar dentro, no usarla como referencia. Excluirla
  también de búsquedas (Glob/Grep) y del control de versiones. Es el cajón del usuario para material suelto.
- La base técnica es **`docs/ARQUITECTURA.md`** (versiones, estructura, Gradle, CI de iOS, reglas de código). Si algo choca con él, consultar al usuario.
- **Antes de lanzar el workflow de iOS en GitHub Actions, preguntar siempre al usuario** (cuesta cuota de macOS; se acumulan mejoras).
  Por defecto se lanza **sin tests** (el usuario prueba en su iPhone); solo con tests si él lo pide.
- No inventar funcionalidad: la fuente de verdad es `docs/SPEC.md` y lo que pida el usuario. Ante la duda, preguntar.
- **No hacer commit ni push sin que el usuario lo pida.** No commitear si la compilación falla.
- Datos solo en local (SharedPreferences / UserDefaults y ficheros internos). Sin red ni cuentas salvo que el SPEC lo diga.
- Mantener este `CLAUDE.md` al día: al cambiar de forma relevante la app, el flujo o las decisiones, actualizarlo en la misma tarea.

## Qué hace la app
Herramienta «oculta» de magia (detalle completo en `docs/SPEC.md`). Parece un escritorio de móvil con 3 páginas (capturas reales)
y una app de listas real. Al deslizar de la página 1→2 y 2→3, el punto donde se pone el dedo da dos dígitos (cajas invisibles
configurables); al pulsar el icono de la página 3 se abre la app de listas y, en ese momento, se escribe cada palabra
configurada (pares lista → palabra) en la posición del número. Reset (esquina sup. izquierda, o cerrar la app) deshace todo.
Config oculta (esquina sup. derecha): pares, capturas, editor visual de cajas, máscaras (con hora real) e imagen limpia.

Código: `shared/src/commonMain/kotlin/com/listapp/`
- `domain/`: lógica pura y tests (`Models.kt`, `Force.kt`; tests en `commonTest`).
- `ui/AppState.kt`: estado global y persistencia (JSON con kotlinx-serialization en SharedPreferences/UserDefaults; imágenes en ficheros).
- `ui/HomeScreen.kt`: escritorio + capa de máscaras. `ui/ListScreens.kt`: app de listas. `ui/ConfigScreen.kt` y `ui/BoxEditor.kt`: configuración.
- `ui/Platform.kt` (`expect`): barras del sistema, botón atrás, selector de galería, decodificar imagen, almacenes. Android en `androidMain`, iOS en `iosMain`.
Modos del escritorio (`ScreenMode`): pantalla completa (captura entera por página) o **iconos deslizantes** `LAYERS_BW` (fondo fijo + capa de iconos con transparencia, calculada
con `domain/Matting.kt` a partir de dos capturas por página, con fondo negro y con fondo blanco). En iconos deslizantes las máscaras y las zonas fijas
(`fixedZones`: barra superior, dock) no se deslizan. Los botones ocultos de reset/config son cajas (`resetBox`, `configBox`) con tiempos de pulsación propios. Las capas procesadas se cachean en ficheros `layer_*.raw`; `imageFromPixels` (expect/actual) las convierte en bitmap.
La **guía de introducción** (`ui/GuideDialog.kt`, 6 pasos con ilustraciones hechas con Compose) se abre con `state.showGuide` desde la bienvenida y desde las Instrucciones; si cambia el flujo de uso o la configuración mínima, hay que actualizar sus textos.
**Icono y nombre de la app** (Config → «Icono y nombre de la app»; `AppIconChanger` con `expect/actual`): todo lo derivado lo genera `tools/generar_iconos.py`
(Pillow; no editar a mano lo marcado «GENERADO»). Android: un `<activity-alias>` por icono × nombre en el manifiesto (entre las marcas `GENERADO`), solo
el de por defecto está `enabled`; `MainActivity` ya no lleva el filtro de lanzador. iOS: iconos alternativos (`<id>.appiconset` + `ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES`
en `project.yml`); el nombre no se puede cambiar. **Añadir un icono**: PNG en `data/icons/png/<id>.png` + entrada (id, label) en `data/icons/fuentes.json`
(o en `EXTRA_ICONS` del script) y, si va a Android/iOS, su id en las listas de arriba del script (`ANDROID_ICONS`, `IOS_ALTERNATES`); ejecutar `python tools/generar_iconos.py`.
**Añadir un nombre**: una línea en `NAMES` y volver a ejecutarlo. Por defecto: icono Apple Notas, nombre «ListApp» (si cambia el icono por defecto, hay que cambiar a mano el `android:icon` de `<application>` en el manifiesto). Al cambiar icono o nombre se pide confirmación y la app se reinicia (Android). Los iconos opacos a cuadrado completo se ponen en modo `inset` (290 px sobre el color del borde);
`bleed` (a sangre) los recorta. Para probar: `adb install -r` conserva el estado de los alias, así que tras probar hay que dejar solo el de por defecto
(`pm disable` de los demás y `pm enable com.listapp/.Icon_apple_notes_listapp`). Los iconos de la caja de la pantalla 3 (modo «Icono») están incrustados en base64 en `ui/FakeIconsData.kt` (**GENERADO** por `tools/generar_iconos.py` a partir de `data/icons/`; para añadir uno: PNG en `data/icons/png/`, entrada en `data/icons/fuentes.json` y volver a ejecutar el script). `ui/FakeIcons.kt` los decodifica.
El aspecto de la app de listas es personalizable (`ListStyle` en la config; `ui/ListLook.kt` lo resuelve y `ListStyleScreen.kt` es el personalizador con vista previa): se aplica con `ListLookTheme` solo a las pantallas de listas.
En la app de listas, tanto los elementos como las propias listas se reordenan manteniendo pulsado 1 s y arrastrando; «Nueva lista» (`NewListDialog`) permite importar elementos pegando texto plano (una línea = un elemento). Vibración: doble al activar reset/config y simple en cada cambio de página que fija un dígito.
Las cajas se guardan en **fracciones (0..1)** de la pantalla completa; Home y editor ocultan las barras del sistema para que coincidan con las capturas.
Estado: Android probado en emulador; **iOS sin compilar todavía** (selector de fotos con `UIImagePickerController` y almacén de ficheros escritos sin probar).

## Reglas de código para que compile en iOS
- En `commonMain`: nada de `java.*` ni `android.*`. Usar `kotlinx-datetime` (0.6.1), `kotlin.math.*` y, si hace falta aritmética
  grande, `bignum` (`com.ionspin.kotlin:bignum:0.3.10`). Sin `String.format`, `Locale`, `SimpleDateFormat`, `Thread`, `synchronized`.
- Sin recursos de Android (`R.string`) en común; textos en el código.
- Iconos dibujados con `Canvas`/`Path` de Compose (no `material-icons-extended`).
- Lógica de dominio pura y sin UI, con tests en `commonTest` (y reloj inyectable si depende del tiempo).
- Lo del sistema (sensores, vibración, galería, portapapeles…): `expect` en `commonMain/.../ui/Platform.kt` + `actual` en `androidMain`/`iosMain`.
- `Platform.ios.kt` lleva `@file:OptIn(ExperimentalForeignApi::class)` (cinterop de Kotlin/Native).
- El fallo de iOS solo se ve en Actions (~20 min): revisar con cuidado antes de lanzarlo.
- Con scripts: en esta máquina los heredocs de Bash con contenido complejo fallan; escribe los `.py` con la herramienta Write y ejecútalos.

## Compilar y probar (Windows)
- Kotlin 2.0.21, Compose Multiplatform 1.7.1, AGP 8.7.3, Gradle 8.9, JDK 17, minSdk 26, target/compileSdk 35, iOS mínimo 15.0.
- `JAVA_HOME` apunta al JDK 17: `C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot`.
- SDK de Android en `C:\Users\jmlop\AppData\Local\Android\Sdk`: crear `local.properties` con `sdk.dir=...` (ignorado por git).
- `./gradlew.bat :shared:testDebugUnitTest assembleDebug` → APK en `app/build/outputs/apk/debug/app-debug.apk`.
  Copiar el APK a `data/android_build/` si se quiere conservar.
- Emulador AVD `calcu` (Android 15), reutilizable. Poner la zona horaria `Europe/Madrid` (arranca en GMT). Gboard puede salir en negro (es del emulador).
- `gh` en Windows: `C:\Program Files\GitHub CLI\gh.exe` (hace falta `gh auth login` una vez).
- En Windows los destinos de iOS se ignoran (`kotlin.native.ignoreDisabledTargets=true`); Android compila y testea igual.

## Probar en el emulador (notas)
- `adb`: `C:\Users\jmlop\AppData\Local\Android\Sdk\platform-tools\adb.exe` (no está en el PATH). Desde Bash usar `MSYS_NO_PATHCONV=1` y rutas `C:/...`.
- Captura: `adb shell screencap -p /sdcard/s.png` + `adb pull` (no redirigir `exec-out` desde PowerShell: corrompe el PNG).
- Para no pasar por el selector de galería: copiar imágenes a `files/images/{screen1,screen2,screen3,clean}.img` y un `shared_prefs/listapp.xml`
  con la clave `config` mediante `adb push` + `run-as com.listapp cp` (app en debug).
- Para iconos deslizantes se pueden sembrar `wall.img`, `bwdark{1-3}.img` y `bwlight{1-3}.img` en `files/images/` y `mode` en la config.
- Mantener pulsado: `adb shell input motionevent DOWN x y`, `sleep`, `MOVE`, `UP` (o `input swipe x y x y 1800` para los botones ocultos).
- Deslizar con `adb shell input swipe x1 y1 x2 y2 120` (rápido, para que el pager cambie de página).

## Flujo de trabajo
1. Desarrollar y probar en Android/JVM en local (emulador).
2. Commit y push cuando el usuario lo pida.
3. **Preguntar antes de lanzar Actions** y acumular mejoras: `gh workflow run ios.yml --ref main` (opción `-f tests=true` solo si se pide).
4. Descargar el artefacto a `data/ios_build/`: `gh run download <id> -n <Nombre>-ios-sin-firmar -D data/ios_build`.
5. Instalar el `.ipa` con Sideloadly/AltStore (Apple ID gratis; caduca a los 7 días; activar Modo desarrollador en el iPhone).

## Lecciones de iOS ya aprendidas
- `gradlew` debe estar en git con permiso de ejecución (`git update-index --chmod=+x gradlew`).
- En iOS, `UIDevice.proximityState` se lee por sondeo; apagar y reactivar la monitorización deja el estado pegado. Mantenerla activa.
- Barra de estado e indicador de inicio se ocultan desde SwiftUI (`ContentView.swift`); el bloqueo automático con `idleTimerDisabled`.

## Instrucciones para el usuario (si la app las necesita)
Si la app lleva una página de «Instrucciones» de cara al usuario final, mantenerla al día en la misma tarea en que se cambie la función.
