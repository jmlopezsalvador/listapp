@file:OptIn(ExperimentalForeignApi::class)

package com.listapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.alternateIconName
import platform.UIKit.setAlternateIconName
import platform.UIKit.supportsAlternateIcons
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy

/** iOS: la barra de estado y el indicador de inicio se ocultan en ContentView.swift. */
@Composable
actual fun SystemBarsEffect(hidden: Boolean, darkIcons: Boolean) {}

@Composable
actual fun rememberBuzzer(): (Int) -> Unit {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember {
        { count: Int ->
            scope.launch {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                if (count >= 2) {
                    delay(130)
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }
            }
            Unit
        }
    }
}

/** iOS no tiene botón «atrás» del sistema: se usan las flechas de la propia app. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

/** Delegado del selector de fotos (UIKit lo guarda débil: hay que retenerlo nosotros). */
private class PickerDelegate(val onResult: (ByteArray?) -> Unit) :
    NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val data = image?.let { UIImageJPEGRepresentation(it, 0.95) }
        picker.dismissViewControllerAnimated(true, null)
        onResult(data?.toByteArray())
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
    }
}

private fun topViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

@Composable
actual fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    val delegate = remember { PickerDelegate { bytes -> if (bytes != null) callback(bytes) } }
    return {
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, true, null)
    }
}

actual fun decodeImage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

/** Los enteros ARGB, en little-endian, son bytes B,G,R,A: justo el formato BGRA_8888 de Skia. */
actual fun imageFromPixels(w: Int, h: Int, argb: IntArray): ImageBitmap {
    val bytes = ByteArray(argb.size * 4)
    for (i in argb.indices) {
        val p = argb[i]
        val o = i * 4
        bytes[o] = p.toByte()
        bytes[o + 1] = (p ushr 8).toByte()
        bytes[o + 2] = (p ushr 16).toByte()
        bytes[o + 3] = (p ushr 24).toByte()
    }
    val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    return Image.makeRaster(info, bytes, w * 4).toComposeImageBitmap()
}

private fun NSData.toByteArray(): ByteArray {
    val n = length.toInt()
    val out = ByteArray(n)
    if (n > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}

@Composable
actual fun rememberAppIconChanger(): AppIconChanger = remember { IosAppIconChanger() }

/** iOS: solo cambia el icono (iconos alternativos). El nombre no se puede cambiar y el sistema muestra siempre su aviso. */
private class IosAppIconChanger : AppIconChanger {
    override val icons = AppIconList.icons
    override val names = AppIconList.names
    override val restartsApp = false

    override fun currentIcon(): String = UIApplication.sharedApplication.alternateIconName ?: AppIconList.MAIN_ICON
    override fun currentName(): String = ""

    override fun select(iconId: String, nameId: String): Boolean {
        val app = UIApplication.sharedApplication
        if (!app.supportsAlternateIcons) return false
        app.setAlternateIconName(if (iconId == AppIconList.MAIN_ICON) null else iconId, completionHandler = null)
        return true
    }
}

class UserDefaultsStore : KeyValueStore {
    private val d = NSUserDefaults.standardUserDefaults
    override fun getString(key: String): String? = d.stringForKey(key)
    override fun putString(key: String, value: String) = d.setObject(value, key)
}

class DocumentsBlobStore : BlobStore {
    private val dir: String =
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String) ?: ""

    override fun exists(name: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath("$dir/$name")
    override fun read(name: String): ByteArray? = NSData.dataWithContentsOfFile("$dir/$name")?.toByteArray()
    override fun write(name: String, bytes: ByteArray) { bytes.toNSData().writeToFile("$dir/$name", true) }
    override fun delete(name: String) { NSFileManager.defaultManager.removeItemAtPath("$dir/$name", null) }
}
