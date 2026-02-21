# Technical Notes: Windows 11 Scanner Integration Challenges

This document records the hard-won lessons from getting WIA 2.0 scanner enumeration working on Windows 11 via JNA COM vtable bindings. Each section describes a bug that was invisible at compile time and only revealed itself at runtime — usually as a cryptic HRESULT or a silent empty list.

---

## 1. `CLSCTX_INPROC_SERVER` vs. `CLSCTX_LOCAL_SERVER`

**Symptom:** `CoCreateInstance` for `CLSID_WiaDevMgr2` returned `0x80040154` (`REGDB_E_CLASSNOTREG`) — the registry appeared to have no entry for the WIA Device Manager.

**Root cause:** WIA Device Manager 2 (`wiaservc.dll`) runs as an **out-of-process local server** (a Windows service), not as an in-process DLL. Passing `CLSCTX_INPROC_SERVER` (`0x1`) to `CoCreateInstance` restricts COM to loading an in-process DLL, which genuinely does not exist for this CLSID. The registry entry is only visible under `HKEY_CLASSES_ROOT\CLSID\{B6C292BC-7C88-41EE-8B54-8EC92617E599}\LocalServer32`, not `InprocServer32`.

**Fix:** Change all `CoCreateInstance` calls for WIA COM objects to use `CLSCTX_LOCAL_SERVER` (`0x4`). `CLSCTX_ALL` (`0x17`) also works but is broader than necessary.

**Lesson:** When a CLSID returns `REGDB_E_CLASSNOTREG` on a system where the component is clearly installed, check *which* server type is registered before assuming the registry is corrupt. Use `OleView.exe` or inspect `HKEY_CLASSES_ROOT\CLSID\<guid>` directly to see which sub-key (`InprocServer32` vs. `LocalServer32`) is present.

---

## 2. Wrong Interface IIDs

**Symptom:** `CoCreateInstance` succeeded (returned `S_OK`), but every subsequent COM call on the returned pointer returned `0x80004002` (`E_NOINTERFACE`) or caused an access violation.

**Root cause:** The IIDs (Interface Identifiers) used in the initial implementation were copied from unofficial sources and were incorrect:

| Interface | Wrong IID used | Correct IID (from `wia_lh.h`) |
|---|---|---|
| `IWiaDevMgr2` | `814B5ACC-…` | `79C07CF1-CBDD-41EE-8EC3-F00080CADA7A` |
| `IEnumWIA_DEV_INFO` | malformed | `5E38B83C-8CF1-11D1-BF92-0060081ED811` |
| `IWiaPropertyStorage` | `98CBEC27-…` | `98B5E8A0-29CC-491A-AAC0-E6DB4FDCCEB6` |

COM uses the IID to look up the correct vtable pointer via `QueryInterface`. A wrong IID means COM returns a vtable for a different (or nonexistent) interface, so every method call dispatches to the wrong function pointer.

