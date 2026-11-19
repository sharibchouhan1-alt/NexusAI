package com.nexusai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the Ollama embedding and chat models with deterministic test doubles in API
 * tests ({@link TestEmbeddingModel}, {@link TestChatModel}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestEmbeddingConfiguration {

    @Bean
    @Primary
    TestEmbeddingModel testEmbeddingModel() {
        return new TestEmbeddingModel();
    }

    @Bean
    @Primary
    TestChatModel testChatModel() {
        return new TestChatModel();
    }
}
