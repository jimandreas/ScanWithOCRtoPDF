package com.jimandreas.scanner

import java.awt.image.BufferedImage

/**
 * A single page returned by the scanner, carrying the image and the actual
 * scan DPI read from the file's embedded metadata.
 */
data class ScannedPage(val image: BufferedImage, val dpi: Int)
