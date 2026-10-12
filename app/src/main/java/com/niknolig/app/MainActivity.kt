package com.niknolig.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var scanStatus: TextView
    private lateinit var scanButton: Button
    private lateinit var scanResults: LinearLayout
    private var scanner: BluetoothLeScanner? = null
    private var activeScanCallback: ScanCallback? = null
    private val discoveredDevices = LinkedHashMap<BluetoothDevice, TextView>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scanTimeout = Runnable {
        stopScan(getString(R.string.scan_time_limit_reached))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scanStatus = findViewById(R.id.scan_status)
        scanButton = findViewById(R.id.device_scan_button)
        scanResults = findViewById(R.id.scan_results)
        scanButton.setOnClickListener {
            if (activeScanCallback == null) {
                requestPermissionAndStartScan()
            } else {
                stopScan(getString(R.string.scan_stopped))
            }
        }

        findViewById<Button>(R.id.support_contact_button).setOnClickListener {
            openTechnicalSupportEmail()
        }
    }

    override fun onStop() {
        stopScan(getString(R.string.scan_stopped))
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_SCAN_PERMISSION) return

        if (hasScanPermission()) {
            startScan()
        } else {
            scanStatus.setText(R.string.scan_permission_denied)
        }
    }

    private fun requestPermissionAndStartScan() {
        if (hasScanPermission()) {
            startScan()
            return
        }

        scanStatus.setText(R.string.scan_permission_required)
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        requestPermissions(arrayOf(permission), REQUEST_SCAN_PERMISSION)
    }

    private fun hasScanPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun startScan() {
        if (activeScanCallback != null) return
        if (!hasScanPermission()) {
            scanStatus.setText(R.string.scan_permission_required)
            return
        }

        val bleScanner = try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter?.bluetoothLeScanner
        } catch (_: SecurityException) {
            scanStatus.setText(R.string.scan_permission_denied)
            return
        } catch (_: IllegalStateException) {
            scanStatus.setText(R.string.scan_bluetooth_unavailable)
            return
        }
        if (bleScanner == null) {
            scanStatus.setText(R.string.scan_bluetooth_unavailable)
            return
        }

        clearDiscoveredDevices()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                runOnUiThread {
                    if (activeScanCallback === this) addOrUpdateDevice(result)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                runOnUiThread {
                    if (activeScanCallback === this) results.forEach(::addOrUpdateDevice)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                runOnUiThread {
                    if (activeScanCallback === this) {
                        stopScan(getString(R.string.scan_failed))
                    }
                }
            }
        }

        scanner = bleScanner
        activeScanCallback = callback
        scanButton.setText(R.string.scan_stop_button)
        scanStatus.setText(R.string.scan_running)
        try {
            bleScanner.startScan(
                null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    .build(),
                callback
            )
            mainHandler.postDelayed(scanTimeout, SCAN_DURATION_MS)
        } catch (_: SecurityException) {
            mainHandler.removeCallbacks(scanTimeout)
            activeScanCallback = null
            scanner = null
            scanButton.setText(R.string.scan_start_button)
            clearDiscoveredDevices()
            scanStatus.setText(R.string.scan_permission_denied)
        } catch (_: IllegalStateException) {
            mainHandler.removeCallbacks(scanTimeout)
            activeScanCallback = null
            scanner = null
            scanButton.setText(R.string.scan_start_button)
            clearDiscoveredDevices()
            scanStatus.setText(R.string.scan_failed)
        }
    }

    private fun addOrUpdateDevice(result: ScanResult) {
        val device = result.device
        val name = result.scanRecord?.deviceName
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(MAX_DEVICE_NAME_LENGTH)
            ?.takeIf(String::isNotEmpty)
            ?: getString(R.string.scan_unknown_device)
        val row = discoveredDevices[device]
        if (row == null) {
            if (discoveredDevices.size >= MAX_DISPLAYED_DEVICES) return
            if (discoveredDevices.isEmpty()) scanResults.removeAllViews()
            val deviceRow = TextView(this).apply {
                setTextColor(getColor(R.color.text_primary))
                textSize = 14f
            }
            discoveredDevices[device] = deviceRow
            scanResults.addView(deviceRow)
        }

        discoveredDevices.getValue(device).apply {
            contentDescription = getString(
                R.string.scan_device_accessibility,
                name,
                result.rssi
            )
            text = getString(R.string.scan_device_result, name, result.rssi)
        }
    }

    private fun clearDiscoveredDevices() {
        discoveredDevices.clear()
        scanResults.removeAllViews()
        scanResults.addView(TextView(this).apply {
            setText(R.string.scan_no_devices)
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
        })
    }

    private fun stopScan(status: String) {
        val callback = activeScanCallback ?: return
        activeScanCallback = null
        mainHandler.removeCallbacks(scanTimeout)
        try {
            scanner?.stopScan(callback)
        } catch (_: SecurityException) {
            // Permission may have been revoked while the scan was active.
        } catch (_: IllegalStateException) {
            // The platform may already have stopped the scan.
        } finally {
            scanner = null
            scanButton.setText(R.string.scan_start_button)
            scanStatus.text = status
            clearDiscoveredDevices()
        }
    }

    private fun openTechnicalSupportEmail() {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.fromParts("mailto", getString(R.string.support_email), null)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_email_subject))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.support_email_body))
        }

        try {
            startActivity(emailIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.support_email_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val REQUEST_SCAN_PERMISSION = 1001
        const val MAX_DISPLAYED_DEVICES = 50
        const val MAX_DEVICE_NAME_LENGTH = 48
        const val SCAN_DURATION_MS = 30_000L
    }
}
