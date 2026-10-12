package com.niknolig.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleDiscoveryTest {
    @Test
    fun repeatedObservationsMergeAndUseLatestSignalAndName() {
        val registry = BleDiscoveryRegistry()
        registry.observe(
            BleScanObservation(
                identityKey = "address:AA",
                name = null,
                address = "AA",
                rssi = -80,
                advertisedServices = setOf("service-a"),
                manufacturerIds = setOf(1)
            )
        )
        registry.observe(
            BleScanObservation(
                identityKey = "address:AA",
                name = "Device",
                address = "AA",
                rssi = -45,
                advertisedServices = setOf("service-b"),
                manufacturerIds = setOf(2)
            )
        )

        val device = registry.snapshot().single()
        assertEquals("Device", device.name)
        assertEquals(-45, device.rssi)
        assertEquals(setOf("service-a", "service-b"), device.advertisedServices)
        assertEquals(setOf(1, 2), device.manufacturerIds)
    }

    @Test
    fun unnamedDevicesAreKeptAndCompatibilityIsNotInferred() {
        val registry = BleDiscoveryRegistry()
        registry.observe(
            BleScanObservation(
                identityKey = "address:BB",
                name = " ",
                address = "BB",
                rssi = -60,
                advertisedServices = emptySet(),
                manufacturerIds = emptySet()
            )
        )

        val device = registry.snapshot().single()
        assertNull(device.name)
        assertEquals(emptySet<String>(), device.advertisedServices)
        assertEquals(emptySet<Int>(), device.manufacturerIds)
    }
}
