# Tests

## ScanPipelineTest

`src/test/kotlin/com/jimandreas/ScanPipelineTest.kt`

An end-to-end integration test for the scan → OCR → PDF pipeline. It bypasses the
UI and WIA scanner entirely, exercising the three real production components in sequence:
`ImageProcessor` → `TesseractOcrEngine` → `PdfBuilder`.

### What the test does

1. **Creates a synthetic bitmap** — 850×1100 px (Letter at 100 DPI), white background,
   with the word `"Hello"` drawn in black at a 100pt sans-serif font. The baseline is
   placed at pixel coordinate (150, 300), in the upper-left area of the page.

2. **Compresses to JPEG** via `ImageProcessor.toJpegBytes()` at 95% quality, exactly
   as the production pipeline does before embedding images in the PDF.

3. **Runs real Tesseract OCR** via `TesseractOcrEngine.recognizePage()`. Using the
   actual LSTM engine (not a mock) makes this a true integration test — it will catch
   regressions in the full OCR round-trip. Requires `eng.traineddata` to be present at
   `appResources/windows-x64/tessdata/eng.traineddata` (already in the repo).

4. **Asserts OCR recognized `"Hello"`** in the returned word list.

5. **Builds a PDF** via `PdfBuilder.build()` with the JPEG and OCR word list.

6. **Reads the PDF back** with PDFBox and checks two things:
   - `PDFTextStripper` finds `"Hello"` anywhere in the document text.
   - `PDFTextStripperByArea` finds `"Hello"` inside the upper-left 400×350 pt region,
     confirming the invisible OCR text overlay is positioned correctly over the image.

### Coordinate rationale

The test image is 850×1100 px at 100 DPI (Letter paper). The PDF scale factor is
`72 / 100 = 0.72 pt/px`. `"Hello"` is drawn with its baseline at pixel (150, 300):

| Quantity | Calculation | Value |
|---|---|---|
| PDF x | 150 px × 0.72 | ≈ 108 pt from left |
| PDF y from bottom | 792 − (300 + ~100) × 0.72 | ≈ 720 − 288 = 432 pt... but PDFTextStripperByArea uses rendering coords (top-left origin) |
| Rendering y from top | ~300 × 0.72 | ≈ 216 pt from top |

The search region `Rectangle2D.Float(0, 0, 400, 350)` comfortably covers x [0, 400 pt]
and y [0, 350 pt] from the top-left, which contains the expected word position while
excluding the lower and right portions of the page.

### Build changes

`build.gradle.kts` — the `tasks.test` block was expanded to include the same
`--add-opens` JVM flags used by `JavaExec` tasks, required because Tess4J and PDFBox
use reflection on AWT internals at test time:

```kotlin
tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.desktop/java.awt.image=ALL-UNNAMED"
    )
}
```

### Running

```bash
# Run just this test
./gradlew test --tests "com.jimandreas.ScanPipelineTest"

# Run all tests
./gradlew test
```
