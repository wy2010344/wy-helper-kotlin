package org.wy.engine

import org.wy.signal.addEffect
import org.wy.signal.batchSignalEnd
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 信号批次效果系统回归测试：效果执行期间重新注册的效果应排到下一批，
 * 由下一次真实批次消费（供浮层组件的"常驻效果"使用），且不会在本批内无限循环。
 */
class SignalEffectTest {

    @Test
    fun residentEffectCatchesStateFlipInNextBatch() {
        var open = true
        var runs = 0
        var lastSeen: Boolean? = null
        lateinit var eff: () -> Unit
        eff = {
            runs++
            lastSeen = open
            if (open) {
                addEffect(1, eff)
            }
        }

        addEffect(0, eff)
        batchSignalEnd()
        assertEquals(1, runs, "打开批只应运行一次，不应同批内无限循环")
        assertEquals(true, lastSeen)

        // 关闭状态翻转后触发一批：常驻效果应捕获到并完成收尾
        open = false
        addEffect(0) {}
        batchSignalEnd()
        assertEquals(false, lastSeen, "常驻效果应捕获到关闭翻转")
        assertEquals(2, runs, "关闭批只运行一次")
    }
}
