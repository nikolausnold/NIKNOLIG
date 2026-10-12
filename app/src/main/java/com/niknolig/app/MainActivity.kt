package com.niknolig.app

import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val discovery = BleDiscoveryRegistry()
    private lateinit var scanButton: Button
    private lateinit var scanStatus: TextView
    private lateinit var deviceList: TextView
    private var isScanning = false

    private val scanTimeout = Runnable {
        finishScanning(getString(R.string.scan_complete))
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            recordResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::recordResult)
        }

        override fun onScanFailed(errorCode: Int) {
            finishScanning(getString(R.string.scan_failed, errorCode))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scanButton = findViewById(R.id.scan_button)
        scanStatus = findViewById(R.id.scan_status)
        deviceList = findViewById(R.id.device_list)
        scanButton.setOnClickListener {
            if (isScanning) {
                finishScanning(getString(R.string.scan_stopped))
            } else {
                startScanning()
            }
        }
        renderDevices()
    }

    override fun onPause() {
        if (isScanning) {
            finishScanning(getString(R.string.scan_stopped))
        }
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_BLUETOOTH_SCAN) return

        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            beginScan()
        } else {
            scanStatus.setText(R.string.scan_permission_denied)
        }
    }

    private fun startScanning() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(android.Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missingPermissions = requiredPermissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions.toTypedArray(), REQUEST_BLUETOOTH_SCAN)
            return
        }
        beginScan()
    }

    private fun beginScan() {
        try {
            val scanner = getSystemService(BluetoothManager::class.java)
                ?.adapter
                ?.bluetoothLeScanner
            if (scanner == null) {
                scanStatus.setText(R.string.bluetooth_unavailable)
                return
            }

            discovery.clear()
            renderDevices()
            isScanning = true
            scanButton.setText(R.string.stop_scanning)
            scanStatus.setText(R.string.scanning)
            scanner.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(),
                scanCallback
            )
            mainHandler.postDelayed(scanTimeout, SCAN_DURATION_MILLIS)
        } catch (_: SecurityException) {
            finishScanning(getString(R.string.scan_permission_denied))
        } catch (_: IllegalStateException) {
            finishScanning(getString(R.string.scan_failed_generic))
        }
    }

    private fun recordResult(result: ScanResult) {
        if (!isScanning) return

        val record = result.scanRecord
        val address = try {
            result.device.address
        } catch (_: SecurityException) {
            null
        }
        val identity = address?.let { "address:${it.uppercase()}" }
            ?: "device:${result.device.hashCode()}"
        discovery.observe(
            BleScanObservation(
                identityKey = identity,
                name = record?.deviceName,
                address = address,
                rssi = result.rssi,
                advertisedServices = record?.serviceUuids
                    ?.map { it.uuid.toString() }
                    ?.toSet()
                    .orEmpty(),
                manufacturerIds = record?.manufacturerSpecificData
                    ?.let { data -> (0 until data.size()).map { data.keyAt(it) }.toSet() }
                    .orEmpty()
            )
        )
        val devices = discovery.snapshot()
        renderDevices()
        scanStatus.text = getString(R.string.scan_found_count, devices.size)
    }

    private fun finishScanning(message: String) {
        if (isScanning) {
            try {
                getSystemService(BluetoothManager::class.java)
                    ?.adapter
                    ?.bluetoothLeScanner
                    ?.stopScan(scanCallback)
            } catch (_: SecurityException) {
                // The OS may revoke scan permission while a scan is active.
            }
        }
        isScanning = false
        mainHandler.removeCallbacks(scanTimeout)
        scanButton.setText(R.string.start_scanning)
        scanStatus.text = message
    }

    private fun renderDevices() {
        val devices = discovery.snapshot()
        if (devices.isEmpty()) {
            deviceList.setText(R.string.no_devices_found)
            return
        }

        deviceList.text = devices.joinToString(separator = "\n\n") { device ->
            val name = device.name ?: getString(R.string.unnamed_device)
            val address = device.address?.let {
                getString(R.string.device_address, it)
            } ?: getString(R.string.address_unavailable)
            val services = device.advertisedServices.takeIf { it.isNotEmpty() }
                ?.joinToString()
                ?: getString(R.string.no_advertised_services)
            val manufacturers = device.manufacturerIds.takeIf { it.isNotEmpty() }
                ?.joinToString { "0x%04X".format(it) }
                ?: getString(R.string.no_manufacturer_data)

            listOf(
                name,
                getString(R.string.device_type_unknown),
                address,
                getString(
                    R.string.device_signal,
                    device.rssi,
                    getString(
                        when {
                            device.rssi >= -60 -> R.string.signal_strong
                            device.rssi >= -75 -> R.string.signal_medium
                            else -> R.string.signal_weak
                        }
                    )
                ),
                getString(R.string.device_compatibility_unverified),
                getString(R.string.device_services, services),
                getString(R.string.device_manufacturers, manufacturers)
            ).joinToString(separator = "\n")
        }
    }

    private companion object {
        const val REQUEST_BLUETOOTH_SCAN = 1001
        const val SCAN_DURATION_MILLIS = 10_000L
    }
}
