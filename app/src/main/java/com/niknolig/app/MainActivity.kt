package com.niknolig.app

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var connectionManager: BleConnectionManager
    private lateinit var connectionStatus: TextView
    private lateinit var devicesContainer: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var disconnectButton: Button
    private val devices = linkedMapOf<String, BleDevice>()
    private var pendingAction = PendingAction.None
    private val scanTimeoutHandler = Handler(Looper.getMainLooper())
    private val scanTimeout = Runnable {
        if (::connectionManager.isInitialized &&
            connectionManager.state == BleConnectionState.SCANNING
        ) {
            connectionManager.stopScan()
            connectionStatus.text = getString(R.string.ble_scan_timed_out)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectionStatus = findViewById(R.id.connection_status)
        devicesContainer = findViewById(R.id.devices_container)
        scanButton = findViewById(R.id.scan_button)
        disconnectButton = findViewById(R.id.disconnect_button)
        connectionManager = BleConnectionManager(
            AndroidBleTransport(this),
            object : BleConnectionManager.Listener {
                override fun onStateChanged(state: BleConnectionState, device: BleDevice?) {
                    runOnUiThread {
                        connectionStatus.text = when (state) {
                            BleConnectionState.IDLE -> getString(R.string.ble_idle)
                            BleConnectionState.SCANNING -> getString(R.string.ble_scanning)
                            BleConnectionState.CONNECTING -> getString(
                                R.string.ble_connecting,
                                device?.name ?: device?.address.orEmpty(),
                            )
                            BleConnectionState.CONNECTED -> getString(
                                R.string.ble_connected,
                                device?.name ?: device?.address.orEmpty(),
                            )
                            BleConnectionState.DISCONNECTING ->
                                getString(R.string.ble_disconnecting)
                        }
                        scanButton.isEnabled =
                            state == BleConnectionState.IDLE || state == BleConnectionState.SCANNING
                        scanButton.text = if (state == BleConnectionState.SCANNING) {
                            getString(R.string.stop_scan)
                        } else {
                            getString(R.string.scan_devices)
                        }
                        disconnectButton.visibility =
                            if (state == BleConnectionState.CONNECTED ||
                                state == BleConnectionState.CONNECTING
                            ) View.VISIBLE else View.GONE
                        scanTimeoutHandler.removeCallbacks(scanTimeout)
                        if (state == BleConnectionState.SCANNING) {
                            scanTimeoutHandler.postDelayed(scanTimeout, SCAN_TIMEOUT_MILLIS)
                        }
                    }
                }

                override fun onDeviceFound(device: BleDevice) {
                    runOnUiThread {
                        if (devices.put(device.address, device) == null) addDeviceRow(device)
                    }
                }

                override fun onError(error: Throwable) {
                    runOnUiThread {
                        connectionStatus.text = getString(
                            R.string.ble_error,
                            error.message ?: getString(R.string.unknown_error),
                        )
                    }
                }
            },
        )

        scanButton.setOnClickListener {
            if (connectionManager.state == BleConnectionState.SCANNING) {
                connectionManager.stopScan()
            } else {
                beginAction(PendingAction.Scan)
            }
        }
        disconnectButton.setOnClickListener { connectionManager.disconnect() }
        findViewById<Button>(R.id.assistant_button).setOnClickListener { showAssistant() }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            scanButton.isEnabled = false
            connectionStatus.text = getString(R.string.ble_not_supported)
        }
    }

    override fun onStop() {
        if (::connectionManager.isInitialized &&
            connectionManager.state == BleConnectionState.SCANNING
        ) {
            connectionManager.stopScan()
        }
        super.onStop()
    }

    override fun onDestroy() {
        scanTimeoutHandler.removeCallbacks(scanTimeout)
        if (::connectionManager.isInitialized) connectionManager.close()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android, retained for compatibility with minSdk 26")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_BLE_PERMISSIONS) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            performPendingAction()
        } else {
            pendingAction = PendingAction.None
            connectionStatus.text = getString(R.string.ble_permission_denied)
        }
    }

    private fun beginAction(action: PendingAction) {
        pendingAction = action
        val missingPermissions = requiredPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            performPendingAction()
            return
        }
        if (missingPermissions.any(::shouldShowRequestPermissionRationale)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.ble_permission_title)
                .setMessage(R.string.ble_permission_rationale)
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    pendingAction = PendingAction.None
                }
                .setPositiveButton(R.string.continue_label) { _, _ ->
                    requestPermissions(missingPermissions.toTypedArray(), REQUEST_BLE_PERMISSIONS)
                }
                .show()
        } else {
            requestPermissions(missingPermissions.toTypedArray(), REQUEST_BLE_PERMISSIONS)
        }
    }

    private fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun performPendingAction() {
        val action = pendingAction
        pendingAction = PendingAction.None
        when (action) {
            PendingAction.Scan -> {
                devices.clear()
                devicesContainer.removeAllViews()
                connectionManager.startScan()
            }
            is PendingAction.Connect -> connectionManager.connect(action.device)
            PendingAction.None -> Unit
        }
    }

    private fun addDeviceRow(device: BleDevice) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, resources.getDimensionPixelSize(R.dimen.space_small), 0, 0)
        }
        val details = TextView(this).apply {
            text = getString(
                R.string.ble_device_description,
                device.name ?: getString(R.string.unknown_device),
                device.address,
                device.rssi,
            )
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
        }
        row.addView(
            details,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(Button(this).apply {
            text = getString(R.string.connect)
            setOnClickListener { beginAction(PendingAction.Connect(device)) }
        })
        devicesContainer.addView(row)
    }

    private fun showAssistant() {
        AlertDialog.Builder(this)
            .setTitle(R.string.assistant_name)
            .setMessage(R.string.assistant_surface_description)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private sealed class PendingAction {
        data object None : PendingAction()
        data object Scan : PendingAction()
        data class Connect(val device: BleDevice) : PendingAction()
    }

    companion object {
        private const val REQUEST_BLE_PERMISSIONS = 101
        private const val SCAN_TIMEOUT_MILLIS = 15_000L
    }
}
