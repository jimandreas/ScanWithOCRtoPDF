package com.jimandreas.scanner

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/**
 * JNA vtable binding for IWiaDevMgr2.
 *
 * IWiaDevMgr2 vtable — verified against wia_lh.h (Windows SDK 10.0.26100.0):
 *   0: QueryInterface
 *   1: AddRef
 *   2: Release
 *   3: EnumDeviceInfo
 *   4: CreateDevice
 *   5: SelectDeviceDlg
 *   6: SelectDeviceDlgID
 *   7: RegisterEventCallbackInterface
 *   8: RegisterEventCallbackProgram
 *   9: RegisterEventCallbackCLSID
 *  10: GetImageDlg
 *
 * NOTE: IWiaDevMgr2::GetImageDlg is DIFFERENT from IWiaDevMgr (WIA 1.0):
 *   - Index is 10 (not 7)
 *   - Takes a device-ID BSTR and HWND directly (no IWiaItem2* root)
 *   - Returns an array of output file paths and the IWiaItem2* used
 */
class WiaDevMgr2(pointer: Pointer) : WiaComBase(pointer) {

    /**
     * EnumDeviceInfo — enumerates WIA device info objects.
     * @param lFlag WIA_DEVINFO_ENUM_LOCAL (0x1) or WIA_DEVINFO_ENUM_ALL (0x3)
     * @param ppIEnum receives IEnumWIA_DEV_INFO*
     */
    fun enumDeviceInfo(lFlag: Int, ppIEnum: PointerByReference): HRESULT {
        return vtableCall(3, pointer, lFlag, ppIEnum) as HRESULT
    }

    /**
     * CreateDevice — creates an IWiaItem2 for a specific device.
     * @param bstrDeviceID device ID string
     * @param ppWiaItem2Root receives IWiaItem2*
     */
    fun createDevice(reserved: Int, bstrDeviceID: String, ppWiaItem2Root: PointerByReference): HRESULT {
        val bstr = com.sun.jna.platform.win32.WTypes.BSTR(bstrDeviceID)
        return vtableCall(4, pointer, reserved, bstr, ppWiaItem2Root) as HRESULT
    }

    /**
     * GetImageDlg (vtable index 10) — shows the WIA 2.0 scan dialog.
     *
     * Signature from wia_lh.h:
     *   HRESULT GetImageDlg(
     *       LONG     lFlags,
     *       BSTR     bstrDeviceID,
     *       HWND     hwndParent,
     *       BSTR     bstrFolderName,   // optional; null = WIA default folder
     *       BSTR     bstrFilename,     // optional; null = WIA default filename
     *       LONG    *plNumFiles,       // out: number of acquired files
     *       BSTR   **ppbstrFilePaths,  // out: array of full file paths
     *       IWiaItem2 **ppItem)        // out: the WIA item used (caller Release()s)
     *
     * Returns S_OK on success, S_FALSE if the user cancelled.
     * Caller must SysFreeString each path BSTR, CoTaskMemFree the array, and Release ppItem.
     *
     * @param lFlags         0 = show full dialog
     * @param bstrDeviceID   pre-selects the scanner (null = show device list)
     * @param hwndParent     parent HWND (Any? accepts WinDef.HWND or null)
     * @param bstrFolderName optional folder shown in the Save dialog
     * @param bstrFilename   optional suggested filename
     * @param plNumFiles     receives the count of acquired image files
     * @param ppbstrFilePaths receives a CoTaskMem array of BSTR file paths
     * @param ppItem         receives the IWiaItem2* used during acquisition
     */
    fun getImageDlg(
        lFlags: Int,
        bstrDeviceID: WTypes.BSTR?,
        hwndParent: Any?,
        bstrFolderName: WTypes.BSTR?,
        bstrFilename: WTypes.BSTR?,
        plNumFiles: IntByReference,
        ppbstrFilePaths: PointerByReference,
        ppItem: PointerByReference
    ): HRESULT {
        return vtableCall(
            10, pointer,
            lFlags, bstrDeviceID, hwndParent,
            bstrFolderName, bstrFilename,
            plNumFiles, ppbstrFilePaths, ppItem
        ) as HRESULT
    }
}
