package com.droidlink.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceConnectionTest {

    @Test
    fun `address returns host colon port`() {
        val connection = DeviceConnection(host = "192.168.1.100", port = 5555)
        assertEquals("192.168.1.100:5555", connection.address)
    }

    @Test
    fun `default values are set correctly`() {
        val connection = DeviceConnection(host = "10.0.0.1", port = 5555)
        assertEquals(0L, connection.id)
        assertEquals("", connection.name)
        assertEquals(false, connection.isConnected)
    }
}
