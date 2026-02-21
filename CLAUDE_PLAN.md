Here is Claude's plan:
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
Plan: ScanWithOCRtoPDF — Full Implementation

Context

The project is a newly initialized Kotlin JVM skeleton (boilerplate Main.kt only, no dependencies beyond
kotlin("test")). The goal is to implement a full-featured Windows 11 desktop scanner → searchable PDF application per
docs/SystemRequirements.md. The app must: enumerate and connect to WIA-compatible scanners (e.g. Brother), acquire
images with configurable settings (duplex, color mode, DPI, paper size), optionally run Tesseract OCR in the
background, and produce a PDF/A-1b file with embedded images, invisible searchable text, and custom XMP metadata.

 ---
Technology Decisions

Concern: UI
Choice: Compose for Desktop 1.8.2
Reason: Idiomatic Kotlin, declarative state model, native Windows distribution via jpackage
────────────────────────────────────────
Concern: PDF
Choice: Apache PDFBox 3.0.6
Reason: Apache License 2.0 (vs. iText AGPL); full PDF/A-1b support including ICC OutputIntent and xmpbox
────────────────────────────────────────
Concern: OCR
Choice: Tess4J 5.18.0
Reason: JNA wrapper for Tesseract; Apache License; bundles Windows DLLs; supports LSTM engine
────────────────────────────────────────
Concern: Scanner
Choice: WIA 2.0 via JNA 5.18.1
Reason: WIA is 64-bit COM (TWAIN is 32-bit only); Brother scanners ship WIA drivers on Win 11; jna-platform provides
COM base classes
────────────────────────────────────────
Concern: Concurrency
Choice: Kotlinx Coroutines 1.10.2
Reason: Dispatchers.Default for CPU-bound OCR/PDF; Dispatchers.IO for scanner I/O; cooperative cancellation via
ensureActive()

Critical gotcha: Gradle must be upgraded from 8.0 → 8.12 — the org.jetbrains.compose 1.8.x plugin requires Gradle ≥
8.6.

 ---
Package Structure

src/main/kotlin/com/jimandreas/
Main.kt                             -- singleWindowApplication() entry point
ui/
App.kt                            -- root @Composable, holds AppState
PresetConfigDialog.kt             -- "Configure Presets" modal
ScanProgressDialog.kt             -- progress + cancel dialog
components/
ScannerDropdown.kt
SettingsDropdowns.kt            -- Sides/ColorMode/Resolution/PaperSize
QualitySlider.kt                -- "Small Size ↔ High Quality" slider
OcrCheckbox.kt                  -- OCR checkbox + language selector
state/
AppState.kt                       -- mutableStateOf fields; suspend fun scanAndProcess()
ScanSettings.kt                   -- data class: sides, colorMode, dpi, paperSize, quality
OcrSettings.kt                    -- data class: enabled, language
PdfMetadata.kt                    -- data class: author, title, keywords
scanner/
ScannerDevice.kt                  -- data class: id, displayName, connectionType
ScannerRepository.kt              -- interface
WiaScannerRepository.kt           -- WIA COM implementation (uses dedicated STA thread)
WiaDevMgr2.kt                     -- JNA vtable binding: IWiaDevMgr2
WiaEnumDevInfo.kt                 -- JNA vtable binding: IEnumWIA_DEV_INFO
WiaPropertyStorage.kt             -- JNA vtable binding: IWiaPropertyStorage
WiaConstants.kt                   -- CLSIDs, IIDs, WIA property ID constants
ScannerException.kt               -- sealed class: DeviceNotFound, PaperJam, AcquisitionFailed
ocr/
OcrEngine.kt                      -- interface: suspend fun recognizePages(...)
TesseractOcrEngine.kt             -- Tess4J implementation with cancellation support
pdf/
PdfBuilder.kt                     -- assembles PDF/A-1b: images + OCR text + ICC + metadata
PdfMetadataWriter.kt              -- xmpbox XMP + PDDocumentInformation
util/
ImageProcessor.kt                 -- JPEG compression, grayscale/B&W conversion
CoroutineDispatchers.kt           -- injectable dispatchers for testability

