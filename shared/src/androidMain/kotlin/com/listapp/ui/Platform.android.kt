package com.listapp.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Modo inmersivo: oculta barra de estado y de navegación (reaparecen un momento al deslizar desde el borde). */
@Composable
actual fun SystemBarsEffect(hidden: Boolean, darkIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val c = WindowCompat.getInsetsController(window, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.isAppearanceLightStatusBars = darkIcons
        c.isAppearanceLightNavigationBars = darkIcons
        if (hidden) c.hide(WindowInsetsCompat.Type.systemBars()) else c.show(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
actual fun rememberBuzzer(): (Int) -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val vibrator: android.os.Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
        val f: (Int) -> Unit = { count ->
            try {
                if (vibrator != null && vibrator.hasVibrator()) {
                    // Pausa inicial 0; pulso de 70 ms; pausa 90 ms; pulso de 70 ms.
                    val pattern = if (count >= 2) longArrayOf(0, 70, 90, 70) else longArrayOf(0, 70)
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
                }
            } catch (_: Throwable) {
            }
        }
        f
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes != null) callback(bytes)
        }
    }
    return { launcher.launch("image/*") }
}

/** Decodifica reduciendo si la imagen es enorme (lado mayor ≤ ~2600 px) para no gastar memoria. */
actual fun decodeImage(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    val longSide = maxOf(bounds.outWidth, bounds.outHeight)
    while (longSide / sample > 2600) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}

actual fun imageFromPixels(w: Int, h: Int, argb: IntArray): ImageBitmap =
    Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()

@Composable
actual fun rememberAppIconChanger(): AppIconChanger {
    val context = LocalContext.current.applicationContext
    return remember { AndroidAppIconChanger(context) }
}

/**
 * Icono y nombre = un <activity-alias> por combinación (ver el manifiesto, generado). Solo uno está activo.
 * Se activa primero el nuevo y después se desactiva el anterior, para no quedarse nunca sin icono.
 */
private class AndroidAppIconChanger(private val context: Context) : AppIconChanger {
    override val icons = AppIconList.icons
    override val names = AppIconList.names
    override val restartsApp = true
    private val pm get() = context.packageManager

    private fun component(icon: String, name: String) = ComponentName(context.packageName, "com.listapp.Icon_${icon}_$name")

    init {
        normalizeAppIcons(context)
    }

    /** Alias activo: el que está ENABLED; si ninguno, el de por defecto (DEFAULT significa «lo que diga el manifiesto»). */
    private fun active(): Pair<String, String> {
        for (i in icons) for (n in names) {
            if (pm.getComponentEnabledSetting(component(i.id, n.id)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return i.id to n.id
        }
        return AppIconList.DEFAULT_ICON to AppIconList.DEFAULT_NAME
    }

    override fun currentIcon() = active().first
    override fun currentName() = active().second

    override fun select(iconId: String, nameId: String): Boolean {
        val (curIcon, curName) = active()
        if (curIcon == iconId && curName == nameId) return true
        return try {
            val target = component(iconId, nameId)
            pm.setComponentEnabledSetting(target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            // Reinicio completo: se abre la app de nuevo (tarea nueva y limpia) por el alias nuevo ANTES de desactivar el anterior.
            // Así el sistema o el lanzador pueden cerrar la tarea vieja sin dejarnos sin app.
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            pm.setComponentEnabledSetting(component(curIcon, curName), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Deja exactamente un alias activo. Si tras actualizar la app hubiera dos a la vez (p. ej. el alias por defecto nuevo, que el manifiesto
 * activa, junto al que eligió el usuario), se conserva el elegido por el usuario y se desactivan los demás.
 */
fun normalizeAppIcons(context: Context) {
    val pm = context.packageManager
    fun component(icon: String, name: String) = ComponentName(context.packageName, "com.listapp.Icon_${icon}_$name")
    val enabled = ArrayList<Pair<String, String>>()
    for (i in AppIconList.icons) for (n in AppIconList.names) {
        val state = pm.getComponentEnabledSetting(component(i.id, n.id))
        val isDefault = i.id == AppIconList.DEFAULT_ICON && n.id == AppIconList.DEFAULT_NAME
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (isDefault && state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)) {
            enabled.add(i.id to n.id)
        }
    }
    if (enabled.size <= 1) return
    val keep = enabled.firstOrNull { !(it.first == AppIconList.DEFAULT_ICON && it.second == AppIconList.DEFAULT_NAME) } ?: enabled.first()
    for (e in enabled) {
        if (e != keep) {
            pm.setComponentEnabledSetting(component(e.first, e.second), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val p = context.getSharedPreferences("listapp", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = p.getString(key, null)
    override fun putString(key: String, value: String) = p.edit().putString(key, value).apply()
}

class FileBlobStore(context: Context) : BlobStore {
    private val dir = File(context.filesDir, "images").apply { mkdirs() }
    override fun exists(name: String) = File(dir, name).exists()
    override fun read(name: String): ByteArray? = File(dir, name).takeIf { it.exists() }?.readBytes()
    override fun write(name: String, bytes: ByteArray) = File(dir, name).writeBytes(bytes)
    override fun delete(name: String) { File(dir, name).delete() }
}
