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
        else -> ScannerException.AcquisitionFailed("HRESULT=0x${hresult.toString(16).uppercase()} $message")
    }
}
