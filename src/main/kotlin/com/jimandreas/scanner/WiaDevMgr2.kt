package com.jimandreas.scanner

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.PointerByReference

/**
 * JNA vtable binding for IWiaDevMgr2.
 *
 * IWiaDevMgr2 vtable (inherits IUnknown at offsets 0,1,2):
 *   0: QueryInterface
 *   1: AddRef
 *   2: Release
 *   3: EnumDeviceInfo
 *   4: CreateDevice
 *   5: SelectDeviceDlg
 *   6: SelectDeviceDlgID
 *   7: GetImageDlg
 *   8: RegisterEventCallbackWindow
 *   9: RegisterEventCallbackInterface
 *  10: RegisterEventCallbackCLSID
 *  11: AddDeviceToList
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
}
