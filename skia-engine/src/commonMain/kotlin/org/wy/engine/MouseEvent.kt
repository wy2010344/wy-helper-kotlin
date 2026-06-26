package org.wy.engine

class MouseEvent(val x: Float,val y: Float) {
    var stoppedProgression=false
        private set

    fun stopPropagation(){
        stoppedProgression=true
    }
}