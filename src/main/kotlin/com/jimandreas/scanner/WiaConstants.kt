package com.jimandreas.scanner

import com.sun.jna.platform.win32.Guid.GUID

object WiaConstants {

    // CLSID for WiaDevMgr2 (WIA Device Manager version 2)
    val CLSID_WiaDevMgr2: GUID = GUID("{B6C292BC-7C88-41EE-8B54-8EC92617E599}")

    // IID for IWiaDevMgr2
    val IID_IWiaDevMgr2: GUID = GUID("{814B5ACC-8B03-4F51-B75B-2CA6A0B8BD7C}")

    // IID for IEnumWIA_DEV_INFO
    val IID_IEnumWIA_DEV_INFO: GUID = GUID("{D8F-6B6-4CB6-8B15-73B0E4D7E2EF}")

    // IID for IWiaPropertyStorage
    val IID_IWiaPropertyStorage: GUID = GUID("{98CBEC27-1BC4-4E79-8B6B-AB6C67BEFF6A}")

    // IID for IWiaItem2
    val IID_IWiaItem2: GUID = GUID("{6CBA0075-1287-407D-9B77-CF0E030435CC}")

    // IID for IWiaTransfer
    val IID_IWiaTransfer: GUID = GUID("{C39D6942-2F4E-4D04-92FE-4EF4D3A1DE5A}")

    // WIA Device Information Property IDs
    const val WIA_DIP_DEV_ID = 2
    const val WIA_DIP_DEV_NAME = 7
    const val WIA_DIP_DEV_TYPE = 5
    const val WIA_DIP_BAUDRATE = 9
    const val WIA_DIP_STI_DRIVER_VERSION = 14
    const val WIA_DIP_WIA_VERSION = 15
    const val WIA_DIP_STI_GEN_CAPABILITIES = 16
    const val WIA_DIP_WIA_DEVICE_TYPE = 17
    const val WIA_DIP_MANUFACTURER = 6
    const val WIA_DIP_DRIVER_VERSION = 10
    const val WIA_DIP_FIRMWARE_VERSION = 11
    const val WIA_DIP_PORT_NAME = 8

    // WIA Image Item Property IDs
    const val WIA_IPA_DATATYPE = 4103
    const val WIA_IPA_DEPTH = 4104
    const val WIA_IPA_FORMAT = 4111
    const val WIA_IPA_FILENAME_EXTENSION = 4112
    const val WIA_IPA_PREFERRED_FORMAT = 4113
    const val WIA_IPA_ITEM_CATEGORY = 4120

    // WIA Scanner Device Property IDs
    const val WIA_DPS_DOCUMENT_HANDLING_SELECT = 3088
    const val WIA_DPS_DOCUMENT_HANDLING_STATUS = 3087
    const val WIA_DPS_PAGES = 3096
    const val WIA_DPS_PAGE_SIZE = 3097
    const val WIA_DPS_PAGE_WIDTH = 3098
    const val WIA_DPS_PAGE_HEIGHT = 3099
    const val WIA_DPS_HORIZONTAL_SHEET_FEED_SIZE = 3088
    const val WIA_DPS_SHEET_FEEDER_REGISTRATION = 3101
    const val WIA_DPS_PREVIEW = 3104

    // WIA Scan Head Property IDs (IPS)
    const val WIA_IPS_XRES = 6147
    const val WIA_IPS_YRES = 6148
    const val WIA_IPS_XPOS = 6149
    const val WIA_IPS_YPOS = 6150
    const val WIA_IPS_XEXTENT = 6151
    const val WIA_IPS_YEXTENT = 6152
    const val WIA_IPS_CUR_INTENT = 6145
    const val WIA_IPS_BRIGHTNESS = 6154
    const val WIA_IPS_CONTRAST = 6155
    const val WIA_IPS_ORIENTATION = 6156
    const val WIA_IPS_ROTATION = 6157
    const val WIA_IPS_MIRROR = 6158
    const val WIA_IPS_THRESHOLD = 6159
    const val WIA_IPS_INVERT = 6160
    const val WIA_IPS_PAGES = 3096
    const val WIA_IPS_DOCUMENT_HANDLING_SELECT = 3088
    const val WIA_IPS_DUPLEX_CAPABLE = 3092

    // WIA Data Type values (for WIA_IPA_DATATYPE)
    const val WIA_DATA_THRESHOLD = 0
    const val WIA_DATA_GRAYSCALE = 2
    const val WIA_DATA_COLOR = 3
    const val WIA_DATA_RAW_RGB = 4
    const val WIA_DATA_RAW_BGR = 5
    const val WIA_DATA_RAW_YUV = 6
    const val WIA_DATA_RAW_YUVK = 7

    // Document Handling Select values
    const val FEEDER = 0x001
    const val FLATBED = 0x002
    const val DUPLEX = 0x004
    const val FRONT_ONLY = 0x010
    const val BACK_ONLY = 0x020
    const val ADVANCED_DUPLEX = 0x100

    // WIA Intent flags
    const val WIA_INTENT_NONE = 0x00000000
    const val WIA_INTENT_IMAGE_TYPE_COLOR = 0x00000001
    const val WIA_INTENT_IMAGE_TYPE_GRAYSCALE = 0x00000002
    const val WIA_INTENT_IMAGE_TYPE_TEXT = 0x00000004
    const val WIA_INTENT_MINIMIZE_SIZE = 0x00010000
    const val WIA_INTENT_MAXIMIZE_QUALITY = 0x00020000

    // Common WIA HRESULTs
    const val WIA_S_NO_DEVICE_AVAILABLE = 0x80210015.toInt()
    const val WIA_ERROR_GENERAL_ERROR = 0x80210001.toInt()
    const val WIA_ERROR_PAPER_JAM = 0x80210003.toInt()
    const val WIA_ERROR_PAPER_EMPTY = 0x80210004.toInt()
    const val WIA_ERROR_PAPER_PROBLEM = 0x80210005.toInt()
    const val WIA_ERROR_OFFLINE = 0x80210006.toInt()
    const val WIA_ERROR_BUSY = 0x80210007.toInt()
    const val WIA_ERROR_WARMING_UP = 0x80210008.toInt()
    const val WIA_ERROR_USER_INTERVENTION = 0x80210009.toInt()
    const val WIA_ERROR_ITEM_DELETED = 0x8021000A.toInt()
    const val WIA_ERROR_DEVICE_COMMUNICATION = 0x8021000B.toInt()
    const val WIA_ERROR_INVALID_COMMAND = 0x8021000C.toInt()
    const val WIA_ERROR_INCORRECT_HARDWARE_SETTING = 0x8021000D.toInt()
    const val WIA_ERROR_DEVICE_LOCKED = 0x8021000E.toInt()
    const val WIA_ERROR_EXCEPTION_IN_DRIVER = 0x8021000F.toInt()
    const val WIA_ERROR_INVALID_DRIVER_RESPONSE = 0x80210010.toInt()

    // Transfer format GUIDs
    val WiaImgFmt_BMP: GUID = GUID("{B96B3CAB-0728-11D3-9D7B-0000F81EF32E}")
    val WiaImgFmt_JPEG: GUID = GUID("{B96B3CAE-0728-11D3-9D7B-0000F81EF32E}")
    val WiaImgFmt_TIFF: GUID = GUID("{B96B3CB1-0728-11D3-9D7B-0000F81EF32E}")
    val WiaImgFmt_PNG: GUID = GUID("{B96B3CAF-0728-11D3-9D7B-0000F81EF32E}")
}
