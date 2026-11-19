package com.nexusai.ingestion;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(IngestionProperties.class)
class IngestionConfig {

    static final String EXECUTOR = "ingestionExecutor";

    /** Bounded pool + queue: a burst of uploads cannot exhaust threads or memory. */
    @Bean(EXECUTOR)
    ThreadPoolTaskExecutor ingestionExecutor(IngestionProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ingestion-");
        executor.setCorePoolSize(properties.workerThreads());
        executor.setMaxPoolSize(properties.workerThreads());
        executor.setQueueCapacity(properties.queueCapacity());
        // Interrupted work stays PROCESSING and is resumed by startup recovery.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
