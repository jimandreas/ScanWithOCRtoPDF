package com.jimandreas.scanner

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.OleAuto
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.PointerByReference

/**
 * JNA vtable binding for IWiaPropertyStorage.
 *
 * vtable layout (inherits IUnknown):
 *   0: QueryInterface
 *   1: AddRef
 *   2: Release
 *   3: ReadMultiple
 *   4: WriteMultiple
 *
 * PROPSPEC layout on 64-bit Windows (sizeof = 16):
 *   offset  0 : ulKind  (ULONG, 4 bytes)
 *   offset  4 : padding (4 bytes — pointer-sized union forces 8-byte alignment)
 *   offset  8 : propid  (ULONG) / lpwstr (LPWSTR pointer)
 *
 * PROPVARIANT layout on 64-bit Windows (sizeof = 16):
 *   offset  0 : vt           (VARTYPE / WORD, 2 bytes)
 *   offset  2 : wReserved1-3 (6 bytes)
 *   offset  8 : value union  (pointer or scalar, 8 bytes)
 */
class WiaPropertyStorage(pointer: Pointer) : WiaComBase(pointer) {

    // Size constants for 64-bit native structs
    private val PROPSPEC_SIZE = 16L
    private val PROPSPEC_PROPID_OFFSET = 8L   // after ulKind(4) + padding(4)
    private val PROPVAR_SIZE = 16L
    private val PROPVAR_VALUE_OFFSET = 8L

    /**
     * Reads a single string property by WIA property ID.
     * Returns null on failure.
     */
    fun readStringProperty(propId: Int): String? {
        val propSpec = Memory(PROPSPEC_SIZE)
        propSpec.clear()
        propSpec.setInt(0, 1)                       // ulKind = PRSPEC_PROPID
        propSpec.setInt(PROPSPEC_PROPID_OFFSET, propId)

        val propVar = Memory(PROPVAR_SIZE)
        propVar.clear()

        val hr = vtableCall(3, pointer, 1, propSpec, propVar) as HRESULT
        if (hr.toInt() != 0) return null

        val vt = propVar.getShort(0).toInt() and 0xFFFF
        val strPtr: Pointer? = propVar.getPointer(PROPVAR_VALUE_OFFSET)
        val result = when (vt) {
            VT_BSTR, VT_LPWSTR -> strPtr?.getWideString(0)
            VT_LPSTR            -> strPtr?.getString(0)
            else                -> null
        }
        // Free the value using the correct mechanism for each VARTYPE
        try {
            when (vt) {
                VT_BSTR    -> if (strPtr != null) OleAuto.INSTANCE.SysFreeString(WTypes.BSTR(strPtr))
                VT_LPWSTR,
                VT_LPSTR   -> if (strPtr != null) Ole32.INSTANCE.CoTaskMemFree(strPtr)
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * Reads a DWORD/integer property by WIA property ID.
     */
    fun readIntProperty(propId: Int): Int? {
        val propSpec = Memory(PROPSPEC_SIZE)
        propSpec.clear()
        propSpec.setInt(0, 1)
        propSpec.setInt(PROPSPEC_PROPID_OFFSET, propId)

        val propVar = Memory(PROPVAR_SIZE)
        propVar.clear()

        val hr = vtableCall(3, pointer, 1, propSpec, propVar) as HRESULT
        if (hr.toInt() != 0) return null

        val vt = propVar.getShort(0).toInt() and 0xFFFF
        return when (vt) {
            VT_I4, VT_UI4 -> propVar.getInt(PROPVAR_VALUE_OFFSET)
            VT_I2, VT_UI2 -> propVar.getShort(PROPVAR_VALUE_OFFSET).toInt()
            else           -> null
        }
    }

    /**
     * Writes a single integer (VT_I4) property.
     */
    fun writeIntProperty(propId: Int, value: Int): HRESULT {
        val propSpec = Memory(PROPSPEC_SIZE)
        propSpec.clear()
        propSpec.setInt(0, 1)
        propSpec.setInt(PROPSPEC_PROPID_OFFSET, propId)

        val propVar = Memory(PROPVAR_SIZE)
        propVar.clear()
        propVar.setShort(0, VT_I4.toShort())
        propVar.setInt(PROPVAR_VALUE_OFFSET, value)

        return vtableCall(4, pointer, 1, propSpec, propVar, 0) as HRESULT
    }

    companion object {
        const val VT_EMPTY  = 0
        const val VT_NULL   = 1
        const val VT_I2     = 2
        const val VT_I4     = 3
        const val VT_R4     = 4
        const val VT_R8     = 5
        const val VT_BSTR   = 8
        const val VT_BOOL   = 11
        const val VT_UI2    = 18
        const val VT_UI4    = 19
        const val VT_LPSTR  = 30
        const val VT_LPWSTR = 31
    }
}
