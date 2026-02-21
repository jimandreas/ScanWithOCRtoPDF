package com.jimandreas.scanner

sealed class ScannerException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class DeviceNotFound(deviceId: String = "") :
        ScannerException(if (deviceId.isBlank()) "No scanner device found." else "Scanner device not found: $deviceId")

    class PaperJam(cause: Throwable? = null) :
        ScannerException("Paper jam detected. Please clear the paper path and try again.", cause)

    class AcquisitionFailed(message: String, cause: Throwable? = null) :
        ScannerException("Image acquisition failed: $message", cause)

    class NoMorePages :
        ScannerException("No more pages to scan.")

    class PermissionDenied :
        ScannerException("Permission denied. Ensure the application has access to the scanner.")
}

fun hresultToScannerException(hresult: Int, message: String = ""): ScannerException {
    return when (hresult) {
        WiaConstants.WIA_ERROR_PAPER_JAM -> ScannerException.PaperJam()
        WiaConstants.WIA_ERROR_PAPER_EMPTY -> ScannerException.NoMorePages()
        WiaConstants.WIA_ERROR_DEVICE_COMMUNICATION -> ScannerException.DeviceNotFound()
        else -> ScannerException.AcquisitionFailed("${hresult.toHex()} $message")
    }
}

/** Formats a signed 32-bit HRESULT as its canonical unsigned hex string, e.g. 0x80040154. */
fun Int.toHex(): String = "0x${Integer.toUnsignedString(this, 16).uppercase()}"

/** Convenience overload for JNA HRESULT (which extends Number). */
fun com.sun.jna.platform.win32.WinNT.HRESULT.toHex(): String = this.toInt().toHex()
