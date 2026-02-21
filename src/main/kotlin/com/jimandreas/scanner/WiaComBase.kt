package com.jimandreas.scanner

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinNT.HRESULT

/**
 * Base class for WIA COM vtable bindings.
 * Reads function pointers from the vtable at [pointer] and invokes them via JNA.
 */
open class WiaComBase(val pointer: Pointer) {

    private val vtable: Pointer = pointer.getPointer(0)

    protected fun vtableCall(index: Int, vararg args: Any?): Any? {
        val fnPtr = vtable.getPointer((index.toLong() * Native.POINTER_SIZE))
        val fn = Function.getFunction(fnPtr)
        return fn.invoke(HRESULT::class.java, args)
    }

    fun addRef(): Int {
        val fnPtr = vtable.getPointer(1L * Native.POINTER_SIZE)
        val fn = Function.getFunction(fnPtr)
        return fn.invokeInt(arrayOf(pointer))
    }

    fun release(): Int {
        val fnPtr = vtable.getPointer(2L * Native.POINTER_SIZE)
        val fn = Function.getFunction(fnPtr)
        return fn.invokeInt(arrayOf(pointer))
    }
}
