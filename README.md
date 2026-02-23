# ScanWithOCRtoPDF

A Windows 11 desktop application that scans documents and saves them as searchable PDF files.

> **Note:** This application is primarily a **scanning backend** — its job is to acquire an image from your scanner, run OCR on it, and produce a searchable PDF. It is not a full document manager. Scanner settings such as resolution, paper size, color mode, and duplex are configured through the Windows WIA scan dialog that appears when you click Scan.

## What you can do

- **Scan to PDF in one click** — select your scanner, hit Scan, and get a PDF
- **Make PDFs searchable** — OCR runs automatically so you can search and copy text inside the PDF
- **Scan multiple pages** — after each scan you can add more pages before building the final PDF
- **Control file size** — the quality slider trades image sharpness against file size
- **12 OCR languages** — English, French, German, Spanish, Italian, Portuguese, Dutch, Polish, Russian, Japanese, Korean, and Arabic
- **Cancel any time** — a Cancel button stops the scan mid-way

## Screenshots

![Scan to PDF dialog](docs/SystemRequirementsScreenshot.png)

## Requirements

- Windows 11 (64-bit)
- Any WIA-compatible scanner (Brother, HP, Canon, Epson, etc.)

## Tip: Install Windows Fax and Scan first

Before using this app, it helps to install **Windows Fax and Scan** and set a default scanner profile:

1. Open **Settings → System → Optional Features**
2. Click **See available features** (if Windows Fax and Scan is not already listed as installed)
3. Search for **Windows Fax and Scan** and install it
4. Launch Windows Fax and Scan, do a test scan, and save the settings as your default profile

Once a default scanner profile exists, the WIA driver remembers your preferred resolution, color mode, and paper size — so this app (and any other WIA-based tool) can pick those up automatically, saving you repeated clicks every time you scan.

## Installation

Download and run the MSI installer — no Java installation required, everything is bundled.

## Getting Started (from source)

```
./gradlew run
```

To build an installable MSI:

```
./gradlew packageMsi
```

The installer is written to `build/compose/binaries/main/msi/`.

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.