src/main/resources/
srgb.icc                            -- sRGB ICC profile for PDF/A-1b OutputIntent

appResources/windows-x64/
tessdata/eng.traineddata            -- Tesseract LSTM English model
tessdata/osd.traineddata

 ---
Files to Modify / Create

1. gradle/wrapper/gradle-wrapper.properties

Change: distributionUrl=https://services.gradle.org/distributions/gradle-8.12-bin.zip

2. settings.gradle.kts

Add a pluginManagement block with gradlePluginPortal(), mavenCentral(), and google() repositories.

3. build.gradle.kts — complete replacement

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
kotlin("jvm") version "2.2.21"
id("org.jetbrains.compose") version "1.8.2"
id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
}

group = "com.jimandreas"
version = "1.0-SNAPSHOT"

repositories {
mavenCentral()
google()
}

dependencies {
implementation(compose.desktop.currentOs)
implementation(compose.material3)
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
implementation("org.apache.pdfbox:pdfbox:3.0.6")
implementation("org.apache.pdfbox:xmpbox:3.0.6")
implementation("org.apache.pdfbox:preflight:3.0.6")
implementation("javax.xml.bind:jaxb-api:2.3.1")
implementation("net.sourceforge.tess4j:tess4j:5.18.0")
implementation("net.java.dev.jna:jna:5.18.1")
implementation("net.java.dev.jna:jna-platform:5.18.1")
testImplementation(kotlin("test"))
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

kotlin { jvmToolchain(17) }
tasks.test { useJUnitPlatform() }

compose.desktop {
application {
mainClass = "com.jimandreas.MainKt"
nativeDistributions {
targetFormats(TargetFormat.Msi, TargetFormat.Exe)
packageName = "ScanWithOCRtoPDF"
packageVersion = "1.0.0"
appResourcesRootDir.set(project.layout.projectDirectory.dir("appResources"))
}
}
}

tasks.withType<JavaExec> {
jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED",
"--add-opens", "java.desktop/java.awt.image=ALL-UNNAMED")
}

4. All new source files

Created per the package structure above. Main.kt is replaced (not a new file).

 ---
Implementation Phases

Phase 1 — Build Infrastructure

1. Update Gradle wrapper to 8.12
2. Update settings.gradle.kts with pluginManagement
3. Replace build.gradle.kts with new version
4. Run ./gradlew dependencies to verify artifact resolution
5. Replace Main.kt with a minimal singleWindowApplication { } that opens a blank window
6. Add srgb.icc to src/main/resources/

Phase 2 — WIA Scanner Integration (hardest part)

1. Define WiaConstants.kt — CLSIDs, IIDs, and WIA property IDs as JNA GUID instances
2. Implement JNA COM vtable bindings: WiaDevMgr2, WiaEnumDevInfo, WiaPropertyStorage
3. Implement WiaScannerRepository:
- Dedicated single-thread executor for COM STA (CoInitializeEx + COINIT_APARTMENTTHREADED)
- enumerateDevices() → CoCreateInstance(CLSID_WiaDevMgr2) → enumerate → read WIA_DIP_DEV_NAME
- acquireImages(device, settings) → CreateDevice → set WIA properties (DPI, color, duplex, paper) →
  IWiaDataTransfer::idtGetData → ImageIO.read(ByteArray)
4. Map WIA HRESULTs to ScannerException sealed subclasses

Phase 3 — UI "Configure Presets" Modal

1. Define ScanSettings, OcrSettings, PdfMetadata data classes with defaults
2. Implement AppState with mutableStateOf fields
3. Build PresetConfigDialog composable:
- Scanner ExposedDropdownMenuBox + "Options" button
- Input card: Sides / Color Mode / Resolution / Paper Size dropdowns
- Optimization Slider (0–100, mapped to JPEG quality 0.3–0.95)
- OCR Checkbox + animated language dropdown
- Metadata fields: Author, Title, Keywords
- "Scan" and "Cancel" buttons
4. Build ScanProgressDialog showing phase and page count

