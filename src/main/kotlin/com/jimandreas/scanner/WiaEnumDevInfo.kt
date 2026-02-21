package com.jimandreas.scanner

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/**
 * JNA vtable binding for IEnumWIA_DEV_INFO.
 *
 * vtable (inherits IUnknown):
 *   0: QueryInterface
 *   1: AddRef
 *   2: Release
 *   3: Next
 *   4: Skip
 *   5: Reset
 *   6: Clone
 *   7: GetCount
 */
class WiaEnumDevInfo(pointer: Pointer) : WiaComBase(pointer) {

    /**
     * Retrieves the next [celt] device info objects.
     * Returns S_OK (0) if all requested items were returned, S_FALSE (1) if fewer.
     */
    fun next(celt: Int, rgelt: PointerByReference, pceltFetched: IntByReference): HRESULT {
        return vtableCall(3, pointer, celt, rgelt, pceltFetched) as HRESULT
    }

    fun skip(celt: Int): HRESULT {
        return vtableCall(4, pointer, celt) as HRESULT
    }

    fun reset(): HRESULT {
        return vtableCall(5, pointer) as HRESULT
    }

    fun getCount(pcelt: IntByReference): HRESULT {
        return vtableCall(7, pointer, pcelt) as HRESULT
    }
}
