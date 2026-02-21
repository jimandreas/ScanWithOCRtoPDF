# Technical Notes: Tess4J / PDFBox OCR Pipeline

This document records the bugs encountered when wiring Tesseract OCR (via Tess4J) into the scan-to-PDF pipeline. Like the WIA notes, every issue here was invisible at compile time and only surfaced at runtime — either as a silent no-op, a Tesseract console warning, or corrupted text in the output PDF.

---

## 1. `setDatapath()` Expects the `tessdata` Directory Itself, Not Its Parent

**Symptom:** Tesseract printed:

```
Error opening data file C:\…\prepareAppResources/eng.traineddata
Please make sure the TESSDATA_PREFIX environment variable is set to your "tessdata" directory.
Failed loading language 'eng'
Tesseract couldn't load any languages!
```

The path in the error was the Compose-prepared resources directory (`build/compose/tmp/prepareAppResources`), not the `tessdata` subdirectory inside it.

**Root cause:** The path resolution code called `tessDir.parent` and returned it as the data path:

```kotlin
val tessDir = File(resourcesDir, "tessdata")
if (tessDir.exists()) return tessDir.parent   // ← wrong: one level too high
```

Tess4J's `setDatapath(path)` expects `path` to be the directory that **directly contains** the `.traineddata` files — i.e. the `tessdata` directory itself. Passing its parent causes Tesseract to look for `eng.traineddata` in the parent, one directory above where the file actually lives. The same off-by-one error existed in all three fallback paths.

**Fix:** Return `tessDir.absolutePath` (the tessdata dir itself), not `tessDir.parent`:

```kotlin
// 1. Packaged distribution
val tessDir = File(resourcesDir, "tessdata")
if (tessDir.exists()) return tessDir.absolutePath          // ← correct

// 2. Dev mode
val devTessdata = File("appResources/windows-x64/tessdata")
if (devTessdata.exists()) return devTessdata.absolutePath  // ← was: parent dir

// 3. Local fallback
val localTess = File("tessdata")
if (localTess.exists()) return localTess.absolutePath      // ← was: File(".")
```

**Lesson:** The Tess4J `setDatapath` name is slightly misleading — it doesn't mean "the path above tessdata", it means "the tessdata path". Think of it as setting `TESSDATA_PREFIX` to the `tessdata` directory, not to its parent. The error message spells this out explicitly but is easy to misread.

---

## 2. `BufferedImage` Carries No DPI Metadata — Tesseract Defaults to 70 DPI

**Symptom:** After tessdata was found, Tesseract printed:

```
Warning: Invalid resolution 1 dpi. Using 70 instead.
```

OCR ran but accuracy on small text was poor.

**Root cause:** `BufferedImage` is a pure pixel buffer. It has no concept of physical resolution (DPI). When Tess4J calls the native Tesseract library with a `BufferedImage`, the library reads a DPI of 0 or 1 from the image metadata field (which is always unset for a `BufferedImage`), considers it invalid, and substitutes 70 DPI as a safe default. Tesseract's LSTM engine uses DPI to estimate character sizes and page layout, so a wrong DPI degrades recognition.

**Fix:** Call `setVariable("user_defined_dpi", dpi.toString())` on the `Tesseract` instance before `doOCR` / `getWords`. This overrides Tesseract's DPI detection with the known scan resolution:

```kotlin
val tess = Tesseract().apply {
    setDatapath(tessDataPath)
    setLanguage(language)
    setOcrEngineMode(1)   // LSTM_ONLY
    setPageSegMode(3)     // PSM_AUTO
    setVariable("user_defined_dpi", dpi.toString())
}
```

**Lesson:** `user_defined_dpi` is Tesseract's escape hatch for callers that supply images without embedded resolution metadata. Any pipeline that feeds `BufferedImage` objects directly to Tess4J must set this variable; otherwise Tesseract silently operates at 70 DPI regardless of the actual scan resolution.

---

## 3. The WIA Dialog Controls Its Own DPI — The App's Setting Is Ignored

**Symptom:** Even after passing `scanSettings.dpi` (300) as `user_defined_dpi`, OCR accuracy was inconsistent. The warning disappeared but results varied depending on the scanner settings chosen inside the WIA dialog.

**Root cause:** `IWiaDevMgr2::GetImageDlg` displays the WIA scanner dialog, which lets the user choose DPI independently. The application's `scanSettings.dpi` value is never communicated to the WIA dialog. Whatever DPI the WIA dialog uses is baked into the pixels of the saved file. If the WIA dialog was last used at 150 DPI, the saved image has 150 DPI of detail, but the app was telling Tesseract "this is 300 DPI" — causing Tesseract to expect twice as many pixels per character as are actually present.