**Fix:** Source all IIDs directly from the Windows SDK header `wia_lh.h` (under `C:\Program Files (x86)\Windows Kits\10\Include\<version>\um\`). The canonical form is the `MIDL_INTERFACE("…")` annotation on each interface declaration.

**Lesson:** Never copy GUIDs from blog posts or Stack Overflow. Always verify against the Windows SDK header files. Even one transposed hex digit silently breaks every call on that interface.

---

## 3. `PROPSPEC` Struct Layout on 64-bit Windows

**Symptom:** `IWiaPropertyStorage::ReadMultiple` returned `S_OK` but the `PROPVARIANT` output was always `VT_EMPTY` — every property read came back null.

**Root cause:** The `PROPSPEC` structure was laid out incorrectly in JNA `Memory`. On 64-bit Windows, `PROPSPEC` is:

```c
typedef struct tagPROPSPEC {
    ULONG  ulKind;       // offset  0, size 4
    // implicit padding  // offset  4, size 4  ← missed this
    union {
        PROPID  propid;  // offset  8, size 4
        LPWSTR  lpwstr;  // offset  8, size 8 (pointer on 64-bit)
    };
} PROPSPEC;             // sizeof = 16
```

The `union` contains an `LPWSTR` (a pointer), which is 8 bytes on a 64-bit process. The C ABI requires the union — and therefore the struct — to be aligned to the size of its largest member (8 bytes). This pushes `ulKind` to consume 8 bytes (4 real + 4 padding), placing `propid` at **offset 8**, not offset 4.

The initial implementation used a compact 8-byte `Memory` buffer and wrote `propid` at offset 4, which actually overlapped the padding region. The native `ReadMultiple` call read `ulKind` correctly but got `propid = 0` (from the padding bytes), so it looked up property ID 0 — which doesn't exist — and returned `VT_EMPTY`.

**Fix:** Allocate 16 bytes for `PROPSPEC` and write `propid` at offset 8:

```kotlin
private val PROPSPEC_SIZE = 16L
private val PROPSPEC_PROPID_OFFSET = 8L

val propSpec = Memory(PROPSPEC_SIZE)
propSpec.clear()
propSpec.setInt(0, 1)                         // ulKind = PRSPEC_PROPID
propSpec.setInt(PROPSPEC_PROPID_OFFSET, propId)
```

**Lesson:** Never assume a C struct's field offsets from field order alone. On 64-bit Windows, any struct containing a pointer (or a union with a pointer branch) will have alignment padding inserted by the compiler. When writing JNA native memory manually, check the actual `sizeof` and field offsets in C/C++ or use `offsetof()`. The SDK documentation rarely calls out padding explicitly.

---

## 4. `BSTR` Memory Freed with the Wrong Allocator

**Symptom:** The application exited with `STATUS_HEAP_CORRUPTION` (exit code `-1073740940` / `0xC0000374`) immediately after successfully reading the first device name from `IWiaPropertyStorage`.

**Root cause:** When a `PROPVARIANT` contains a `VT_BSTR` value, the string is a COM `BSTR` — a length-prefixed wide string allocated by `SysAllocString` from the OLE Automation heap. The initial implementation freed it with `Ole32.CoTaskMemFree`, which operates on the COM task allocator heap.

These are two distinct heap allocators on Windows. Freeing a `BSTR` through `CoTaskMemFree` writes a free-block header into the wrong heap, which the heap integrity checker detects (typically a few allocations later) and terminates the process.

**Fix:** Free `VT_BSTR` values with `OleAuto.INSTANCE.SysFreeString()`. Free `VT_LPWSTR` and `VT_LPSTR` values — which *are* allocated by the task allocator — with `Ole32.INSTANCE.CoTaskMemFree()`:

```kotlin
when (vt) {
    VT_BSTR  -> OleAuto.INSTANCE.SysFreeString(WTypes.BSTR(strPtr))
    VT_LPWSTR,
    VT_LPSTR -> Ole32.INSTANCE.CoTaskMemFree(strPtr)
}
```

**Lesson:** COM has multiple memory allocation conventions and they are not interchangeable. The rule: `BSTR` ↔ `SysAllocString`/`SysFreeString`; task-memory strings ↔ `CoTaskMemAlloc`/`CoTaskMemFree`. Documentation for each COM method specifies which convention the callee uses for out-parameters — read it.

---

## 5. WIA Requires a COM STA Thread

**Symptom:** Scanner enumeration worked when called from the application's main thread, but crashed or returned `RPC_E_WRONG_THREAD` (`0x8001010E`) when called from a Kotlin coroutine dispatcher.

**Root cause:** WIA Device Manager 2 is registered as an **Apartment-threaded** COM server (STA — Single-Threaded Apartment). The COM runtime requires that all calls to an STA object originate from the thread that created the apartment — i.e., the thread that called `CoInitializeEx(NULL, COINIT_APARTMENTTHREADED)`. Kotlin's `Dispatchers.IO` and `Dispatchers.Default` are thread pools; any given coroutine resumption may land on a different thread, which would have its own MTA (Multi-Threaded Apartment) context. Crossing apartment boundaries without a proper COM proxy causes either `RPC_E_WRONG_THREAD` or silent data corruption.

**Fix:** Create a single-threaded `ExecutorService` (`Executors.newSingleThreadExecutor`) whose one thread calls `CoInitializeEx` with `COINIT_APARTMENTTHREADED` during initialization. All WIA calls are marshalled onto this thread via `suspendCancellableCoroutine` + `executor.submit(Callable { … })`.

```kotlin
private val staExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
    Thread(r, "WIA-STA").also { it.isDaemon = true }
}

