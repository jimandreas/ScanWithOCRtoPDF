package com.jimandreas.util

import com.jimandreas.state.ColorMode
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Handles image compression and color-mode conversion before PDF embedding.
 */
object ImageProcessor {

    /**
     * Converts a [BufferedImage] to JPEG bytes at the given quality (0.0–1.0).
     * Also applies [colorMode] transformation.
     */
    fun toJpegBytes(image: BufferedImage, quality: Float, colorMode: ColorMode): ByteArray {
        val converted = applyColorMode(image, colorMode)
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

    private fun applyColorMode(src: BufferedImage, colorMode: ColorMode): BufferedImage {
        return when (colorMode) {
            ColorMode.FULL_COLOR -> ensureRgb(src)
            ColorMode.GRAYSCALE -> toGrayscale(src)
            ColorMode.BLACK_AND_WHITE -> toBinary(src)
        }
    }

    private fun ensureRgb(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_RGB || src.type == BufferedImage.TYPE_3BYTE_BGR) return src
        val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return dst
    }

    private fun toGrayscale(src: BufferedImage): BufferedImage {
        val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_BYTE_GRAY)
        val g = dst.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return dst
    }

    private fun toBinary(src: BufferedImage): BufferedImage {
        val gray = toGrayscale(src)
        // Use TYPE_BYTE_GRAY so JPEG can encode it; TYPE_BYTE_BINARY is not JPEG-compatible.
        val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_BYTE_GRAY)
        for (y in 0 until gray.height) {
            for (x in 0 until gray.width) {
                val lum = Color(gray.getRGB(x, y)).red
                dst.setRGB(x, y, if (lum >= 128) Color.WHITE.rgb else Color.BLACK.rgb)
            }
        }
        return dst
    }
}
