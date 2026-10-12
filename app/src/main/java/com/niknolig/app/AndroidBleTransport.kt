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
import android.os.Build
import java.io.IOException

class AndroidBleTransport(context: Context) : BleTransport {
    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var connectionListener: BleTransport.ConnectionListener? = null

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

    override fun stopScan() {
        val activeCallback = scanCallback ?: return
        scanner?.stopScan(activeCallback)
        scanCallback = null
        scanner = null
    }

    override fun connect(device: BleDevice, listener: BleTransport.ConnectionListener) {
        val activeAdapter = adapter ?: throw IllegalStateException("Bluetooth is unavailable")
        if (!activeAdapter.isEnabled) throw IllegalStateException("Bluetooth is turned off")
        if (gatt != null) throw IllegalStateException("A BLE connection is already active")
        val remoteDevice = activeAdapter.getRemoteDevice(device.address)
        connectionListener = listener
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            remoteDevice.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            remoteDevice.connectGatt(appContext, false, gattCallback)
        } ?: throw IllegalStateException("Could not start BLE connection")
    }

    override fun disconnect() {
        val activeGatt = gatt
        if (activeGatt == null) {
            connectionListener?.onDisconnected()
        } else {
            activeGatt.disconnect()
        }
    }

    override fun close() {
        stopScan()
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

    @Suppress("MissingPermission")
    private fun ScanResult.toBleDevice(): BleDevice = BleDevice(
        address = device.address,
        name = device.name,
        rssi = rssi,
    )
}
