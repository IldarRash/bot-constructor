package com.example.botconstructor.services

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class LocalServiceRegistry {
    val isUpdate = AtomicBoolean(true)

    private val cacheClientApi = Caffeine.newBuilder()
        .maximumSize(10)
        .softValues()
        .build<String, Service>()

    private val cacheBotAnswer = Caffeine.newBuilder()
        .maximumSize(10)
        .softValues()
        .build<String, Service>()


    fun add(service: Service) {
        isUpdate.set(true)
        when (service.serviceType) {
            ServiceType.CLIENT_API -> cacheClientApi.put(service.id, service)
            ServiceType.BOT_CREATOR_ANSWER -> cacheBotAnswer.put(service.id, service)
            else -> throw RuntimeException("Service type dont found")
        }
    }

    fun getInstants(type: ServiceType) = when (type) {
        ServiceType.CLIENT_API -> cacheClientApi.asMap().values
        ServiceType.BOT_CREATOR_ANSWER -> cacheBotAnswer.asMap().values
        else -> throw RuntimeException("Service type dont found")
    }
}