init {
    staExecutor.submit { Ole32.INSTANCE.CoInitializeEx(null, 0x2 /*COINIT_APARTMENTTHREADED*/) }.get()
}

private suspend fun <T> runOnSta(block: () -> T): T =
    suspendCancellableCoroutine { cont ->
        val future = staExecutor.submit(Callable {
            try { cont.resume(block()) } catch (e: Exception) { cont.resumeWithException(e) }
        })
        cont.invokeOnCancellation { future.cancel(true) }
    }
```

**Lesson:** COM apartment rules apply even when the COM objects are accessed from Java/Kotlin rather than C++. When integrating any legacy Windows COM API that predates the MTA model (scanners, printers, shell extensions), assume STA and dedicate a thread for the lifetime of the COM object.

---

## 6. Debugging Strategy: HRESULT Hex Formatting

A smaller but practically important issue: JNA returns `HRESULT` values as signed 32-bit integers. WIA error codes like `0x80040154` print as `-2147221164` in decimal — a number that is useless for looking up documentation or searching error code tables.

**Fix:** Add a pair of extension functions to format HRESULTs as unsigned hex:

```kotlin
fun Int.toHex(): String = "0x${Integer.toUnsignedString(this, 16).uppercase()}"
fun HRESULT.toHex(): String = this.toInt().toHex()
```

With these in place, error messages become `CoCreateInstance failed: 0x80040154` instead of `-2147221164`, making it immediately clear whether the error is `REGDB_E_CLASSNOTREG`, `E_NOINTERFACE`, or a WIA-specific code like `0x80210015` (`WIA_S_NO_DEVICE_AVAILABLE`).

---

## Summary: Debugging Order (Enumeration)

The bugs above were discovered in this sequence:

1. `CLSCTX_INPROC_SERVER` → `0x80040154` on first run; immediately obvious once formatted as hex
2. Wrong IIDs → access violations / `E_NOINTERFACE`; found by diffing against `wia_lh.h`
3. `PROPSPEC` layout → property reads returned `VT_EMPTY`; found by printing raw `PROPVARIANT` bytes
4. Wrong BSTR free → heap corruption crash after first successful read; found by noting the exit code `0xC0000374` and correlating with the last successful operation
5. STA requirement → threading crash under coroutine dispatchers; found by testing from a plain `Thread` vs. dispatcher

---

## 7. WIA Automation Layer (`wiaaut.dll`) Removed from Modern Windows

**Symptom:** `CoCreateInstance` for CLSID `{850D1D11-70F3-4BE5-9A11-77AA6B2BB201}` returned `0x80040154` (`REGDB_E_CLASSNOTREG`) on Windows 11.

**Root cause:** The WIA Automation Layer (`wiaaut.dll`) — the high-level COM scripting wrapper that exposed `CommonDialog.ShowAcquireImage` — was **permanently removed from Windows starting with version 1703** (Creators Update, 2017). It is not present on any currently-supported Windows version. Any code targeting it will always fail with `REGDB_E_CLASSNOTREG`.

**Fix:** Replace all usage of the Automation Layer with the native WIA 2.0 COM API (`IWiaDevMgr2`, `IWiaItem2`, `IWiaTransfer`, `IWiaDevMgr2::GetImageDlg`). These are implemented in `wiaservc.dll`, which ships with every Windows version and is always registered.

**Lesson:** The WIA Automation Layer is widely documented online, but all of those resources are obsolete. When targeting Windows 10/11, use only the interfaces declared in `wia_lh.h`. Ignore any sample code or documentation that refers to `wiaaut.dll`, `WIA.CommonDialog`, or `WIA.DeviceManager`.

---

## 8. `IWiaDevMgr2::GetImageDlg` Has a Completely Different Vtable Index and Signature from WIA 1.0

**Symptom:** `IWiaDevMgr2::GetImageDlg` called at vtable index 7 returned `0x80070057` (`E_INVALIDARG`) regardless of argument values.

**Root cause:** The vtable layout of `IWiaDevMgr2` (WIA 2.0) is **not** the same as `IWiaDevMgr` (WIA 1.0). In WIA 1.0, `GetImageDlg` is at index 7. In WIA 2.0, the slot at index 7 is `RegisterEventCallbackInterface` — a completely different method with a completely different signature. `GetImageDlg` moved to **index 10** to accommodate three new event registration methods inserted between `SelectDeviceDlgID` and `GetImageDlg`.

Correct `IWiaDevMgr2` vtable (verified from `wia_lh.h`, Windows SDK 10.0.26100.0):

| Index | Method |
|---|---|
| 0–2 | `QueryInterface`, `AddRef`, `Release` (IUnknown) |
| 3 | `EnumDeviceInfo` |
| 4 | `CreateDevice` |
| 5 | `SelectDeviceDlg` |
| 6 | `SelectDeviceDlgID` |
| 7 | `RegisterEventCallbackInterface` ← **not** `GetImageDlg` |
| 8 | `RegisterEventCallbackProgram` |
| 9 | `RegisterEventCallbackCLSID` |
| **10** | **`GetImageDlg`** |

Additionally, the `GetImageDlg` *signature* changed completely between WIA 1.0 and 2.0:

| Parameter | WIA 1.0 (`IWiaDevMgr`) | WIA 2.0 (`IWiaDevMgr2`) |
|---|---|---|
| 1st | `HWND hwndParent` | `LONG lFlags` |
| 2nd | `LONG lDeviceType` | `BSTR bstrDeviceID` |
| 3rd | `LONG lFlags` | `HWND hwndParent` |
| 4th | `LONG lIntent` | `BSTR bstrFolderName` |
| 5th | `IWiaItem2* pItemRoot` | `BSTR bstrFilename` |
| 6th | `BSTR bstrFilename` (full path) | `LONG* plNumFiles` (out) |
| 7th | `GUID* pguidFormat` | `BSTR** ppbstrFilePaths` (out) |
| 8th | _(none)_ | `IWiaItem2** ppItem` (out) |

The WIA 2.0 version saves files to a folder and returns an array of file paths via `ppbstrFilePaths`, rather than writing to a caller-specified path.

**Fix:** Call `GetImageDlg` at vtable index 10 with the WIA 2.0 signature. Always derive vtable indices from the SDK header, never from documentation or blog posts.

**Lesson:** When upgrading from WIA 1.0 to WIA 2.0, do not assume that a method with the same name has the same vtable position or signature. Verify every index and every parameter type against `wia_lh.h`. A wrong vtable index is silent at compile time and produces a confusing HRESULT at runtime (in this case `E_INVALIDARG` from `RegisterEventCallbackInterface` rejecting our `GetImageDlg` arguments).

---

## 9. `IWiaDevMgr2::GetImageDlg` Returns `E_POINTER` for Null `bstrFolderName` / `bstrFilename`

**Symptom:** `GetImageDlg` (called at the correct vtable index 10) returned `0x80004003` (`E_POINTER`) even though all output-pointer arguments were valid.

**Root cause:** The MSDN documentation for `IWiaDevMgr2::GetImageDlg` lists `bstrFolderName` and `bstrFilename` as optional `[in]` parameters and implies they may be `NULL`. In practice, the WIA service implementation checks these pointers before the dialog is shown and returns `E_POINTER` if either is `NULL`. Passing a null BSTR (a null pointer) for either argument triggers this check regardless of what the documentation says.

**Fix:** Always pass non-null BSTRs for `bstrFolderName` and `bstrFilename`. A valid writable directory for `bstrFolderName` (e.g. the system temp directory) and any non-empty string for `bstrFilename` are sufficient. Allocate them with `OleAuto.SysAllocString` and free them with `OleAuto.SysFreeString` after the call:

```kotlin
val outputFolder = System.getProperty("java.io.tmpdir").trimEnd('\\', '/')
val bstrFolderName = OleAuto.INSTANCE.SysAllocString(outputFolder)
val bstrFilename   = OleAuto.INSTANCE.SysAllocString("wia_scan")
try {
    devMgr.getImageDlg(0, bstrDeviceID, hwnd, bstrFolderName, bstrFilename, ...)
} finally {
    OleAuto.INSTANCE.SysFreeString(bstrFolderName)
    OleAuto.INSTANCE.SysFreeString(bstrFilename)
}
```

A valid `HWND` is also required for `hwndParent`; passing `null` causes `E_INVALIDARG` on many WIA driver implementations. Use `User32.INSTANCE.GetDesktopWindow()` as a safe fallback when no application window HWND is available at the call site.

**Lesson:** COM documentation describing a parameter as "optional" does not guarantee the implementation tolerates a null pointer. When an `[in]` string parameter is labelled optional, always try passing an empty non-null BSTR (`SysAllocString("")`) rather than null. `E_POINTER` on an `[in]` parameter (not an `[out]` parameter) is a strong signal that the implementation enforces non-null even where docs do not.

---

## 10. `TYPE_BYTE_BINARY` Images Cannot Be JPEG-Encoded

**Symptom:** PDF generation threw an exception during image compression when the Black & White scan mode was selected.

**Root cause:** Java's JPEG image writer (`ImageIO` JPEG plugin) does not support `BufferedImage.TYPE_BYTE_BINARY` (1-bit packed pixels). Attempting to encode a binary image directly throws `javax.imageio.IIOException: Unsupported Image Type`. The black-and-white conversion in `ImageProcessor.toBinary` produced a `TYPE_BYTE_BINARY` image, which was then passed directly to the JPEG writer.

**Fix:** Use `BufferedImage.TYPE_BYTE_GRAY` instead of `TYPE_BYTE_BINARY` as the destination format in `toBinary`. The thresholding logic (pixel ≥ 128 → white, else → black) still produces a visually binary result, but stored in a grayscale buffer that the JPEG writer can encode:

```kotlin
// Before (broken):
val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_BYTE_BINARY)

