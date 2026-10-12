package com.niknolig.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import java.io.IOException

class AndroidBleTransport(context: Context) : BleTransport {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var connectionListener: BleTransport.ConnectionListener? = null

    @SuppressLint("MissingPermission")
    override fun startScan(listener: BleTransport.ScanListener) {
        val activeAdapter = adapter ?: throw IllegalStateException("Bluetooth is unavailable")
        if (!activeAdapter.isEnabled) throw IllegalStateException("Bluetooth is turned off")
        val activeScanner = activeAdapter.bluetoothLeScanner
            ?: throw IllegalStateException("BLE scanning is unavailable")
        scanner = activeScanner
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                listener.onDeviceFound(result.toBleDevice())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { listener.onDeviceFound(it.toBleDevice()) }
            }

            override fun onScanFailed(errorCode: Int) {
                scanCallback = null
                listener.onFailure(IOException("BLE scan failed (code $errorCode)"))
            }
        }
        scanCallback = callback
        activeScanner.startScan(callback)
    }

    @SuppressLint("MissingPermission")
    override fun stopScan() {
        val activeCallback = scanCallback ?: return
        scanner?.stopScan(activeCallback)
        scanCallback = null
        scanner = null
    }

    @SuppressLint("MissingPermission")
    override fun connect(device: BleDevice, listener: BleTransport.ConnectionListener) {
        val activeAdapter = adapter ?: throw IllegalStateException("Bluetooth is unavailable")
        if (!activeAdapter.isEnabled) throw IllegalStateException("Bluetooth is turned off")
        if (gatt != null) throw IllegalStateException("A BLE connection is already active")
        val remoteDevice = activeAdapter.getRemoteDevice(device.address)
        connectionListener = listener
        gatt = remoteDevice.connectGatt(
            appContext,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE,
            BluetoothDevice.PHY_LE_1M_MASK,
            Handler(Looper.getMainLooper()),
        ) ?: throw IllegalStateException("Could not start BLE connection")
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        val activeGatt = gatt
        if (activeGatt == null) {
            connectionListener?.onDisconnected()
        } else {
            activeGatt.disconnect()
        }
    }

    override fun close() {
        try {
            stopScan()
        } catch (_: SecurityException) {
            scanCallback = null
            scanner = null
        }
        try {
            gatt?.disconnect()
        } catch (_: SecurityException) {
            // Closing the GATT below still releases the local connection.
        }
        gatt?.close()
        gatt = null
        connectionListener = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val error = IOException("BLE GATT connection failed (status $status)")
                connectionListener?.onFailure(error)
                gatt.close()
                this@AndroidBleTransport.gatt = null
                connectionListener = null
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> connectionListener?.onConnected()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectionListener?.onDisconnected()
                    gatt.close()
                    this@AndroidBleTransport.gatt = null
                    connectionListener = null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun ScanResult.toBleDevice(): BleDevice = BleDevice(
        address = device.address,
        name = device.name,
        rssi = rssi,
    )
}
