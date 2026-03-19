package com.geofence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configures a shared ThreadPoolTaskScheduler with 3 threads.
 *
 * Spring Boot's default scheduler uses a single-threaded executor. With multiple
 * @Scheduled methods in this application (SecurityEventService.flush at 500ms,
 * RequestLogService.flush at 1s, RequestLogService.purgeOldLogs daily) a single
 * thread means a slow or blocked task delays all others. Three threads give each
 * recurring task its own slot with one spare for bursts.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }
}
