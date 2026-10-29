package org.wy.unknownthis

interface AInter {
    fun call1(){
        println("call from interface")
    }
    fun call2(){
call1()
    }
}
open class A1{

    open fun call2(){
        println("call from super a1")
    }
    open fun call1(){
call2()
    }
}
fun main(){
    println("dd")
    val a1=object : A1() {
        override fun call1() {
            println("ff")
            super.call1()
        }

        override fun call2() {
            println("call from obj")
        }
    }
    //会死循环
    a1.call1()
    val ai=object : AInter{
        override fun call1() {
            println("call from inter")
        }
        override fun call2() {
            println("dd")
            super.call2()
        }
    }
    ai.call2()
}