**Fix:** After `ImageIO.read(file)`, read the actual DPI from the saved file's embedded metadata using the `javax_imageio_1.0` standard metadata tree. `HorizontalPixelSize` is stored in mm-per-pixel; convert to DPI with `25.4 / mm`:

```kotlin
private fun readDpiFromFile(file: File, fallback: Int): Int {
    return try {
        ImageIO.createImageInputStream(file).use { iis ->
            val reader = ImageIO.getImageReaders(iis).next()
            try {
                reader.input = iis
                val root = reader.getImageMetadata(0)
                    .getAsTree("javax_imageio_1.0") as? org.w3c.dom.Element
                    ?: return fallback
                val nodes = root.getElementsByTagName("HorizontalPixelSize")
                if (nodes.length > 0) {
                    val mm = (nodes.item(0) as? org.w3c.dom.Element)
                        ?.getAttribute("value")?.toDoubleOrNull()
                    if (mm != null && mm > 0.0)
                        return (25.4 / mm).roundToInt().coerceIn(72, 1200)
                }
                fallback
            } finally { reader.dispose() }
        }
    } catch (_: Exception) { fallback }
}
```

The actual DPI is carried alongside the image in a `ScannedPage(image: BufferedImage, dpi: Int)` data class so it flows through to Tesseract and to the PDF coordinate calculations without needing global state.

The `javax_imageio_1.0` standard metadata format is populated by ImageIO plugins for all common formats (BMP, JPEG, PNG, TIFF), making this approach format-agnostic. The fallback to `scanSettings.dpi` handles the rare case where the file has no DPI metadata.

**Lesson:** When using a WIA dialog for acquisition, the application cannot assume the scan DPI — the user controls it in the dialog. Always read the DPI back from the saved file's metadata rather than guessing from application settings. `javax_imageio_1.0`'s `HorizontalPixelSize` element is the portable way to do this across image formats in Java/Kotlin.

---

## 4. `doOCR()` Returns a Text Block — Highlights Drift from Actual Text

**Symptom:** Searching the PDF and finding highlighted results showed blue bands that were displaced from the visible text — sometimes shifted up or down by several lines, or covering a different column than the actual words.

**Root cause:** The first implementation used `tess.doOCR(image)` which returns the entire page's text as a single `String`. This string was then written into the PDF as a block of invisible text starting at the top-left of the page, advancing line by line at a fixed leading of 12pt. Nothing in this approach knows where on the page each word actually appeared. The invisible text and the scanned image are completely unregistered with each other — the text block is just dumped at the top and scrolls down at a uniform rate that rarely matches the real line spacing of the document.

**Fix:** Use `tess.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD)` (integer value `3`) instead of `doOCR`. This returns a `List<Word>` where each `Word` carries:
- `text`: the recognized string
- `boundingBox`: a `java.awt.Rectangle` with pixel coordinates (`x`, `y`, `width`, `height`; origin = top-left of image, y increases downward)

Each word is then placed in the PDF at its exact position by converting pixel coordinates to PDF points and flipping the Y axis:

```kotlin
val scale = 72.0f / dpi          // pixels → PDF points
val pdfX  = word.x * scale
val pdfY  = pageHeight - (word.y + word.height) * scale   // flip Y
val fontSize = word.height * scale

// Horizontal scaling stretches the word to exactly fill its bounding box width
val textWidth = font.getStringWidth(word.text) / 1000f * fontSize
val hScale = (pdfWidth / textWidth * 100f).coerceIn(10f, 500f)

cs.beginText()
cs.setFont(font, fontSize)
cs.setRenderingMode(RenderingMode.NEITHER)   // invisible, still searchable
cs.setHorizontalScaling(hScale)
cs.setTextMatrix(Matrix.getTranslateInstance(pdfX, pdfY))
cs.showText(word.text)
cs.endText()
```

**Lesson:** `doOCR()` is convenient for extracting text content but useless for building a positioned overlay. Any scan-to-searchable-PDF pipeline that wants accurate search highlighting must use the word/line iterator API (`getWords`, `getSegmentedRegions`, or `ResultIterator`) to obtain bounding boxes. The coordinate transform is straightforward — the only subtlety is that image Y grows downward while PDF Y grows upward.

---

## 5. `PDType0Font` with Identity-H Encoding Produces Unreadable Digit Text

**Symptom:** After the positioning fix, highlights appeared in the correct locations. However, copying text from the PDF produced corrupted output — all digits and punctuation were replaced by Hangul Jamo characters (e.g. `19 February 2026` became `ᆽᇅ Februaryᆸ ᆾᆼᆾᇂ`). Letters were unaffected. The offset was systematic: every affected character was displaced by exactly `+4492` (0x118C) Unicode code points.

