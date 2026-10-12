package com.niknolig.app

data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
)

enum class BleConnectionState {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
}

interface BleTransport {
    interface ScanListener {
        fun onDeviceFound(device: BleDevice)
        fun onFailure(error: Throwable)
    }

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onFailure(error: Throwable)
    }

    fun startScan(listener: ScanListener)
    fun stopScan()
    fun connect(device: BleDevice, listener: ConnectionListener)
    fun disconnect()
    fun close()
}

class BleConnectionManager(
    private val transport: BleTransport,
    private val listener: Listener,
) {
    interface Listener {
        fun onStateChanged(state: BleConnectionState, device: BleDevice?)
        fun onDeviceFound(device: BleDevice)
        fun onError(error: Throwable)
    }

    var state: BleConnectionState = BleConnectionState.IDLE
        private set
    private var selectedDevice: BleDevice? = null

    fun startScan(): Boolean {
        if (state != BleConnectionState.IDLE) return false
        setState(BleConnectionState.SCANNING)
        return try {
            transport.startScan(object : BleTransport.ScanListener {
                override fun onDeviceFound(device: BleDevice) {
                    if (state == BleConnectionState.SCANNING) listener.onDeviceFound(device)
                }

                override fun onFailure(error: Throwable) {
                    if (state == BleConnectionState.SCANNING) fail(error)
                }
            })
            true
        } catch (error: Exception) {
            fail(error)
            false
        }
    }

    fun stopScan(): Boolean {
        if (state != BleConnectionState.SCANNING) return false
        return try {
            transport.stopScan()
            setState(BleConnectionState.IDLE)
            true
        } catch (error: Exception) {
            fail(error)
            false
        }
    }

    fun connect(device: BleDevice): Boolean {
        if (state != BleConnectionState.IDLE && state != BleConnectionState.SCANNING) return false
        if (state == BleConnectionState.SCANNING) {
            try {
                transport.stopScan()
            } catch (error: Exception) {
                fail(error)
                return false
            }
        }
        selectedDevice = device
        setState(BleConnectionState.CONNECTING)
        return try {
            transport.connect(device, object : BleTransport.ConnectionListener {
                override fun onConnected() {
                    if (state == BleConnectionState.CONNECTING) setState(BleConnectionState.CONNECTED)
                }

                override fun onDisconnected() {
                    if (state == BleConnectionState.DISCONNECTING ||
                        state == BleConnectionState.CONNECTED ||
                        state == BleConnectionState.CONNECTING
                    ) {
                        selectedDevice = null
                        setState(BleConnectionState.IDLE)
                    }
                }

                override fun onFailure(error: Throwable) {
                    if (state == BleConnectionState.CONNECTING ||
                        state == BleConnectionState.DISCONNECTING
                    ) {
                        fail(error)
                    }
                }
            })
            true
        } catch (error: Exception) {
            fail(error)
            false
        }
    }

    fun disconnect(): Boolean {
        if (state != BleConnectionState.CONNECTED && state != BleConnectionState.CONNECTING) return false
        setState(BleConnectionState.DISCONNECTING)
        return try {
            transport.disconnect()
            true
        } catch (error: Exception) {
            fail(error)
            false
        }
    }

    fun close() {
        try {
            if (state == BleConnectionState.SCANNING) transport.stopScan()
            if (state == BleConnectionState.CONNECTED ||
                state == BleConnectionState.CONNECTING ||
                state == BleConnectionState.DISCONNECTING
            ) {
                transport.disconnect()
            }
        } finally {
            transport.close()
        }
    }

    private fun fail(error: Throwable) {
        selectedDevice = null
        setState(BleConnectionState.IDLE)
        listener.onError(error)
    }

    private fun setState(next: BleConnectionState) {
        state = next
        listener.onStateChanged(next, selectedDevice)
    }
}
