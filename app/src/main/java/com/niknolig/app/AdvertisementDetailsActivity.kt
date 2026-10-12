package com.niknolig.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattService
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
    private lateinit var servicesButton: Button
    private lateinit var servicesStatus: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bluetoothGatt: BluetoothGatt? = null
    private var connected = false
    private var connectionPending = false
    private var disconnectRequested = false
    private var serviceDiscoveryPending = false
    private var disconnectPending = false
    private val connectionTimeout = Runnable {
        val pendingGatt = bluetoothGatt ?: return@Runnable
        if (connectionPending) {
            bluetoothGatt = null
            connectionPending = false
            pendingGatt.close()
            showDisconnected(R.string.connection_timed_out)
        }
    }
    private val serviceDiscoveryTimeout = Runnable {
        if (serviceDiscoveryPending) {
            serviceDiscoveryPending = false
            servicesButton.isEnabled = connected
            servicesStatus.setText(R.string.service_discovery_timed_out)
        }
    }
    private val disconnectTimeout = Runnable {
        if (disconnectPending) {
            val gatt = bluetoothGatt
            bluetoothGatt = null
            disconnectPending = false
            connected = false
            gatt?.close()
            showDisconnected(R.string.disconnect_timed_out)
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
                    status != BluetoothGatt.GATT_SUCCESS -> {
                        mainHandler.removeCallbacks(connectionTimeout)
                        mainHandler.removeCallbacks(serviceDiscoveryTimeout)
                        mainHandler.removeCallbacks(disconnectTimeout)
                        bluetoothGatt = null
                        connectionPending = false
                        serviceDiscoveryPending = false
                        disconnectPending = false
                        connected = false
                        servicesButton.isEnabled = false
                        gatt.close()
                        showDisconnected(R.string.connection_failed)
                    }

                    status == BluetoothGatt.GATT_SUCCESS &&
                        newState == BluetoothProfile.STATE_CONNECTED -> {
                        mainHandler.removeCallbacks(connectionTimeout)
                        mainHandler.removeCallbacks(disconnectTimeout)
                        connectionPending = false
                        connected = true
                        disconnectRequested = false
                        disconnectPending = false
                        servicesStatus.setText(R.string.services_not_inspected)
                        servicesButton.isEnabled = true
                        connectionStatus.setText(R.string.connection_connected)
                        connectionButton.setText(R.string.disconnect_device)
                        connectionButton.isEnabled = true
                    }

                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        mainHandler.removeCallbacks(connectionTimeout)
                        mainHandler.removeCallbacks(disconnectTimeout)
                        bluetoothGatt = null
                        connectionPending = false
                        serviceDiscoveryPending = false
                        disconnectPending = false
                        connected = false
                        mainHandler.removeCallbacks(serviceDiscoveryTimeout)
                        servicesButton.isEnabled = false
                        servicesStatus.setText(R.string.services_unavailable_until_connected)
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

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            runOnUiThread {
                if (bluetoothGatt !== gatt || !connected) return@runOnUiThread
                mainHandler.removeCallbacks(serviceDiscoveryTimeout)
                serviceDiscoveryPending = false
                servicesButton.isEnabled = true
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    servicesStatus.setText(R.string.service_discovery_failed)
                    return@runOnUiThread
                }

                val discoveredServices = try {
                    gatt.services
                } catch (_: SecurityException) {
                    servicesStatus.setText(R.string.connection_permission_denied)
                    return@runOnUiThread
                }
                servicesStatus.text = if (discoveredServices.isEmpty()) {
                    getString(R.string.no_gatt_services)
                } else {
                    discoveredServices.joinToString(separator = "\n\n") { service ->
                        val characteristics = service.characteristics.joinToString("\n") { characteristic ->
                            val properties = characteristicProperties(characteristic)
                            val descriptors = characteristic.descriptors.joinToString("\n") { descriptor ->
                                getString(
                                    R.string.gatt_descriptor_entry,
                                    descriptor.uuid,
                                    descriptorPermissions(descriptor).ifEmpty {
                                        getString(R.string.no_descriptor_permissions)
                                    }
                                )
                            }
                            getString(
                                R.string.gatt_characteristic_entry,
                                characteristic.uuid,
                                if (properties.isEmpty()) {
                                    getString(R.string.no_characteristic_properties)
                                } else {
                                    properties.joinToString(", ")
                                },
                                if (descriptors.isEmpty()) {
                                    getString(R.string.no_gatt_descriptors)
                                } else {
                                    descriptors
                                }
                            )
                        }
                        getString(
                            R.string.gatt_service_entry,
                            service.uuid,
                            serviceType(service),
                            if (characteristics.isEmpty()) {
                                getString(R.string.no_gatt_characteristics)
                            } else {
                                characteristics
                            }
                        )
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
        servicesButton = findViewById(R.id.services_button)
        servicesStatus = findViewById(R.id.services_status)
        connectionButton.setOnClickListener {
            if (connected) disconnectDevice() else requestConnectionPermission()
        }
        servicesButton.setOnClickListener { discoverServices() }
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
        mainHandler.removeCallbacks(serviceDiscoveryTimeout)
        mainHandler.removeCallbacks(disconnectTimeout)
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
        servicesButton.isEnabled = false
        serviceDiscoveryPending = false
        mainHandler.removeCallbacks(serviceDiscoveryTimeout)
        disconnectPending = true
        servicesStatus.setText(R.string.services_unavailable_until_connected)
        connectionStatus.setText(R.string.connection_disconnecting)
        disconnectRequested = true
        try {
            gatt.disconnect()
            mainHandler.postDelayed(disconnectTimeout, CONNECTION_TIMEOUT_MILLIS)
        } catch (_: SecurityException) {
            bluetoothGatt = null
            connected = false
            disconnectPending = false
            gatt.close()
            showDisconnected(R.string.connection_permission_denied)
        }
    }

    private fun showDisconnected(messageResource: Int) {
        connected = false
        connectionPending = false
        serviceDiscoveryPending = false
        disconnectPending = false
        mainHandler.removeCallbacks(serviceDiscoveryTimeout)
        mainHandler.removeCallbacks(disconnectTimeout)
        servicesButton.isEnabled = false
        servicesStatus.setText(R.string.services_unavailable_until_connected)
        connectionStatus.setText(messageResource)
        connectionButton.setText(R.string.connect_device)
        connectionButton.isEnabled = true
    }

    private fun discoverServices() {
        val gatt = bluetoothGatt
        if (!connected || gatt == null) {
            servicesStatus.setText(R.string.services_unavailable_until_connected)
            return
        }
        servicesButton.isEnabled = false
        serviceDiscoveryPending = true
        servicesStatus.setText(R.string.service_discovery_in_progress)
        try {
            if (!gatt.discoverServices()) {
                serviceDiscoveryPending = false
                servicesButton.isEnabled = true
                servicesStatus.setText(R.string.service_discovery_failed)
            } else {
                mainHandler.postDelayed(serviceDiscoveryTimeout, CONNECTION_TIMEOUT_MILLIS)
            }
        } catch (_: SecurityException) {
            serviceDiscoveryPending = false
            servicesButton.isEnabled = true
            servicesStatus.setText(R.string.connection_permission_denied)
        }
    }

    private fun characteristicProperties(characteristic: BluetoothGattCharacteristic): List<String> {
        val properties = characteristic.properties
        return buildList {
            if (properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) {
                add(getString(R.string.characteristic_broadcast))
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                add(getString(R.string.characteristic_read))
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                add(getString(R.string.characteristic_write))
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                add(getString(R.string.characteristic_write_no_response))
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                add(getString(R.string.characteristic_notify))
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                add(getString(R.string.characteristic_indicate))
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) {
                add(getString(R.string.characteristic_signed_write))
            }
            if (properties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) {
                add(getString(R.string.characteristic_extended_properties))
            }
        }
    }

    private fun descriptorPermissions(descriptor: BluetoothGattDescriptor): List<String> {
        val permissions = descriptor.permissions
        return buildList {
            if (permissions and BluetoothGattDescriptor.PERMISSION_READ != 0) {
                add(getString(R.string.descriptor_read))
            }
            if (permissions and BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED != 0) {
                add(getString(R.string.descriptor_read_encrypted))
            }
            if (permissions and BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM != 0) {
                add(getString(R.string.descriptor_read_encrypted_mitm))
            }
            if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE != 0) {
                add(getString(R.string.descriptor_write))
            }
            if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED != 0) {
                add(getString(R.string.descriptor_write_encrypted))
            }
            if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM != 0) {
                add(getString(R.string.descriptor_write_encrypted_mitm))
            }
            if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED != 0) {
                add(getString(R.string.descriptor_write_signed))
            }
            if (permissions and BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED_MITM != 0) {
                add(getString(R.string.descriptor_write_signed_mitm))
            }
        }
    }

    private fun serviceType(service: BluetoothGattService): String =
        when (service.type) {
            BluetoothGattService.SERVICE_TYPE_PRIMARY -> getString(R.string.primary_gatt_service)
            BluetoothGattService.SERVICE_TYPE_SECONDARY -> getString(R.string.secondary_gatt_service)
            else -> getString(R.string.unknown_gatt_service_type)
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
