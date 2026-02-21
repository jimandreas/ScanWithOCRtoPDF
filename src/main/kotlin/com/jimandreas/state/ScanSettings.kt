package com.jimandreas.state

data class ScanSettings(
    val sides: Sides = Sides.ONE_SIDED,
    val colorMode: ColorMode = ColorMode.GRAYSCALE,
    val dpi: Int = 300,
    val paperSize: PaperSize = PaperSize.LETTER,
    val jpegQuality: Float = 0.90f
)

enum class Sides(val displayName: String) {
    ONE_SIDED("One Sided"),
    BOTH_SIDES("Both Sides (Duplex)")
}

enum class ColorMode(val displayName: String) {
    BLACK_AND_WHITE("Black and White"),
    GRAYSCALE("Grayscale"),
    FULL_COLOR("Full Color")
}

enum class PaperSize(val displayName: String, val widthInches: Double, val heightInches: Double) {
    LETTER("Letter (8.5\" x 11\")", 8.5, 11.0),
    A4("A4 (8.3\" x 11.7\")", 8.268, 11.693),
    LEGAL("Legal (8.5\" x 14\")", 8.5, 14.0)
}
