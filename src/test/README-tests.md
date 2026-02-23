# Tests

## ScanPipelineTest

`src/test/kotlin/com/jimandreas/ScanPipelineTest.kt`

End-to-end integration tests for the scan → OCR → PDF pipeline. Each test creates a
synthetic bitmap with known text, runs real Tesseract OCR, builds a PDF via `PdfBuilder`,
then reads it back with PDFBox to verify text content and invisible-overlay position.
The UI and WIA scanner are bypassed entirely.

Using the real LSTM Tesseract engine (not a mock) makes these true integration tests —
they catch regressions anywhere in the full OCR → PDF round-trip.

---

### Shared setup

All tests use a shared `TesseractOcrEngine` instance and a common set of helpers:

| Helper | Purpose |
|---|---|
| `blankPage()` | 850×1100 white `BufferedImage` (Letter at 100 DPI) |
| `drawText(image, text, x, y, fontPt)` | Draws black text at a given baseline position |
| `drawTable(image)` | Draws a 2×2 ruled table with word text in each cell |
| `ocr(image)` | Calls `TesseractOcrEngine.recognizePage()` |
| `assertWord(words, expected)` | Asserts a specific word was recognized by OCR |
| `withPdf(image, words, block)` | JPEG-compresses → `PdfBuilder.build()` → opens with PDFBox → deletes temp file |
| `fullText(doc)` | `PDFTextStripper` over the whole document |
| `regionText(doc, rect)` | `PDFTextStripperByArea` for a rectangular region |

---

### Page geometry

```
image  : 850 × 1100 px  (Letter at 100 DPI)
PDF    : 612 × 792 pt   (scale = 72 / 100 = 0.72)

Quadrant regions — rendering coordinates (origin = top-left, Y increases downward):

  ┌──────────────┬──────────────┐
  │  UPPER_LEFT  │  UPPER_RIGHT │  y: [0,   396]
  │  x: [0, 306] │  x: [306,612]│
  ├──────────────┼──────────────┤
  │  LOWER_LEFT  │  LOWER_RIGHT │  y: [396, 792]
  │  x: [0, 306] │  x: [306,612]│
  └──────────────┴──────────────┘
```

`PDFTextStripperByArea` uses these rendering coordinates (`Rectangle2D.Float(x, y, width, height)`).

---

### Tests

#### `topLeftTextIsRecognizedAndPositioned`

Draws `"Hello"` at image baseline (150, 300).

| Step | Check |
|---|---|
| OCR | word list contains `"Hello"` |
| PDF full text | contains `"Hello"` |
| PDF region | `UPPER_LEFT` region contains `"Hello"` |
| Page count | exactly 1 page |

Image → PDF coordinate mapping: `pdfX = 150 × 0.72 = 108 pt`, rendering `Y = 300 × 0.72 = 216 pt` from top — both within `UPPER_LEFT`.

---

#### `topRightTextIsRecognizedAndPositioned`

Draws `"Tiger"` at image baseline (450, 120).

`pdfX = 450 × 0.72 = 324 pt` (right of centre 306 pt), rendering `Y = 86 pt` — falls in `UPPER_RIGHT`.

---

#### `bottomLeftTextIsRecognizedAndPositioned`

Draws `"Ocean"` at image baseline (50, 1000).

`pdfX = 36 pt` (left half), rendering `Y = 720 pt` (below the 396 pt mid-point) — falls in `LOWER_LEFT`.

---

#### `bottomRightTextIsRecognizedAndPositioned`

Draws `"Abyss"` at image baseline (450, 1000).

`pdfX = 324 pt` (right half), rendering `Y = 720 pt` (lower half) — falls in `LOWER_RIGHT`.

---

#### `tableTextIsRecognized`

Draws a 2×2 ruled table (2 px borders) in the middle of the page and verifies that all
four cell words appear in the PDF text layer.

```
Table origin : pixel (100, 500)
Cell size    : 200 × 80 px,  font = 50 pt (dpi / 2)
Grid lines   : horizontal at y = 500, 580, 660
               vertical   at x = 100, 300, 500

  ┌─────────────┬─────────────┐  y = 500
  │   Alpha     │   Beta      │  baseline y = 560
  ├─────────────┼─────────────┤  y = 580
  │   Gamma     │   Delta     │  baseline y = 640
  └─────────────┴─────────────┘  y = 660
   x=100        x=300        x=500
```

The test asserts all four words (`Alpha`, `Beta`, `Gamma`, `Delta`) appear anywhere in
the full PDF text extraction.

---

### Tessdata requirement

`TesseractOcrEngine` resolves tessdata in order (see `CLAUDE.md`). Gradle runs tests
from the project root, so `appResources/windows-x64/tessdata/eng.traineddata` is found
automatically — no extra configuration needed. If the file is missing the test fails at
`assertWord` with a clear message.

---

### Build changes

`build.gradle.kts` — `tasks.test` was expanded to include the same `--add-opens` JVM
flags used by `JavaExec`, required because Tess4J and PDFBox use reflection on AWT
internals at test time:

```kotlin
tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.desktop/java.awt.image=ALL-UNNAMED"
    )
}
```

---

### Running

```bash
# Run just this test class
./gradlew test --tests "com.jimandreas.ScanPipelineTest"

# Run a single test by name
./gradlew test --tests "com.jimandreas.ScanPipelineTest.tableTextIsRecognized"

# Run all tests
./gradlew test
```
