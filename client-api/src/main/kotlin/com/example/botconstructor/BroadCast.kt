package com.example.botconstructor

import reactor.core.publisher.ConnectableFlux
import reactor.core.publisher.Flux

class BroadCast<T> {
    private val broadCast = start<T>()

    private fun <T> start() =
        Flux.empty<T>().publish().refCount()

    fun <T> listener() = broadCast
}
