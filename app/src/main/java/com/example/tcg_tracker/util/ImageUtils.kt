package com.example.tcg_tracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.tcg_tracker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private object BitmapCache {
    // 1/8 de la memoria disponible, medido en KB
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

/** Descarga una imagen (con caché en memoria). Devuelve null si falla. */
suspend fun downloadBitmap(imageUrl: String, timeoutMs: Int = 5000): Bitmap? {
    BitmapCache.get(imageUrl)?.let { return it }
    return withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doInput = true
            }
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
                ?.also { BitmapCache.put(imageUrl, it) }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}

/** Decodifica una imagen reduciéndola a ~[maxSide] px y corrigiendo la rotación EXIF. */
fun decodeUprightBitmap(context: Context, uri: Uri, maxSide: Int = 1280): Bitmap? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2

        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    } catch (e: Exception) {
        null
    }
}

private fun decodeFileSampled(path: String, reqSide: Int = 1024): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= reqSide && bounds.outHeight / (sample * 2) >= reqSide) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

fun ImageView.loadCardImage(imageUri: String?) {
    if (!imageUri.isNullOrEmpty()) {
        if (imageUri.startsWith("http://", ignoreCase = true) || imageUri.startsWith("https://", ignoreCase = true)) {
            setTag(imageUri)

            BitmapCache.get(imageUri)?.let {
                setImageBitmap(it)
                return
            }

            setImageResource(R.drawable.carta_vacia)
            val imageView = this
            val scope = imageView.findViewTreeLifecycleOwner()?.lifecycleScope ?: CoroutineScope(Dispatchers.Main)

            scope.launch {
                val bitmap = downloadBitmap(imageUri)
                if (imageView.tag == imageUri) {
                    if (bitmap != null) imageView.setImageBitmap(bitmap)
                    else imageView.setImageResource(R.drawable.carta_vacia)
                }
            }
            return
        }

        val file = File(imageUri)
        if (file.exists()) {
            val bitmap = decodeFileSampled(file.absolutePath)
            if (bitmap != null) {
                setImageBitmap(bitmap)
                return
            }
        } else {
            try {
                setImageURI(Uri.parse(imageUri))
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    setImageResource(R.drawable.carta_vacia)
}
