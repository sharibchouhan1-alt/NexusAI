package com.nexusai.research;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(ResearchProperties.class)
class ResearchConfig {

    static final String EXECUTOR = "researchExecutor";

    /** Research is slow and model-bound: a small, bounded pool separate from ingestion. */
    @Bean(EXECUTOR)
    ThreadPoolTaskExecutor researchExecutor(ResearchProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("research-");
        executor.setCorePoolSize(properties.workerThreads());
        executor.setMaxPoolSize(properties.workerThreads());
        executor.setQueueCapacity(properties.queueCapacity());
        // Interrupted runs stay active in the database and are resumed on startup.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
