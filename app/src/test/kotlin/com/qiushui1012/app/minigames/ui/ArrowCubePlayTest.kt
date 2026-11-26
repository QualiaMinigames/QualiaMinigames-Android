package com.qiushui1012.app.minigames.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ArrowCubePlayTest {
    @Test
    fun a_flight_that_stays_on_screen_fades_over_the_last_500ms() {
        assertEquals(1f, flightFadeAlpha(2_999f), 1e-6f)
        assertEquals(1f, flightFadeAlpha(3_000f), 1e-6f)
        assertEquals(0.5f, flightFadeAlpha(3_250f), 1e-6f)
        assertEquals(0f, flightFadeAlpha(3_500f), 1e-6f)
        assertEquals(0f, flightFadeAlpha(4_000f), 1e-6f)
    }
}
