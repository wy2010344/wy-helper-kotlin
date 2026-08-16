package org.wy.demo.helper

import com.wy.layout.AlignItem
import com.wy.layout.DirectionJustify
import com.wy.mve.StateHolder
import com.wy.mve.StateHolderWithNode
import org.wy.engine.*
import org.wy.engine.helper.slider
import org.wy.engine.layout.FlexObject
import org.wy.engine.layout.FlexParam
import org.wy.engine.layout.LayoutDirection
import org.wy.signal.createSignal
import org.wy.signal.getValue
import org.wy.signal.setValue

fun main() {
    object : SkiaApp(720, 420), FlexParam {
        override val layout: LayoutDirection = FlexObject(this)
        override val directionJustify: DirectionJustify get() = DirectionJustify.start
        override val alignFix: Boolean get() = true
        override val alignItem: AlignItem get() = AlignItem.stretch
        override val gap: Float get() = 8f

        override fun StateHolderWithNode<Node, List<Node>>.argChildren() {
            page {
                sectionTitle("Slider 滑杆")
                hint("按下拖动取值；聚焦后 ← / → 步进 0.05。")

                var volume by createSignal(0.6f)
                var brightness by createSignal(0.3f)
                var opacity by createSignal(0f)

                row { slider({ volume }, { volume = it }); hint("音量 ${(volume * 100).toInt()}%") }
                row { slider({ brightness }, { brightness = it }); hint("亮度 ${(brightness * 100).toInt()}%") }
                row { slider({ opacity }, { opacity = it }); hint("透明度 ${(opacity * 100).toInt()}%") }
                row { slider({ 0.8f }, {}, enabled = false); hint("不可用") }
            }
        }
    }
}
