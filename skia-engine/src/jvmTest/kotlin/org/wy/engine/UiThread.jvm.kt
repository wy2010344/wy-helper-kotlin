package org.wy.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

internal actual fun runOnUiThread(block: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) {
        block()
        return
    }
    val latch = CountDownLatch(1)
    SwingUtilities.invokeAndWait {
        try {
            block()
        } finally {
            latch.countDown()
        }
    }
    latch.await(5, TimeUnit.SECONDS)
}
