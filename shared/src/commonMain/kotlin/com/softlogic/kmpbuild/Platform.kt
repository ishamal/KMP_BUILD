package com.softlogic.kmpbuild

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform