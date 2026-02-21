# ScanWithOCRtoPDF

A Windows 11 desktop application that acquires pages from a WIA-compatible scanner, optionally runs Tesseract OCR in the background, and produces a **PDF/A-1b** archival file with embedded images, an invisible searchable text layer, and custom XMP metadata.

## Features

- **Scanner enumeration** — detects all WIA 2.0 devices (USB, network, WSD) via COM
- **Configurable acquisition** — duplex / one-sided, Black & White / Grayscale / Full Color, 75–1200 DPI, Letter / A4 / Legal paper sizes
- **JPEG quality slider** — trades file size against image sharpness (30 %–95 %)
- **Background OCR** — Tesseract LSTM engine runs on `Dispatchers.Default`; 14 languages selectable in the UI
- **PDF/A-1b output** — sRGB OutputIntent, embedded images, invisible text layer (rendering mode `NEITHER`), XMP + Dublin Core metadata
- **Batch scanning** — "Scan More Pages / Build PDF" dialog for multi-page flatbed or ADF workflows
- **Cooperative cancellation** — Cancel button stops OCR mid-page; temp files are cleaned up
- **Packagable** — `./gradlew packageMsi` produces a self-contained MSI (bundled JRE 17, no JDK required on target machine)

## Screenshots

![Configure Presets dialog](docs/SystemRequirementsScreenshot.png)

## Technology Stack

| Concern | Library | Version |
|---|---|---|
| UI | Compose for Desktop | 1.8.2 |
| PDF | Apache PDFBox + xmpbox | 3.0.6 |
| OCR | Tess4J (Tesseract JNA wrapper) | 5.18.0 |
| Scanner | WIA 2.0 via JNA | 5.18.1 |
| Concurrency | Kotlinx Coroutines | 1.10.2 |
| Language | Kotlin JVM | 2.2.21 |
| Build | Gradle | 8.12 |

## Requirements

- **OS:** Windows 11 (64-bit) — WIA 2.0 is a Windows-only COM API
- **JDK:** 17+ (for development; the packaged MSI bundles its own JRE)
- **Scanner driver:** any WIA-compatible scanner (Brother, HP, Canon, etc.)
- **Tesseract data:** `eng.traineddata` in `appResources/windows-x64/tessdata/` (included in repo)

## Getting Started

### Run from source

```
./gradlew run
```

The first launch downloads ~300 MB of dependencies and unpacks Tess4J native DLLs (~2 s one-time cost).

### Build a distributable MSI

```
./gradlew packageMsi
```

The installer is written to `build/compose/binaries/main/msi/`. Install it on any Windows 11 machine — no JDK required.

### Build an EXE launcher

```
./gradlew packageExe
```

## Project Structure

```
src/main/kotlin/com/jimandreas/
├── Main.kt                        # singleWindowApplication() entry point
├── ui/
│   ├── App.kt                     # Root composable, hosts all dialogs
│   ├── PresetConfigDialog.kt      # Main "Configure Presets" panel
│   ├── ScanProgressDialog.kt      # Progress + Cancel overlay
│   └── components/
│       ├── ScannerDropdown.kt
│       ├── SettingsDropdowns.kt   # Sides / ColorMode / Resolution / PaperSize
│       ├── QualitySlider.kt
│       └── OcrCheckbox.kt
├── state/
│   ├── AppState.kt                # Compose state + coroutine orchestration
│   ├── ScanSettings.kt
│   ├── OcrSettings.kt
│   └── PdfMetadata.kt
├── scanner/
│   ├── WiaScannerRepository.kt    # COM STA thread + device enumeration
│   ├── WiaComBase.kt              # JNA vtable dispatch helper
│   ├── WiaDevMgr2.kt              # IWiaDevMgr2 binding
│   ├── WiaEnumDevInfo.kt          # IEnumWIA_DEV_INFO binding
│   ├── WiaPropertyStorage.kt      # IWiaPropertyStorage binding
│   ├── WiaConstants.kt            # CLSIDs, IIDs, property ID constants
│   ├── ScannerDevice.kt
│   ├── ScannerRepository.kt       # Interface
│   └── ScannerException.kt        # Sealed: DeviceNotFound, PaperJam, …
├── ocr/
│   ├── OcrEngine.kt               # Interface
│   └── TesseractOcrEngine.kt      # Tess4J implementation
├── pdf/
│   ├── PdfBuilder.kt              # PDF/A-1b assembly
│   └── PdfMetadataWriter.kt       # XMP + PDDocumentInformation
└── util/
    ├── ImageProcessor.kt          # JPEG compression, color-mode conversion
    └── CoroutineDispatchers.kt    # Injectable dispatchers for testing

src/main/resources/
└── srgb.icc                       # sRGB ICC profile for PDF/A-1b OutputIntent

appResources/windows-x64/
└── tessdata/
    ├── eng.traineddata            # Tesseract LSTM English model
    └── osd.traineddata
```

## Architecture Notes

### COM STA thread isolation
All WIA calls are marshalled onto a single dedicated thread that called `CoInitializeEx(COINIT_APARTMENTTHREADED)`. Calling WIA from `Dispatchers.IO` (a thread pool) would create an MTA context that conflicts with the STA apartment required by WIA drivers.

### Coroutine pipeline
```
startScan()
  └─ Dispatchers.IO    → WiaScannerRepository.acquireImages()
  └─ Dispatchers.Default → ImageProcessor.toJpegBytes()  (per page)
  └─ Dispatchers.Default → TesseractOcrEngine.recognizePage()  (if OCR enabled)
  └─ Dispatchers.Default → PdfBuilder.build()
  └─ Dispatchers.Main   → update UI state / show result dialog
```

### PDF/A-1b invisible text
OCR text is written using `RenderingMode.NEITHER` (PDF operator `3 Tr`). The text occupies no visible pixels but is fully indexed by PDF viewers for `Ctrl+F` search. A NotoSans TTF is embedded as `PDType0Font` — a requirement for reliable font embedding under PDF/A-1b.

### Cancellation
`scanJob.cancel()` propagates `CancellationException` through every `withContext` boundary. The catch block in `AppState.performScanCycle` re-throws `CancellationException` and cleans up any accumulated page buffers.

## Adding OCR Languages

1. Download the `.traineddata` file for the desired language from the [tessdata repository](https://github.com/tesseract-ocr/tessdata).
2. Place it in `appResources/windows-x64/tessdata/`.
3. Add the language code + display name pair to `OcrSettings.SUPPORTED_LANGUAGES` in `OcrSettings.kt`.

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.

All bundled third-party libraries (PDFBox, Tess4J, JNA, Kotlinx Coroutines, Compose for Desktop) are also Apache 2.0 licensed.
