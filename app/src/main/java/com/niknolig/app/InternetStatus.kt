package com.niknolig.app

internal enum class InternetStatus(val messageResource: Int) {
    OFFLINE(R.string.internet_status_offline),
    LOCAL_ONLY(R.string.internet_status_local_only),
    UNVERIFIED(R.string.internet_status_unverified),
    ONLINE(R.string.internet_status_online),
    UNKNOWN(R.string.internet_status_unknown)
}

internal object InternetStatusClassifier {
    fun classify(
        networkAvailable: Boolean,
        capabilitiesAvailable: Boolean,
        hasInternetCapability: Boolean,
        internetValidated: Boolean
    ): InternetStatus = when {
        !networkAvailable -> InternetStatus.OFFLINE
        !capabilitiesAvailable -> InternetStatus.UNKNOWN
        !hasInternetCapability -> InternetStatus.LOCAL_ONLY
        !internetValidated -> InternetStatus.UNVERIFIED
        else -> InternetStatus.ONLINE
    }
}
