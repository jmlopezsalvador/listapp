@file:OptIn(ExperimentalForeignApi::class)

package com.listapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
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
actual fun SystemBarsEffect(hidden: Boolean) {}

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

private fun NSData.toByteArray(): ByteArray {
    val n = length.toInt()
    val out = ByteArray(n)
    if (n > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}

class UserDefaultsStore : KeyValueStore {
    private val d = NSUserDefaults.standardUserDefaults
    override fun getString(key: String): String? = d.stringForKey(key)
    override fun putString(key: String, value: String) = d.setObject(value, key)
}

class DocumentsBlobStore : BlobStore {
    private val dir: String =
        (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String) ?: ""

    override fun read(name: String): ByteArray? = NSData.dataWithContentsOfFile("$dir/$name")?.toByteArray()
    override fun write(name: String, bytes: ByteArray) { bytes.toNSData().writeToFile("$dir/$name", true) }
    override fun delete(name: String) { NSFileManager.defaultManager.removeItemAtPath("$dir/$name", null) }
}
