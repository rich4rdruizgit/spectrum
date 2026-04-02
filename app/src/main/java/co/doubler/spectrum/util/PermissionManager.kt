package co.doubler.spectrum.util

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.doubler.spectrum.domain.model.ScanMode

// ── Permission groups per scan mode ──────────────────────────────

object PermissionGroups {

    private val wifiPermissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE
    )

    private val bluetoothPermissions = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private val magneticPermissions = listOf(
        Manifest.permission.CAMERA
    )

    fun forMode(mode: ScanMode): List<String> = when (mode) {
        ScanMode.GHOST, ScanMode.COMPETE -> wifiPermissions
        ScanMode.BLUETOOTH -> bluetoothPermissions
        ScanMode.MAGNETIC -> magneticPermissions
    }
}

// ── Permission state holder ──────────────────────────────────────

data class PermissionState(
    val allGranted: Boolean,
    val deniedPermissions: List<String>,
    val shouldShowRationale: Boolean,
    val requestPermissions: () -> Unit
)

// ── Composable permission helper ─────────────────────────────────

@Composable
fun rememberPermissionState(permissions: List<String>): PermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionState by remember { mutableStateOf(checkPermissions(context, permissions)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionState = permissionState.copy(
            allGranted = results.values.all { it },
            deniedPermissions = results.filterValues { !it }.keys.toList()
        )
    }

    // Re-check permissions when returning from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = checkPermissions(context, permissions)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return permissionState.copy(
        requestPermissions = { launcher.launch(permissions.toTypedArray()) }
    )
}

private fun checkPermissions(
    context: android.content.Context,
    permissions: List<String>
): PermissionState {
    val denied = permissions.filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PermissionChecker.PERMISSION_GRANTED
    }
    return PermissionState(
        allGranted = denied.isEmpty(),
        deniedPermissions = denied,
        shouldShowRationale = false, // Updated by Activity context when available
        requestPermissions = {} // Replaced by launcher in composable
    )
}
