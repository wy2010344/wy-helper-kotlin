package org.wy.unknownthis


interface M{

    fun size(): Float{
        return 10f
    }

}
open class A:M{
    init {
        children()
    }
    open fun  children(){

    }
}

open class B():A(){

    fun printSize(){
        println(size())
    }
}

fun main(){

    object : B(){
        override fun size(): Float {
            return 20f
        }

        override fun children() {
            object : A(){
                override fun size(): Float {
                    printSize()
                    return 100f
                }
                override fun children() {
                    println("abc${size()}")
                }
            }
        }
    }
}