package org.wy.mve

import kotlin.test.assertNotNull

class ContextTest {

    @kotlin.test.Test
    fun context_value() {
        val ctx = com.wy.mve.Context<String>("hello")
        assertNotNull(ctx)
        assertNotNull(ctx.value)
    }
}