**Root cause:** `PDType0Font.load(document, stream, ...)` creates a Composite (CIDFont) font with **Identity-H** encoding. Identity-H maps character codes in the PDF content stream directly to **glyph IDs (GIDs)** in the TrueType font. PDFBox writes the GID of each character into the stream, not its Unicode code point.

In NotoSans-Regular.ttf, the digit glyphs happen to be assigned GIDs in the range 4540–4549. When a PDF viewer extracts text and encounters character code 4540 in the stream, it must look up the corresponding Unicode value in the font's **ToUnicode CMap**. If that CMap is absent or incorrect, the viewer falls back to treating the 2-byte code directly as a Unicode code point: 4540 = `0x11BC` = ᆼ (Hangul Jamo). Code points 0x11BC–0x11C5 are consecutive Hangul Jamo characters, which is why all ten digits map neatly into that block.

Letters appeared correct only by coincidence: NotoSans assigns basic Latin uppercase and lowercase glyphs at GIDs that are numerically close to their ASCII code points, so those codes land back in the printable ASCII range when interpreted as Unicode. Digits do not share this coincidence.

The bug reproduced regardless of whether the font was embedded as a subset (`true`) or in full (`false`) because the Identity-H encoding mechanism and the GID-as-character-code issue are the same in both cases.

**Fix:** Use `PDTrueTypeFont` with `WinAnsiEncoding` instead of `PDType0Font`:

```kotlin
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont
import org.apache.pdfbox.pdmodel.font.encoding.WinAnsiEncoding

stream?.let { PDTrueTypeFont.load(document, it, WinAnsiEncoding.INSTANCE) }
```

`PDTrueTypeFont` uses **simple (8-bit) encoding**. For WinAnsiEncoding, the character code written into the PDF stream is the WinAnsi code point, which equals the ASCII code point for all printable ASCII characters (0x20–0x7E). A PDF viewer reading code 0x30 from the stream maps it through WinAnsi to `U+0030` = `'0'`. No GID involvement, no ToUnicode CMap required. Filter word text to `c.code in 0x20..0xFF` before calling `showText` to ensure only WinAnsi-encodable characters are used.

**Lesson:** For invisible-text OCR overlays on Latin-script documents, `PDType0Font` (CIDFont) is overkill and introduces a brittle dependency on a correct ToUnicode CMap. `PDTrueTypeFont` with `WinAnsiEncoding` is simpler, encodes characters as their familiar ASCII/Latin-1 values, and works correctly in all PDF viewers without any CMap. Reserve `PDType0Font` for documents that genuinely require multilingual or non-BMP Unicode characters.

---

## 6. JPEG Quality of 75% Degrades Document Scan Fidelity

**Symptom:** Scanned documents embedded in the PDF appeared noticeably soft and blocky compared to the original scan, particularly around fine text strokes and sharp edges.

**Root cause:** The default JPEG quality of `0.75f` (75%) is appropriate for photographic web images where bandwidth matters. For scanned documents, which contain high-contrast edges (black text on white paper), JPEG's DCT-based compression introduces visible blockiness at that quality level. Typical document scanning tools use 90–95%.

**Fix:** Raise the default from `0.75f` to `0.90f` in `ScanSettings`. The maximum on the UI quality slider is 0.95, so 0.90 is near the high end but leaves room to trade quality for file size if desired.

**Lesson:** JPEG quality presets that are reasonable for photos are usually too aggressive for scanned documents. Document scanning generally benefits from 85–95% JPEG quality; below 80% the compression artifacts become visible on text.

---

## Summary: Debugging Order (OCR Pipeline)

The OCR bugs were discovered in this sequence:

1. **Tessdata not found** → `Failed loading language 'eng'` from Tesseract; traced to `setDatapath` receiving the parent of `tessdata` instead of `tessdata` itself
2. **DPI = 1 warning** → `Warning: Invalid resolution 1 dpi. Using 70 instead.`; fixed by adding `setVariable("user_defined_dpi", ...)`
3. **Poor OCR accuracy despite DPI fix** → recognised that `scanSettings.dpi` was a guess; fixed by reading actual DPI from `javax_imageio_1.0` metadata of the WIA-saved file
4. **Highlight drift** → text highlights appeared in wrong positions; fixed by switching from `doOCR()` (unpositioned text block) to `getWords(RIL_WORD)` (per-word bounding boxes)
5. **Digits → Hangul Jamo in copy-paste** → systematic +4492 offset on all digits; fixed by replacing `PDType0Font` + Identity-H with `PDTrueTypeFont` + `WinAnsiEncoding`
6. **Soft PDF images** → JPEG quality 75% too low for document scanning; raised default to 90%
