package co.doubler.spectrum.data.sensor

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import co.doubler.spectrum.domain.model.BluetoothDeviceType
import co.doubler.spectrum.domain.model.BluetoothNode
import co.doubler.spectrum.util.Constants.BLE_NEARBY_TIMEOUT_MS
import co.doubler.spectrum.util.Constants.BLE_SCAN_PAUSE_MS
import co.doubler.spectrum.util.Constants.BLE_SCAN_PERIOD_MS
import co.doubler.spectrum.util.Constants.DEFAULT_TX_POWER
import co.doubler.spectrum.util.Constants.PATH_LOSS_EXPONENT_INDOOR
import co.doubler.spectrum.util.SignalUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothScannerImpl @Inject constructor(
    private val bluetoothAdapter: BluetoothAdapter,
) : BluetoothScanner {

    companion object {
        private const val TAG = "BluetoothScannerImpl"
        private const val TX_POWER_NOT_AVAILABLE = 127 // Android ScanResult constant
    }

    override val scanResults: Flow<List<BluetoothNode>> = callbackFlow {
        val scanner: BluetoothLeScanner? = try {
            @Suppress("MissingPermission")
            if (!bluetoothAdapter.isEnabled) {
                Log.w(TAG, "Bluetooth adapter is disabled")
                trySend(emptyList())
                null
            } else {
                bluetoothAdapter.bluetoothLeScanner
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied accessing Bluetooth adapter: ${e.message}")
            trySend(emptyList())
            null
        }

        if (scanner == null) {
            awaitClose()
            return@callbackFlow
        }

        val deviceMap = ConcurrentHashMap<String, BluetoothNode>()

        val callback = object : ScanCallback() {
            @Suppress("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val node = mapToBluetoothNode(result)
                    deviceMap[node.address] = node
                } catch (e: SecurityException) {
                    Log.w(TAG, "Permission denied processing scan result: ${e.message}")
                }
            }

            @Suppress("MissingPermission")
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                try {
                    results.forEach { result ->
                        val node = mapToBluetoothNode(result)
                        deviceMap[node.address] = node
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Permission denied processing batch results: ${e.message}")
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE scan failed with error code: $errorCode")
                trySend(deviceMap.values.toList())
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val dutyCycleJob = launch {
            try {
                while (true) {
                    @Suppress("MissingPermission")
                    scanner.startScan(emptyList(), scanSettings, callback)
                    delay(BLE_SCAN_PERIOD_MS)

                    @Suppress("MissingPermission")
                    scanner.stopScan(callback)
                    trySend(deviceMap.values.toList())

                    delay(BLE_SCAN_PAUSE_MS)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission denied during BLE scan: ${e.message}")
                trySend(emptyList())
            }
        }

        awaitClose {
            dutyCycleJob.cancel()
            try {
                @Suppress("MissingPermission")
                scanner.stopScan(callback)
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission denied stopping BLE scan: ${e.message}")
            } catch (e: IllegalStateException) {
                // Scanner was already stopped or BT adapter turned off
            }
            deviceMap.clear()
        }
    }

    override val nearbyDevices: Flow<List<BluetoothNode>> = scanResults.map { devices ->
        val now = System.currentTimeMillis()
        devices.filter { now - it.lastSeen < BLE_NEARBY_TIMEOUT_MS }
    }

    @Suppress("MissingPermission")
    private fun mapToBluetoothNode(result: ScanResult): BluetoothNode {
        val device = result.device
        val rssi = result.rssi
        val txPower = result.txPower.takeIf { it != TX_POWER_NOT_AVAILABLE }
        val estimatedDistance = txPower?.let {
            SignalUtils.estimateDistance(rssi, it, PATH_LOSS_EXPONENT_INDOOR)
        } ?: SignalUtils.estimateDistance(rssi, DEFAULT_TX_POWER, PATH_LOSS_EXPONENT_INDOOR)

        return BluetoothNode(
            address = device.address,
            name = device.name,
            type = classifyDeviceType(device.bluetoothClass, device.name),
            rssi = rssi,
            isConnected = false,
            txPower = txPower,
            estimatedDistance = estimatedDistance,
            lastSeen = System.currentTimeMillis(),
        )
    }

    private fun classifyDeviceType(
        bluetoothClass: BluetoothClass?,
        name: String?,
    ): BluetoothDeviceType {
        // Try classification from BluetoothClass major device class
        bluetoothClass?.let { btClass ->
            return when (btClass.majorDeviceClass) {
                BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                    // Sub-classify audio devices
                    when {
                        btClass.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> BluetoothDeviceType.HEADPHONES
                        btClass.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> BluetoothDeviceType.SPEAKER
                        btClass.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> BluetoothDeviceType.SPEAKER
                        else -> BluetoothDeviceType.HEADPHONES // Default audio to headphones
                    }
                }
                BluetoothClass.Device.Major.PHONE -> BluetoothDeviceType.PHONE
                BluetoothClass.Device.Major.WEARABLE -> BluetoothDeviceType.WATCH
                else -> classifyFromName(name)
            }
        }

        // Fallback to name-based heuristics
        return classifyFromName(name)
    }

    private fun classifyFromName(name: String?): BluetoothDeviceType {
        if (name.isNullOrBlank()) return BluetoothDeviceType.UNKNOWN
        val lowerName = name.lowercase()
        return when {
            lowerName.contains("airpods") || lowerName.contains("buds") ||
                lowerName.contains("headphone") || lowerName.contains("earphone") ||
                lowerName.contains("earbuds") -> BluetoothDeviceType.HEADPHONES
            lowerName.contains("watch") || lowerName.contains("band") ||
                lowerName.contains("fit") -> BluetoothDeviceType.WATCH
            lowerName.contains("speaker") || lowerName.contains("soundbar") ||
                lowerName.contains("boom") || lowerName.contains("jbl") ||
                lowerName.contains("sonos") -> BluetoothDeviceType.SPEAKER
            lowerName.contains("phone") || lowerName.contains("pixel") ||
                lowerName.contains("galaxy") || lowerName.contains("iphone") -> BluetoothDeviceType.PHONE
            else -> BluetoothDeviceType.UNKNOWN
        }
    }
}
