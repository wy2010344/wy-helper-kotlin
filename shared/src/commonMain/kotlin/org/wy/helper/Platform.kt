package org.wy.helper

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform