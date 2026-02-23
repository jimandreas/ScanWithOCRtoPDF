# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run from source (downloads ~300 MB of deps on first run)
./gradlew run

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.jimandreas.SomeTest"

# Run a single test method
./gradlew test --tests "com.jimandreas.ScanPipelineTest.tableTextIsRecognized"

# Build distributable MSI installer
./gradlew packageMsi

# Build EXE launcher
./gradlew packageExe

# Full build (compile + test)
./gradlew build
```

**JVM args required for `JavaExec` and `tasks.test`** (already set in `build.gradle.kts`):
```
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.desktop/java.awt.image=ALL-UNNAMED
```
These are needed by Tess4J and PDFBox for reflection on AWT internals.

## Key Dependency Versions

| Concern | Library | Version |
|---|---|---|
| UI | Compose for Desktop | 1.8.2 |
| PDF | Apache PDFBox + xmpbox | 3.0.6 |
| OCR | Tess4J (Tesseract LSTM) | 5.18.0 |
| Scanner | JNA + jna-platform | 5.18.1 |
| Coroutines | kotlinx-coroutines | 1.10.2 |
| Kotlin | kotlin("jvm") + compose plugin | 2.2.21 |
| JVM target | JRE/toolchain | 17 |

## Architecture

### High-Level Pipeline

```
startScan()
  └─ Dispatchers.IO    → WiaScannerRepository.acquireImages()   [COM STA thread]
  └─ Dispatchers.Default → ImageProcessor.toJpegBytes()          [per page]
  └─ Dispatchers.Default → TesseractOcrEngine.recognizePage()    [if OCR enabled]
  └─ Dispatchers.Default → PdfBuilder.build()
  └─ Dispatchers.Main   → update Compose state / show dialogs
```

### Package Structure

`src/main/kotlin/com/jimandreas/`

- **`Main.kt`** — `singleWindowApplication()` entry point
- **`ui/`** — Compose UI layer
  - `App.kt` — Root composable; creates `WiaScannerRepository`, `TesseractOcrEngine`, and `AppState`; hosts all dialogs
  - `PresetConfigDialog.kt` — "Configure Presets" panel (main UI)
  - `ScanProgressDialog.kt` — Modal progress + Cancel overlay
  - `components/` — Isolated UI widgets (`ScannerDropdown`, `SettingsDropdowns`, `QualitySlider`, `OcrCheckbox`)
- **`state/`** — Compose state and domain models
  - `AppState.kt` — All `mutableStateOf` fields; coroutine orchestration via `startScan()`, `finalizePdf()`, `cancelScan()`
  - `ScanSettings.kt` — Sides, ColorMode, PaperSize enums + `ScanSettings` data class
  - `OcrSettings.kt` — `SUPPORTED_LANGUAGES` map; add entries here to expose new OCR languages
  - `PdfMetadata.kt` — Author, Title, Keywords
- **`scanner/`** — WIA COM layer
  - `WiaScannerRepository.kt` — Implements `ScannerRepository`; owns the COM STA thread; calls WIA Automation Layer for image acquisition
  - `WiaComBase.kt` — JNA vtable dispatch helper (base class for all WIA COM bindings)
  - `WiaDevMgr2.kt`, `WiaEnumDevInfo.kt`, `WiaPropertyStorage.kt` — Individual COM interface JNA bindings
  - `WiaConstants.kt` — All CLSIDs, IIDs, WIA property ID constants, and HRESULT values
  - `ScannerException.kt` — Sealed class hierarchy; `hresultToScannerException()` maps WIA HRESULTs
- **`ocr/`** — OCR abstraction
  - `OcrEngine.kt` — Interface: `suspend fun recognizePage(image, language): String?`
  - `TesseractOcrEngine.kt` — Tess4J LSTM implementation; tessdata path resolved from packaged resources dir or `appResources/windows-x64/tessdata/` in dev mode
- **`pdf/`** — PDF generation
  - `PdfBuilder.kt` — PDF/A-1b assembly: sRGB OutputIntent, JPEG image layers, invisible OCR text (`RenderingMode.NEITHER`), NotoSans font embedding
  - `PdfMetadataWriter.kt` — XMP (DublinCore + PDF/A identification) + `PDDocumentInformation`
- **`util/`**
  - `ImageProcessor.kt` — JPEG compression, grayscale/B&W `BufferedImage` conversion
  - `CoroutineDispatchers.kt` — Injectable `AppDispatchers` interface; use `DefaultDispatchers` in production and inject test doubles in unit tests

### Critical WIA/COM Constraint

All WIA COM calls **must** execute on the single dedicated `WIA-STA` thread in `WiaScannerRepository` that called `CoInitializeEx(COINIT_APARTMENTTHREADED)`. Calling WIA from `Dispatchers.IO` (a thread pool) creates an MTA context that breaks WIA drivers. The `runOnSta {}` helper marshals coroutine calls onto this thread using `suspendCancellableCoroutine`.

### B&W Image Encoding

`ImageProcessor.toBinary` must produce `TYPE_BYTE_GRAY` (not `TYPE_BYTE_BINARY`). Java's JPEG writer does not support 1-bit packed pixels — passing `TYPE_BYTE_BINARY` throws `IIOException: Unsupported Image Type`. The threshold logic (pixel ≥ 128 → white, else → black) still gives a visually binary result in a grayscale buffer.

### PDF/A-1b Invisible Text

OCR text uses `RenderingMode.NEITHER` (PDF operator `3 Tr`) — no visible pixels, fully searchable. `PDType0Font` (NotoSans TTF) is required; standard Type 1 fonts are not reliably embeddable under PDF/A-1b. Font file loaded from `/fonts/NotoSans-Regular.ttf` or `/NotoSans-Regular.ttf` on the classpath.

### Tessdata Resolution (runtime)

`TesseractOcrEngine` resolves tessdata in order:
1. `compose.application.resources.dir` system property (packaged MSI)
2. `appResources/windows-x64/` relative to working directory (dev mode)
3. `tessdata/` in working directory
4. Empty string (Tess4J default extraction)

## Adding OCR Languages

1. Download `.traineddata` from [tessdata repo](https://github.com/tesseract-ocr/tessdata)
2. Place in `appResources/windows-x64/tessdata/`
3. Add language code + display name to `OcrSettings.SUPPORTED_LANGUAGES` in `OcrSettings.kt`

## Distribution

The MSI packages: JRE 17, all JARs, and the `appResources/windows-x64/` folder (including tessdata). Output path: `build/compose/binaries/main/msi/`. The packaged app sets `compose.application.resources.dir` automatically.
