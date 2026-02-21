package com.jimandreas.scanner

data class ScannerDevice(
    val id: String,
    val displayName: String,
    val connectionType: ConnectionType = ConnectionType.USB
)

enum class ConnectionType { USB, NETWORK, WSD }
