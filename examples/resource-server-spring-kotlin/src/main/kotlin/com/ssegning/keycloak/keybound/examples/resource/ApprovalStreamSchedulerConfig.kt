package com.ssegning.keycloak.keybound.examples.resource

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class ApprovalStreamSchedulerConfig {
    @Bean
    fun approvalStreamScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 2
        scheduler.threadNamePrefix = "approval-stream-"
        scheduler.initialize()
        return scheduler
    }
}
