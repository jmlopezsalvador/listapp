package com.listapp.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
actual fun SystemBarsEffect(hidden: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val c = WindowCompat.getInsetsController(window, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) c.hide(WindowInsetsCompat.Type.systemBars()) else c.show(WindowInsetsCompat.Type.systemBars())
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

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val p = context.getSharedPreferences("listapp", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = p.getString(key, null)
    override fun putString(key: String, value: String) = p.edit().putString(key, value).apply()
}

class FileBlobStore(context: Context) : BlobStore {
    private val dir = File(context.filesDir, "images").apply { mkdirs() }
    override fun read(name: String): ByteArray? = File(dir, name).takeIf { it.exists() }?.readBytes()
    override fun write(name: String, bytes: ByteArray) = File(dir, name).writeBytes(bytes)
    override fun delete(name: String) { File(dir, name).delete() }
}
