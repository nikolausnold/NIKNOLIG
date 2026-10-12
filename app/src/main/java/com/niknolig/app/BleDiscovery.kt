package com.niknolig.app

internal data class BleScanObservation(
    val identityKey: String,
    val name: String?,
    val address: String?,
    val rssi: Int,
    val advertisedServices: Set<String>,
    val manufacturerIds: Set<Int>
)

internal data class DiscoveredBleDevice(
    val name: String?,
    val address: String?,
    val rssi: Int,
    val advertisedServices: Set<String>,
    val manufacturerIds: Set<Int>
)

internal class BleDiscoveryRegistry {
    private val devices = linkedMapOf<String, DiscoveredBleDevice>()

    fun observe(observation: BleScanObservation) {
        val previous = devices[observation.identityKey]
        devices[observation.identityKey] = DiscoveredBleDevice(
            name = observation.name?.takeIf { it.isNotBlank() } ?: previous?.name,
            address = observation.address ?: previous?.address,
            rssi = observation.rssi,
            advertisedServices = previous?.advertisedServices.orEmpty() + observation.advertisedServices,
            manufacturerIds = previous?.manufacturerIds.orEmpty() + observation.manufacturerIds
        )
    }

    fun snapshot(): List<DiscoveredBleDevice> = devices.values.sortedByDescending { it.rssi }

    fun clear() {
        devices.clear()
    }
}
