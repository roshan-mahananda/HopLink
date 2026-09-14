package com.example.bluetoothchatapplication02.bluetooth

import android.os.ParcelUuid
import java.util.UUID

object HopLinkConfig {
    val SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("0000FDB9-0000-1000-8000-00805f9b34fb")
    val SERVICE_UUID_JAVA: UUID = UUID.fromString("0000FDB9-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FDBA-0000-1000-8000-00805f9b34fb")
}