package com.jimandreas.scanner

import com.jimandreas.state.ScanSettings
import java.awt.image.BufferedImage

interface ScannerRepository {
    suspend fun enumerateDevices(): List<ScannerDevice>
    suspend fun acquireImages(device: ScannerDevice, settings: ScanSettings): List<BufferedImage>
}
