package com.niknolig.app

import org.junit.Assert.assertEquals
import org.junit.Test

class InternetStatusTest {
    @Test
    fun `classifies network and validated internet states`() {
        assertEquals(
            InternetStatus.OFFLINE,
            InternetStatusClassifier.classify(false, false, false, false)
        )
        assertEquals(
            InternetStatus.UNKNOWN,
            InternetStatusClassifier.classify(true, false, false, false)
        )
        assertEquals(
            InternetStatus.LOCAL_ONLY,
            InternetStatusClassifier.classify(true, true, false, false)
        )
        assertEquals(
            InternetStatus.UNVERIFIED,
            InternetStatusClassifier.classify(true, true, true, false)
        )
        assertEquals(
            InternetStatus.ONLINE,
            InternetStatusClassifier.classify(true, true, true, true)
        )
    }
}
