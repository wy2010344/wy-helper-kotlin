package org.wy.engine

class MouseEvent(
    val nodeWithPosition: NodeWithPosition,
    val renderer: Renderer,
    val down: Boolean,
    val up: Boolean,
    val move: Boolean,
    val wheel: Float? = null,
    val rootX: Float = 0f,
    val rootY: Float = 0f
) {
    val node: Node get() = nodeWithPosition.node
    val x: Float get() = nodeWithPosition.x
    val y: Float get() = nodeWithPosition.y

    val globalX: Float get() = rootX
    val globalY: Float get() = rootY

    val shift: Boolean get() = renderer.keyboardModifiers.shift
    val buttons: Int get() = renderer.mouseButtons

    var stoppedProgression = false
        private set

    fun stopPropagation() { stoppedProgression = true }
}
