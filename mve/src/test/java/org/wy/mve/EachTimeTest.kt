package org.wy.mve

import com.wy.mve.EachTime
import kotlin.test.assertEquals

class EachTimeTest {

    @kotlin.test.Test
    fun eachTime_index_value() {
        // EachTime 是 interface，通过 anonymous object 实现
        val et: EachTime<String> = object : EachTime<String> {
            override val index: Int = 0
            override val value: String = "test"
        }
        assertEquals(0, et.index)
        assertEquals("test", et.value)
    }
}