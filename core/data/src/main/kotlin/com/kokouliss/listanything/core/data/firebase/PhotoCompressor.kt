package com.kokouliss.listanything.core.data.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

internal class CompressedPhoto(val full: ByteArray, val thumbnail: ByteArray)

/** Shrinks a picked or captured picture to something that fits in a Firestore document. */
internal class PhotoCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Blocking. Call it on an IO dispatcher. Throws IOException when the picture cannot be read.
     * [thumbnailPx] is the longer side of the small picture: a list row needs less than a chat bubble.
     */
    fun compress(uri: Uri, thumbnailPx: Int = THUMBNAIL_MAX_PX): CompressedPhoto {
        val bitmap = decode(uri, FULL_MAX_PX)
        val (thumbWidth, thumbHeight) = fitWithin(bitmap.width, bitmap.height, thumbnailPx)
        val thumbnail = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
        return CompressedPhoto(
            full = encodeUnder(bitmap, FULL_MAX_BYTES),
            thumbnail = thumbnail.toJpeg(THUMBNAIL_QUALITY),
        )
    }

    /** The middle of the picture as a small square JPEG, for a profile photo. Blocking, like [compress]. */
    fun avatar(uri: Uri): ByteArray {
        val bitmap = decode(uri, AVATAR_PX * 4)
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        return Bitmap.createScaledBitmap(square, AVATAR_PX, AVATAR_PX, true).toJpeg(FULL_QUALITY)
    }

    private fun decode(uri: Uri, maxPx: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        // ImageDecoder applies the camera's rotation, so portrait shots stay upright.
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (width, height) = fitWithin(info.size.width, info.size.height, maxPx)
            decoder.setTargetSize(width, height)
        }
    }

    private fun encodeUnder(bitmap: Bitmap, maxBytes: Int): ByteArray {
        var quality = FULL_QUALITY
        var bytes = bitmap.toJpeg(quality)
        while (bytes.size > maxBytes && quality > MIN_QUALITY) {
            quality -= QUALITY_STEP
            bytes = bitmap.toJpeg(quality)
        }
        check(bytes.size <= maxBytes) { "Photo is still ${bytes.size} bytes after compression" }
        return bytes
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    private companion object {
        const val FULL_MAX_PX = 1280
        const val THUMBNAIL_MAX_PX = 160

        // Drawn at 56dp at most; about 5 KB, so a space's member list stays small.
        const val AVATAR_PX = 128
        const val FULL_QUALITY = 80
        const val THUMBNAIL_QUALITY = 70
        const val MIN_QUALITY = 35
        const val QUALITY_STEP = 15

        // A Firestore document may hold 1 MiB. This leaves room for field names and encoding.
        const val FULL_MAX_BYTES = 700_000
    }
}

/** Scales [width] x [height] down so the longer side is at most [maxPx]. Never scales up. */
internal fun fitWithin(width: Int, height: Int, maxPx: Int): Pair<Int, Int> {
    val longest = max(width, height)
    if (longest <= maxPx) return width to height
    val scale = maxPx.toFloat() / longest
    return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
}
