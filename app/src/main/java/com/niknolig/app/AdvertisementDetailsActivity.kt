package com.niknolig.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView

class AdvertisementDetailsActivity : Activity() {
    private lateinit var connectionStatus: TextView
    private lateinit var connectionButton: Button
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bluetoothGatt: BluetoothGatt? = null
    private var connected = false
    private var connectionPending = false
    private var disconnectRequested = false
    private val connectionTimeout = Runnable {
        val pendingGatt = bluetoothGatt ?: return@Runnable
        if (connectionPending) {
            bluetoothGatt = null
            connectionPending = false
            pendingGatt.close()
            showDisconnected(R.string.connection_timed_out)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            runOnUiThread {
                if (bluetoothGatt !== gatt) {
                    gatt.close()
                    return@runOnUiThread
                }

                when {
                    status == BluetoothGatt.GATT_SUCCESS &&
                        newState == BluetoothProfile.STATE_CONNECTED -> {
                        mainHandler.removeCallbacks(connectionTimeout)
                        connectionPending = false
                        connected = true
                        disconnectRequested = false
                        connectionStatus.setText(R.string.connection_connected)
                        connectionButton.setText(R.string.disconnect_device)
                        connectionButton.isEnabled = true
                    }

                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        mainHandler.removeCallbacks(connectionTimeout)
                        bluetoothGatt = null
                        connectionPending = false
                        connected = false
                        gatt.close()
                        showDisconnected(
                            if (disconnectRequested) R.string.connection_disconnected
                            else R.string.connection_failed
                        )
                        disconnectRequested = false
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advertisement_details)

        findViewById<Button>(R.id.back_button).setOnClickListener { finish() }
        connectionStatus = findViewById(R.id.connection_status)
        connectionButton = findViewById(R.id.connection_button)
        connectionButton.setOnClickListener {
            if (connected) disconnectDevice() else requestConnectionPermission()
        }
        findViewById<TextView>(R.id.device_name).text =
            intent.getStringExtra(EXTRA_DEVICE_NAME) ?: getString(R.string.unnamed_device)
        findViewById<TextView>(R.id.signal_strength).text =
            getString(R.string.signal_strength_value, intent.getIntExtra(EXTRA_RSSI, 0))

        val txPower = intent.getIntExtra(EXTRA_TX_POWER, Int.MIN_VALUE)
        findViewById<TextView>(R.id.transmit_power).text = if (txPower == Int.MIN_VALUE) {
            getString(R.string.transmit_power_unavailable)
        } else {
            getString(R.string.transmit_power_value, txPower)
        }

        val serviceUuids = intent.getStringArrayExtra(EXTRA_SERVICE_UUIDS).orEmpty()
        findViewById<TextView>(R.id.service_uuids).text = if (serviceUuids.isEmpty()) {
            getString(R.string.no_service_uuids)
        } else {
            serviceUuids.joinToString(separator = "\n")
        }

        val manufacturerData = intent.getStringArrayExtra(EXTRA_MANUFACTURER_DATA).orEmpty()
        findViewById<TextView>(R.id.manufacturer_data).text = if (manufacturerData.isEmpty()) {
            getString(R.string.no_manufacturer_data)
        } else {
            manufacturerData.joinToString(separator = "\n")
        }

        val serviceData = intent.getStringArrayExtra(EXTRA_SERVICE_DATA).orEmpty()
        findViewById<TextView>(R.id.service_data).text = if (serviceData.isEmpty()) {
            getString(R.string.no_service_data)
        } else {
            serviceData.joinToString(separator = "\n")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CONNECT_PERMISSION) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            connectDevice()
        } else {
            connectionStatus.setText(R.string.connection_permission_denied)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(connectionTimeout)
        bluetoothGatt?.let { gatt ->
            bluetoothGatt = null
            try {
                gatt.disconnect()
            } catch (_: SecurityException) {
            } finally {
                gatt.close()
            }
        }
        super.onDestroy()
    }

    private fun requestConnectionPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) {
            connectDevice()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_CONNECT_PERMISSION
            )
        }
    }

    private fun connectDevice() {
        val scanResult = getScanResult()
        if (scanResult == null) {
            connectionStatus.setText(R.string.connection_unavailable)
            return
        }

        connectionButton.isEnabled = false
        connectionStatus.setText(R.string.connection_connecting)
        connectionPending = true
        disconnectRequested = false
        try {
            val device = scanResult.device
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (bluetoothGatt == null) {
                connectionPending = false
                showDisconnected(R.string.connection_failed)
                return
            }
            mainHandler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT_MILLIS)
        } catch (_: SecurityException) {
            connectionPending = false
            bluetoothGatt = null
            showDisconnected(R.string.connection_permission_denied)
        } catch (_: IllegalArgumentException) {
            connectionPending = false
            bluetoothGatt = null
            showDisconnected(R.string.connection_failed)
        }
    }

    private fun disconnectDevice() {
        val gatt = bluetoothGatt ?: return
        connectionButton.isEnabled = false
        connectionStatus.setText(R.string.connection_disconnecting)
        disconnectRequested = true
        try {
            gatt.disconnect()
        } catch (_: SecurityException) {
            bluetoothGatt = null
            connected = false
            gatt.close()
            showDisconnected(R.string.connection_permission_denied)
        }
    }

    private fun showDisconnected(messageResource: Int) {
        connected = false
        connectionPending = false
        connectionStatus.setText(messageResource)
        connectionButton.setText(R.string.connect_device)
        connectionButton.isEnabled = true
    }

    @Suppress("DEPRECATION")
    private fun getScanResult(): ScanResult? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SCAN_RESULT, ScanResult::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_SCAN_RESULT)
        }

    companion object {
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_RSSI = "rssi"
        const val EXTRA_TX_POWER = "tx_power"
        const val EXTRA_SERVICE_UUIDS = "service_uuids"
        const val EXTRA_MANUFACTURER_DATA = "manufacturer_data"
        const val EXTRA_SERVICE_DATA = "service_data"
        const val EXTRA_SCAN_RESULT = "scan_result"

        private const val REQUEST_CONNECT_PERMISSION = 101
        private const val CONNECTION_TIMEOUT_MILLIS = 15_000L
    }
}