Phase 4 — OCR Background Threading

1. Implement TesseractOcrEngine.recognizePages() using withContext(Dispatchers.Default) with ensureActive() per page
2. Implement AppState.scanAndProcess() coroutine orchestrating:
- Scanner I/O on Dispatchers.IO (WIA STA executor)
- Image compression on Dispatchers.Default
- OCR on Dispatchers.Default (conditional)
- PDF build on Dispatchers.Default
- Progress updates back on Dispatchers.Main
3. "Cancel" button calls scanJob.cancel(); CancellationException triggers temp-file cleanup
4. ScannerException variants shown as AlertDialog with user-readable messages

Phase 5 — PDF/A Generation

1. PdfBuilder.build():
- Add PDF/A-1b OutputIntent with bundled srgb.icc via PDICCBased
- Per page: embed JPEG via PDImageXObject.createFromByteArray(), draw to page via PDPageContentStream
- If OCR text present: append invisible text layer (rendering mode 3) using embedded Noto Sans TTF
- Delegate metadata to PdfMetadataWriter
- Save with CompressParameters.NO_COMPRESSION
2. PdfMetadataWriter.write():
- xmpbox XMPMetadata with DublinCore (title, creator, subjects) + XMPBasic + PDFA Identification (part=1,
  conformance=B)
- Also set PDDocumentInformation for backward-compatible readers

Phase 6 — Batch Scanning

1. After first page acquisition, show AlertDialog: "Scan More Pages" | "Done"
2. "Scan More Pages" re-calls acquireImages() and appends to accumulated list
3. ADF scanners return multiple pages per call naturally; flatbed uses this dialog loop

Phase 7 — Distribution Packaging

1. Download eng.traineddata + osd.traineddata into appResources/windows-x64/tessdata/
2. Run ./gradlew packageMsi or ./gradlew packageExe
3. Bundled output includes JRE 17, all JARs, and tessdata folder

 ---
Key Design Notes

- COM STA requirement: All WIA COM calls must be made on the single dedicated executor thread that called
  CoInitializeEx(COINIT_APARTMENTTHREADED). Never call WIA from Dispatchers.IO (thread pool → MTA conflict).
- Tess4J DLL extraction: Tess4J unpacks native DLLs to a temp directory on first use (~2s). Set
  System.setProperty("jna.library.path", resourcesDir) before first Tesseract instantiation. tessdata path =
  System.getProperty("compose.application.resources.dir") + "/tessdata".
- Kotlin plugin version alignment: kotlin("jvm") and org.jetbrains.kotlin.plugin.compose must both be 2.2.21.
- PDF/A invisible text font: Use PDType0Font.load(document, notoSansTtfStream, true) — standard Type 1 fonts are not
  reliably embedded in PDF/A-1b mode.

 ---
Verification

1. Build: ./gradlew build completes with no errors after Phase 1
2. Window opens: ./gradlew run shows a blank Compose window
3. Scanner enumeration: With Brother scanner connected, device appears in dropdown
4. Scan acquisition: Clicking "Scan" produces List<BufferedImage> logged to console (Phase 2 smoke test)
5. OCR: A test image run through TesseractOcrEngine returns expected text
6. PDF output: Open generated PDF in Adobe Acrobat; verify:
- Ctrl+F text search finds OCR'd words
- File → Properties → Description shows Author/Title/Keywords
- Preflight → PDF/A-1b reports no violations
7. Cancellation: Click "Cancel" mid-OCR; confirm no orphaned temp files and UI returns to ready state
8. Packaging: ./gradlew packageMsi produces an installable MSI; install on a clean Windows 11 VM and verify all
   features work without a JDK installed
   ╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌


