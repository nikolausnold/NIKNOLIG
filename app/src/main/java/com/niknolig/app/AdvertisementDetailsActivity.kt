package com.niknolig.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class AdvertisementDetailsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advertisement_details)

        findViewById<Button>(R.id.back_button).setOnClickListener { finish() }
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
    }

    companion object {
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_RSSI = "rssi"
        const val EXTRA_TX_POWER = "tx_power"
        const val EXTRA_SERVICE_UUIDS = "service_uuids"
    }
}
