package com.ssegning.keycloak.keybound.examples.backend.controller

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class StoreDashboardSchedulerConfig {
    @Bean
    fun storeDashboardScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 2
        scheduler.threadNamePrefix = "store-dashboard-stream-"
        scheduler.initialize()
        return scheduler
    }
}
