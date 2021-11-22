package com.example.botconstructor.model

import org.springframework.messaging.rsocket.RSocketRequester

class Bot(val id: String, val rSocketRequester: RSocketRequester) {
}
