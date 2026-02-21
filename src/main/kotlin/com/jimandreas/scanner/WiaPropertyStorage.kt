package com.jimandreas.scanner

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.PointerByReference

/**
 * JNA vtable binding for IWiaPropertyStorage.
 * Provides typed access to WIA property values stored in an IPropertyStorage-like object.
 *
 * vtable layout (inherits IUnknown):
 *   0: QueryInterface
 *   1: AddRef
 *   2: Release
 *   3: ReadMultiple
 *   4: WriteMultiple
 */
class WiaPropertyStorage(pointer: Pointer) : WiaComBase(pointer) {

    /**
     * Reads a single BSTR/string property by property ID.
     * Returns null on failure or if the property is not found.
     */
    fun readStringProperty(propId: Int): String? {
        val propSpec = Memory(8)
        propSpec.setInt(0, 1)       // PRSPEC_PROPID
        propSpec.setInt(4, propId)

        val propVar = Memory(16)
        propVar.clear()

        val hr = vtableCall(3, pointer, 1, propSpec, propVar) as HRESULT
        if (hr.toInt() != 0) return null

        val vt = propVar.getShort(0).toInt() and 0xFFFF
        return when (vt) {
            VT_BSTR, VT_LPWSTR -> propVar.getPointer(8)?.getWideString(0)
            VT_LPSTR -> propVar.getPointer(8)?.getString(0)
            else -> null
        }.also {
            try { Ole32.INSTANCE.CoTaskMemFree(propVar.getPointer(8)) } catch (_: Exception) {}
        }
    }

    /**
     * Reads a DWORD/integer property by property ID.
     */
    fun readIntProperty(propId: Int): Int? {
        val propSpec = Memory(8)
        propSpec.setInt(0, 1)
        propSpec.setInt(4, propId)

        val propVar = Memory(16)
        propVar.clear()

        val hr = vtableCall(3, pointer, 1, propSpec, propVar) as HRESULT
        if (hr.toInt() != 0) return null

        val vt = propVar.getShort(0).toInt() and 0xFFFF
        return when (vt) {
            VT_I4, VT_UI4 -> propVar.getInt(8)
            VT_I2, VT_UI2 -> propVar.getShort(8).toInt()
            else -> null
        }
    }

    /**
     * Writes a single integer (VT_I4) property.
     */
    fun writeIntProperty(propId: Int, value: Int): HRESULT {
        val propSpec = Memory(8)
        propSpec.setInt(0, 1)
        propSpec.setInt(4, propId)

        val propVar = Memory(16)
        propVar.clear()
        propVar.setShort(0, VT_I4.toShort())
        propVar.setInt(8, value)

        return vtableCall(4, pointer, 1, propSpec, propVar, 0) as HRESULT
    }

    companion object {
        const val VT_EMPTY = 0
        const val VT_NULL = 1
        const val VT_I2 = 2
        const val VT_I4 = 3
        const val VT_R4 = 4
        const val VT_R8 = 5
        const val VT_BSTR = 8
        const val VT_BOOL = 11
        const val VT_UI2 = 18
        const val VT_UI4 = 19
        const val VT_LPSTR = 30
        const val VT_LPWSTR = 31
    }
}
