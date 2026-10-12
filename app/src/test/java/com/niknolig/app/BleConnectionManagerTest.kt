package com.niknolig.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleConnectionManagerTest {
    @Test
    fun explicitlySelectedDeviceCanConnectAndDisconnect() {
        val transport = FakeBleTransport()
        val listener = RecordingListener()
        val manager = BleConnectionManager(transport, listener)
        val device = BleDevice("AA:BB:CC:DD:EE:FF", "Sensor", -54)

        assertTrue(manager.startScan())
        transport.scanListener!!.onDeviceFound(device)
        assertEquals(listOf(device), listener.foundDevices)
        assertTrue(manager.connect(device))
        assertEquals(1, transport.stopScanCalls)
        assertEquals(BleConnectionState.CONNECTING, manager.state)
        assertEquals(device, listener.states.last().second)

        transport.connectionListener!!.onConnected()
        assertEquals(BleConnectionState.CONNECTED, manager.state)
        assertTrue(manager.disconnect())
        assertEquals(BleConnectionState.DISCONNECTING, manager.state)

        transport.connectionListener!!.onDisconnected()
        assertEquals(BleConnectionState.IDLE, manager.state)
        assertNull(listener.states.last().second)
    }

    @Test
    fun rejectsInvalidTransitionsAndStopsScanningExplicitly() {
        val transport = FakeBleTransport()
        val manager = BleConnectionManager(transport, RecordingListener())

        assertFalse(manager.disconnect())
        assertTrue(manager.startScan())
        assertFalse(manager.startScan())
        assertFalse(manager.disconnect())
        assertTrue(manager.stopScan())
        assertFalse(manager.stopScan())
        assertEquals(BleConnectionState.IDLE, manager.state)
    }

    @Test
    fun scanAndConnectionFailuresReturnToIdle() {
        val transport = FakeBleTransport()
        val listener = RecordingListener()
        val manager = BleConnectionManager(transport, listener)

        assertTrue(manager.startScan())
        transport.scanListener!!.onFailure(IllegalStateException("scan failed"))
        assertEquals(BleConnectionState.IDLE, manager.state)
        assertEquals("scan failed", listener.errors.single().message)

        val device = BleDevice("AA:BB:CC:DD:EE:FF", null, -80)
        assertTrue(manager.connect(device))
        transport.connectionListener!!.onFailure(IllegalStateException("connect failed"))
        assertEquals(BleConnectionState.IDLE, manager.state)
        assertEquals("connect failed", listener.errors.last().message)
    }

    private class RecordingListener : BleConnectionManager.Listener {
        val states = mutableListOf<Pair<BleConnectionState, BleDevice?>>()
        val foundDevices = mutableListOf<BleDevice>()
        val errors = mutableListOf<Throwable>()

        override fun onStateChanged(state: BleConnectionState, device: BleDevice?) {
            states += state to device
        }

        override fun onDeviceFound(device: BleDevice) {
            foundDevices += device
        }

        override fun onError(error: Throwable) {
            errors += error
        }
    }

    private class FakeBleTransport : BleTransport {
        var scanListener: BleTransport.ScanListener? = null
        var connectionListener: BleTransport.ConnectionListener? = null
        var stopScanCalls = 0

        override fun startScan(listener: BleTransport.ScanListener) {
            scanListener = listener
        }

        override fun stopScan() {
            stopScanCalls++
        }

        override fun connect(device: BleDevice, listener: BleTransport.ConnectionListener) {
            connectionListener = listener
        }

        override fun disconnect() = Unit

        override fun close() = Unit
    }
}
