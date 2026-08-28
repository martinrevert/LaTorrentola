package com.martinrevert.latorrentola.database

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.Date

class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    @Test
    fun `test date conversion`() {
        val now = Date()
        val timestamp = converters.dateToTimestamp(now)
        val result = converters.fromTimestamp(timestamp)

        assertThat(result?.time).isEqualTo(now.time)
    }

    @Test
    fun `test null conversions`() {
        assertThat(converters.fromTimestamp(null)).isNull()
        assertThat(converters.dateToTimestamp(null)).isNull()
    }
}
