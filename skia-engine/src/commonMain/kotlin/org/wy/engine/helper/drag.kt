package org.wy.engine.helper

import com.wy.mve.StateHolder
import org.wy.engine.PointerCapture
import org.wy.engine.PointerEvent
import org.wy.engine.engineGlobalContext

/**
 * 基于指针捕获的拖拽辅助函数。
 *
 * 典型用法：在节点的 `onPointerDown` 中调用——
 * ```kotlin
 * override fun onPointerDown(e: PointerEvent) {
 *     drag(e) { me ->
 *         // me.rootX / me.rootY 为全局坐标
 *     }
 * }
 * ```
 *
 * 捕获指针后，Move 事件持续回调 [change]（全局坐标 rootX/rootY），
 * Up 时回调最后一次并自动结束捕获。
 *
 * 捕获生命周期与宿主 [StateHolder] 绑定：宿主销毁（如列表删除行）时
 * 自动释放捕获，死节点不会再收到拖拽回调。
 */
fun StateHolder<*, *>.drag(down: PointerEvent, change: (e: PointerEvent) -> Unit): PointerCapture {
    val g = consume(engineGlobalContext)!!
    val capture = g.capturePointer(
        id = down.id,
        onMove = change,
        onUp = change
    )
    addDestroy { capture.release() }
    return capture
}