// After (correct):
val dst = BufferedImage(src.width, src.height, BufferedImage.TYPE_BYTE_GRAY)
```

**Lesson:** JPEG supports only three image types: 8-bit grayscale, 24-bit RGB, and 32-bit CMYK. Any other `BufferedImage` type must be converted before JPEG encoding. `TYPE_BYTE_BINARY`, `TYPE_BYTE_INDEXED`, and `TYPE_INT_ARGB` (alpha channel) are common sources of this failure. If in doubt, explicitly convert to `TYPE_BYTE_GRAY` or `TYPE_INT_RGB` before handing an image to the JPEG writer.

---

## Summary: Debugging Order (Acquisition)

The acquisition bugs were discovered in this sequence:

1. `wiaaut.dll` not registered → `0x80040154`; identified by looking up CLSID in registry
2. Wrong vtable index (7 instead of 10) for `GetImageDlg` → `0x80070057` (`E_INVALIDARG`) from `RegisterEventCallbackInterface` rejecting the arguments; found by reading `wia_lh.h` and counting vtable slots
3. Null `bstrFolderName`/`bstrFilename` → `0x80004003` (`E_POINTER`); identified by process of elimination after the vtable index was corrected
4. Null `hwndParent` → `E_INVALIDARG`; fixed by using `GetDesktopWindow()`
5. `TYPE_BYTE_BINARY` JPEG crash → exception during PDF build with B&W mode; identified from the `IIOException` message
