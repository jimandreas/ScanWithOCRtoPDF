package com.jimandreas.scanner

import com.jimandreas.state.ScanSettings
import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.OleAuto
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * WIA 2.0 scanner repository using JNA COM vtable bindings.
 *
 * All COM calls are made on a dedicated single-thread executor that called
 * CoInitializeEx(COINIT_APARTMENTTHREADED) — required for WIA STA.
 */
class WiaScannerRepository : ScannerRepository {

    // Single-threaded COM STA executor
    private val staExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "WIA-STA").also { it.isDaemon = true }
    }

    init {
        staExecutor.submit {
            // COINIT_APARTMENTTHREADED = 0x2
            val hr = Ole32.INSTANCE.CoInitializeEx(null, 0x2)
            // S_OK (0) or S_FALSE (1) means success
            if (hr.toInt() != 0 && hr.toInt() != 1) {
                throw RuntimeException("CoInitializeEx failed: ${hr.toHex()}")
            }
        }.get()
    }

    override suspend fun enumerateDevices(): List<ScannerDevice> =
        runOnSta { enumerateDevicesSync() }

    override suspend fun acquireImages(device: ScannerDevice, settings: ScanSettings): List<ScannedPage> =
        runOnSta { acquireImagesSync(device, settings) }

    private suspend fun <T> runOnSta(block: () -> T): T =
        suspendCancellableCoroutine { cont ->
            val future = staExecutor.submit(Callable {
                try {
                    cont.resume(block())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            })
            cont.invokeOnCancellation { future.cancel(true) }
        }

    private fun enumerateDevicesSync(): List<ScannerDevice> {
        val pDevMgr = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(
            WiaConstants.CLSID_WiaDevMgr2,
            null,
            WiaConstants.CLSCTX_LOCAL_SERVER,
            WiaConstants.IID_IWiaDevMgr2,
            pDevMgr
        )
        if (hr.toInt() != 0) {
            if (hr.toInt() == WiaConstants.WIA_S_NO_DEVICE_AVAILABLE) return emptyList()
            throw ScannerException.AcquisitionFailed(
                "CoCreateInstance IWiaDevMgr2 failed: ${hr.toHex()}"
            )
        }

        val devMgr = WiaDevMgr2(pDevMgr.value)
        try {
            val pEnum = PointerByReference()
            val hrEnum = devMgr.enumDeviceInfo(0x3 /* WIA_DEVINFO_ENUM_ALL */, pEnum)
            if (hrEnum.toInt() != 0) return emptyList()

            val enumDevInfo = WiaEnumDevInfo(pEnum.value)
            try {
                return buildList {
                    val pItem = PointerByReference()
                    val fetched = IntByReference(0)
                    while (true) {
                        val hrNext = enumDevInfo.next(1, pItem, fetched)
                        if (hrNext.toInt() != 0 || fetched.value == 0) break
                        val devInfo = WiaPropertyStorage(pItem.value)
                        try {
                            val id = devInfo.readStringProperty(WiaConstants.WIA_DIP_DEV_ID) ?: continue
                            val name = devInfo.readStringProperty(WiaConstants.WIA_DIP_DEV_NAME) ?: id
                            add(ScannerDevice(id = id, displayName = name))
                        } finally {
                            devInfo.release()
                        }
                    }
                }
            } finally {
                enumDevInfo.release()
            }
        } finally {
            devMgr.release()
        }
    }

    private fun acquireImagesSync(device: ScannerDevice, settings: ScanSettings): List<ScannedPage> {
        val pDevMgr = PointerByReference()
        val hrCreate = Ole32.INSTANCE.CoCreateInstance(
            WiaConstants.CLSID_WiaDevMgr2,
            null,
            WiaConstants.CLSCTX_LOCAL_SERVER,
            WiaConstants.IID_IWiaDevMgr2,
            pDevMgr
        )
        if (hrCreate.toInt() != 0) {
            throw hresultToScannerException(hrCreate.toInt(), "CoCreateInstance for acquisition")
        }
        val devMgr = WiaDevMgr2(pDevMgr.value)
        try {
            return acquireViaGetImageDlg(devMgr, device.id, settings)
        } finally {
            devMgr.release()
        }
    }

    /**
     * Uses IWiaDevMgr2::GetImageDlg() (vtable index 10) to show the WIA 2.0 scan dialog.
     *
     * The WIA 2.0 signature differs completely from WIA 1.0:
     *   GetImageDlg(lFlags, bstrDeviceID, hwndParent, bstrFolderName, bstrFilename,
     *               *plNumFiles, **ppbstrFilePaths, **ppItem)
     *
     * WIA saves the scanned image(s) to its own folder and returns the full file paths via
     * ppbstrFilePaths[0..plNumFiles-1].  We read each path back as a BufferedImage.
     *
     * Memory ownership: caller must SysFreeString each path BSTR, CoTaskMemFree the array,
     * and Release the ppItem pointer.
     */
    private fun acquireViaGetImageDlg(devMgr: WiaDevMgr2, deviceId: String, settings: ScanSettings): List<ScannedPage> {
        val hwnd = User32.INSTANCE.GetDesktopWindow()
        val bstrDeviceID = OleAuto.INSTANCE.SysAllocString(deviceId)
        // IWiaDevMgr2::GetImageDlg returns E_POINTER if bstrFolderName or bstrFilename
        // is null, even though the MSDN docs list them as optional.  Provide non-null
        // BSTRs: direct output into the system temp folder with a fixed base filename.
        val outputFolder = System.getProperty("java.io.tmpdir").trimEnd('\\', '/')
        val bstrFolderName = OleAuto.INSTANCE.SysAllocString(outputFolder)
        val bstrFilename   = OleAuto.INSTANCE.SysAllocString("wia_scan")
        val plNumFiles = IntByReference(0)
        val ppbstrFilePaths = PointerByReference()
        val ppItem = PointerByReference()

        try {
            val hr = devMgr.getImageDlg(
                0,             // lFlags: 0 = show full dialog
                bstrDeviceID,  // pre-select the scanner the user chose in the app UI
                hwnd,          // valid parent HWND
                bstrFolderName,
                bstrFilename,
                plNumFiles,
                ppbstrFilePaths,
                ppItem
            )

            // S_FALSE (1) = user cancelled the dialog
            if (hr.toInt() == 1) return emptyList()
            if (hr.toInt() != 0) throw hresultToScannerException(hr.toInt(), "GetImageDlg")

            return collectScannedPages(plNumFiles.value, ppbstrFilePaths, 300)
        } finally {
            OleAuto.INSTANCE.SysFreeString(bstrDeviceID)
            OleAuto.INSTANCE.SysFreeString(bstrFolderName)
            OleAuto.INSTANCE.SysFreeString(bstrFilename)
            ppItem.value?.let { releaseComPointer(it) }
        }
    }

    /**
     * Reads the scanned image files returned by WIA (GetImageDlg / DeviceDlg) into
     * a list of ScannedPages.  Frees the BSTR array elements and the CoTask array.
     */
    private fun collectScannedPages(
        numFiles: Int,
        ppbstrFilePaths: PointerByReference,
        fallbackDpi: Int
    ): List<ScannedPage> {
        if (numFiles <= 0) return emptyList()
        val pages = mutableListOf<ScannedPage>()
        val filePathsArray = ppbstrFilePaths.value
        if (filePathsArray != null) {
            for (i in 0 until numFiles) {
                val bstrPtr = filePathsArray.getPointer(i.toLong() * Native.POINTER_SIZE)
                if (bstrPtr != null) {
                    try {
                        val path = bstrPtr.getWideString(0)
                        val file = java.io.File(path)
                        val image = ImageIO.read(file)
                        if (image != null) {
                            val dpi = readDpiFromFile(file, fallbackDpi)
                            pages.add(ScannedPage(image, dpi))
                        }
                    } finally {
                        OleAuto.INSTANCE.SysFreeString(WTypes.BSTR(bstrPtr))
                    }
                }
            }
            Ole32.INSTANCE.CoTaskMemFree(filePathsArray)
        }
        return pages
    }

    /**
     * Reads the horizontal DPI from the image file's embedded metadata using the
     * standard javax_imageio_1.0 metadata tree (works for BMP, JPEG, PNG, TIFF).
     * HorizontalPixelSize is in mm-per-pixel; DPI = 25.4 / mm.
     * Returns [fallback] if the metadata is missing or the value is implausible.
     */
    private fun readDpiFromFile(file: java.io.File, fallback: Int): Int {
        return try {
            ImageIO.createImageInputStream(file).use { iis ->
                val readers = ImageIO.getImageReaders(iis)
                if (!readers.hasNext()) return fallback
                val reader = readers.next()
                try {
                    reader.input = iis
                    val meta = reader.getImageMetadata(0)
                    val root = meta.getAsTree("javax_imageio_1.0") as? org.w3c.dom.Element
                        ?: return fallback
                    val nodes = root.getElementsByTagName("HorizontalPixelSize")
                    if (nodes.length > 0) {
                        val mm = (nodes.item(0) as? org.w3c.dom.Element)
                            ?.getAttribute("value")?.toDoubleOrNull()
                        if (mm != null && mm > 0.0) {
                            return (25.4 / mm).roundToInt().coerceIn(72, 1200)
                        }
                    }
                    fallback
                } finally {
                    reader.dispose()
                }
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun releaseComPointer(pointer: Pointer) {
        try {
            val vtable = pointer.getPointer(0)
            val releasePtr = vtable.getPointer(2L * Native.POINTER_SIZE)
            Function.getFunction(releasePtr).invokeInt(arrayOf(pointer))
        } catch (_: Exception) {}
    }

    fun close() {
        staExecutor.submit {
            Ole32.INSTANCE.CoUninitialize()
        }.get()
        staExecutor.shutdown()
    }
}
