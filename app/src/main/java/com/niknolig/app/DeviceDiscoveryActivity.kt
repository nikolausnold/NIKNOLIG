package com.niknolig.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class DeviceDiscoveryActivity : Activity() {
    private lateinit var scanButton: Button
    private lateinit var scanStatus: TextView
    private lateinit var resultsContainer: LinearLayout
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var scanning = false
    private var scanStartedAt = 0L
    private val discoveredDevices = linkedMapOf<String, ScanResult>()
    private val updateScanStatus = object : Runnable {
        override fun run() {
            if (!scanning) return
            val remainingMillis = SCAN_DURATION_MILLIS - (SystemClock.elapsedRealtime() - scanStartedAt)
            if (remainingMillis <= 0) {
                stopScan(R.string.scan_complete)
                return
            }
            val remainingSeconds = (remainingMillis + 999) / 1000
            scanStatus.text = getString(R.string.scanning_countdown, remainingSeconds)
            mainHandler.postDelayed(this, STATUS_UPDATE_INTERVAL_MILLIS)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            runOnUiThread {
                if (!scanning) return@runOnUiThread
                val key = deviceKey(result)
                if (!discoveredDevices.containsKey(key) && discoveredDevices.size >= MAX_RESULTS) {
                    return@runOnUiThread
                }
                discoveredDevices[key] = result
                renderResults()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                scanning = false
                scanner = null
                mainHandler.removeCallbacks(updateScanStatus)
                scanButton.setText(R.string.start_scan)
                scanStatus.setText(R.string.scan_failed)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_discovery)

        scanButton = findViewById(R.id.scan_button)
        scanStatus = findViewById(R.id.scan_status)
        resultsContainer = findViewById(R.id.device_results)
        findViewById<Button>(R.id.back_button).setOnClickListener { finish() }
        scanButton.setOnClickListener {
            if (scanning) stopScan() else requestPermissionAndScan()
        }
    }

    override fun onPause() {
        stopScan()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_BLE_PERMISSION) return

        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startScan()
        } else {
            scanStatus.setText(R.string.scan_permission_denied)
        }
    }

    private fun requestPermissionAndScan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            startScan()
        } else {
            requestPermissions(arrayOf(permission), REQUEST_BLE_PERMISSION)
        }
    }

    private fun startScan() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            scanner = adapter?.bluetoothLeScanner
            val activeScanner = scanner
            if (activeScanner == null) {
                scanStatus.setText(R.string.bluetooth_unavailable)
                return
            }

            discoveredDevices.clear()
            renderResults()
            activeScanner.startScan(scanCallback)
            scanning = true
            scanStartedAt = SystemClock.elapsedRealtime()
            scanButton.setText(R.string.stop_scan)
            mainHandler.removeCallbacks(updateScanStatus)
            updateScanStatus.run()
        } catch (_: SecurityException) {
            scanStatus.setText(R.string.scan_permission_denied)
            scanner = null
        } catch (_: IllegalStateException) {
            scanStatus.setText(R.string.bluetooth_unavailable)
            scanner = null
        }
    }

    private fun stopScan(statusMessage: Int = R.string.scan_stopped) {
        mainHandler.removeCallbacks(updateScanStatus)
        if (!scanning) return
        try {
            scanner?.stopScan(scanCallback)
            scanStatus.setText(statusMessage)
        } catch (_: SecurityException) {
            scanStatus.setText(R.string.scan_permission_denied)
        } finally {
            scanning = false
            scanner = null
            if (::scanButton.isInitialized) scanButton.setText(R.string.start_scan)
        }
    }

    private fun renderResults() {
        resultsContainer.removeAllViews()
        if (discoveredDevices.isEmpty()) {
            val emptyState = TextView(this).apply {
                setText(R.string.no_devices_found)
                setTextColor(getColor(R.color.text_secondary))
                textSize = 15f
                setPadding(0, resources.getDimensionPixelSize(R.dimen.space_medium), 0, 0)
            }
            resultsContainer.addView(emptyState)
            return
        }

        discoveredDevices.values
            .sortedByDescending { it.rssi }
            .forEach { result ->
                val item = TextView(this).apply {
                    text = getString(
                        R.string.device_result,
                        result.scanRecord?.deviceName ?: getString(R.string.unnamed_device),
                        result.rssi
                    )
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 16f
                    isClickable = true
                    isFocusable = true
                    setPadding(
                        resources.getDimensionPixelSize(R.dimen.card_padding),
                        resources.getDimensionPixelSize(R.dimen.space_medium),
                        resources.getDimensionPixelSize(R.dimen.card_padding),
                        resources.getDimensionPixelSize(R.dimen.space_medium)
                    )
                    setBackgroundColor(getColor(R.color.dark_surface))
                    setOnClickListener { showAdvertisementDetails(result) }
                }
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.space_small)
                }
                resultsContainer.addView(item, params)
            }
    }

    private fun showAdvertisementDetails(result: ScanResult) {
        val record = result.scanRecord
        val serviceUuids = record?.serviceUuids.orEmpty()
            .map { it.uuid.toString() }
            .toTypedArray()
        val manufacturerData = record?.manufacturerSpecificData?.let { data ->
            (0 until data.size()).map { index ->
                val companyId = data.keyAt(index)
                getString(
                    R.string.manufacturer_data_entry,
                    companyId,
                    data.valueAt(index).toHexString()
                )
            }.toTypedArray()
        }.orEmpty()
        val serviceData = record?.serviceData?.map { (uuid, data) ->
            getString(R.string.service_data_entry, uuid.uuid, data.toHexString())
        }?.toTypedArray().orEmpty()
        startActivity(
            Intent(this, AdvertisementDetailsActivity::class.java).apply {
                putExtra(AdvertisementDetailsActivity.EXTRA_DEVICE_NAME, record?.deviceName)
                putExtra(AdvertisementDetailsActivity.EXTRA_RSSI, result.rssi)
                putExtra(
                    AdvertisementDetailsActivity.EXTRA_TX_POWER,
                    record?.txPowerLevel ?: Int.MIN_VALUE
                )
                putExtra(AdvertisementDetailsActivity.EXTRA_SERVICE_UUIDS, serviceUuids)
                putExtra(AdvertisementDetailsActivity.EXTRA_MANUFACTURER_DATA, manufacturerData)
                putExtra(AdvertisementDetailsActivity.EXTRA_SERVICE_DATA, serviceData)
                putExtra(AdvertisementDetailsActivity.EXTRA_SCAN_RESULT, result)
            }
        )
    }

    private fun deviceKey(result: ScanResult): String {
        val record = result.scanRecord
        val manufacturerData = record?.manufacturerSpecificData?.let { data ->
            (0 until data.size()).joinToString(";") { index ->
                "${data.keyAt(index)}:${data.valueAt(index).toHexString()}"
            }
        }.orEmpty()
        val serviceData = record?.serviceData?.entries.orEmpty()
            .sortedBy { it.key.uuid.toString() }
            .joinToString(";") { (uuid, data) -> "${uuid.uuid}:${data.toHexString()}" }
        return listOf(
            record?.deviceName.orEmpty(),
            record?.serviceUuids.orEmpty().map { it.uuid.toString() }.sorted().joinToString(";"),
            manufacturerData,
            serviceData
        ).joinToString("|")
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = " ") { byte -> "%02X".format(byte.toInt() and 0xFF) }

    companion object {
        private const val REQUEST_BLE_PERMISSION = 100
        private const val MAX_RESULTS = 50
        private const val SCAN_DURATION_MILLIS = 15_000L
        private const val STATUS_UPDATE_INTERVAL_MILLIS = 1_000L
    }
}
