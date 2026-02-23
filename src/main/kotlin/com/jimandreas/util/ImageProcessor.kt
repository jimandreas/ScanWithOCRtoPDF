package com.jimandreas.util

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Handles image compression before PDF embedding.
 */
object ImageProcessor {

    /**
     * Converts a [BufferedImage] to JPEG bytes at the given quality (0.0–1.0).
     */
    fun toJpegBytes(image: BufferedImage, quality: Float): ByteArray {
        val converted = ensureRgb(image)
        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val params = writer.defaultWriteParam.also { p ->
            p.compressionMode = ImageWriteParam.MODE_EXPLICIT
            p.compressionQuality = quality.coerceIn(0.1f, 1.0f)
        }
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(converted, null, null), params)
        }
        writer.dispose()
        return out.toByteArray()
    }

    private fun ensureRgb(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_RGB || src.type == BufferedImage.TYPE_3BYTE_BGR) return src
        val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return dst
    }
}
