package co.doubler.spectrum.data.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import co.doubler.spectrum.domain.model.WifiSignal
import co.doubler.spectrum.util.Constants.RSSI_SMOOTHING_FACTOR
import co.doubler.spectrum.util.Constants.WIFI_CONNECTED_CHECK_INTERVAL_MS
import co.doubler.spectrum.util.Constants.WIFI_SCAN_INTERVAL_MS
import co.doubler.spectrum.util.SignalUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiScannerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wifiManager: WifiManager,
    private val connectivityManager: ConnectivityManager,
) : WifiScanner {

    companion object {
        private const val TAG = "WifiScannerImpl"
    }

    override val scanResults: Flow<List<WifiSignal>> = callbackFlow {
        val rssiCache = mutableMapOf<String, Double>()
        var connectedBssid: String? = null

        // Track connected BSSID for isUserNetwork flag
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                connectedBssid = extractBssid(capabilities)
            }

            override fun onLost(network: Network) {
                connectedBssid = null
            }
        }

        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot register network callback: ${e.message}")
        }

        val receiver = object : BroadcastReceiver() {
            @Suppress("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    val results = wifiManager.scanResults
                    val currentBssids = results.map { it.BSSID }.toSet()

                    // Evict BSSIDs that disappeared from scan
                    rssiCache.keys.removeAll { it !in currentBssids }

                    val signals = results.map { scanResult ->
                        val rawRssi = scanResult.level.toDouble()
                        val smoothedRssi = rssiCache[scanResult.BSSID]?.let { previous ->
                            SignalUtils.exponentialMovingAverage(previous, rawRssi, RSSI_SMOOTHING_FACTOR)
                        } ?: rawRssi
                        rssiCache[scanResult.BSSID] = smoothedRssi

                        WifiSignal(
                            bssid = scanResult.BSSID,
                            ssid = scanResult.SSID ?: "",
                            rssi = smoothedRssi.toInt(),
                            frequency = scanResult.frequency,
                            channel = SignalUtils.channelFromFrequency(scanResult.frequency),
                            capabilities = scanResult.capabilities ?: "",
                            isUserNetwork = scanResult.BSSID == connectedBssid,
                            timestamp = System.currentTimeMillis(),
                            wifiStandard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                when (scanResult.wifiStandard) {
                                    ScanResult.WIFI_STANDARD_LEGACY -> "WiFi Legacy"
                                    ScanResult.WIFI_STANDARD_11N    -> "WiFi 4 (802.11n)"
                                    ScanResult.WIFI_STANDARD_11AC   -> "WiFi 5 (802.11ac)"
                                    ScanResult.WIFI_STANDARD_11AX   -> "WiFi 6 (802.11ax)"
                                    6                               -> "WiFi 7 (802.11be)"
                                    else                            -> "Desconocido"
                                }
                            } else "Desconocido",
                            wpsEnabled = scanResult.capabilities.contains("WPS"),
                            channelWidth = when (scanResult.channelWidth) {
                                ScanResult.CHANNEL_WIDTH_20MHZ          -> "20 MHz"
                                ScanResult.CHANNEL_WIDTH_40MHZ          -> "40 MHz"
                                ScanResult.CHANNEL_WIDTH_80MHZ          -> "80 MHz"
                                ScanResult.CHANNEL_WIDTH_160MHZ         -> "160 MHz"
                                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
                                else                                    -> "Desconocido"
                            },
                        )
                    }
                    trySend(signals)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Permission denied reading scan results: ${e.message}")
                    trySend(emptyList())
                }
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(receiver, filter)

        // Periodic scan trigger
        val scanJob = launch {
            while (true) {
                try {
                    @Suppress("MissingPermission")
                    if (wifiManager.isWifiEnabled) {
                        @Suppress("DEPRECATION")
                        wifiManager.startScan()
                    } else {
                        Log.w(TAG, "WiFi is disabled, skipping scan")
                        trySend(emptyList())
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Permission denied starting scan: ${e.message}")
                    trySend(emptyList())
                }
                delay(WIFI_SCAN_INTERVAL_MS)
            }
        }

        awaitClose {
            scanJob.cancel()
            context.unregisterReceiver(receiver)
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: IllegalArgumentException) {
                // Callback was never registered or already unregistered
            }
        }
    }

    override val connectedNetwork: Flow<WifiSignal?> = callbackFlow {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                val wifiSignal = extractConnectedWifiSignal(capabilities)
                trySend(wifiSignal)
            }

            override fun onLost(network: Network) {
                trySend(null)
            }
        }

        try {
            connectivityManager.registerNetworkCallback(networkRequest, callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot register network callback for connected network: ${e.message}")
            trySend(null)
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: IllegalArgumentException) {
                // Already unregistered
            }
        }
    }

    private fun extractBssid(capabilities: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiInfo = capabilities.transportInfo as? WifiInfo
            return wifiInfo?.bssid
        }
        return null
    }

    @Suppress("MissingPermission")
    private fun extractConnectedWifiSignal(capabilities: NetworkCapabilities): WifiSignal? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val wifiInfo = capabilities.transportInfo as? WifiInfo ?: return null
            return WifiSignal(
                bssid = wifiInfo.bssid ?: return null,
                ssid = wifiInfo.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: "",
                rssi = wifiInfo.rssi,
                frequency = wifiInfo.frequency,
                channel = SignalUtils.channelFromFrequency(wifiInfo.frequency),
                capabilities = "",
                isUserNetwork = true,
                timestamp = System.currentTimeMillis(),
            )
        }
        return null
    }
}
