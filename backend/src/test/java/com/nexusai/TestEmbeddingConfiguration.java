package com.nexusai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the Ollama embedding model with {@link TestEmbeddingModel} in API tests. */
@TestConfiguration(proxyBeanMethods = false)
public class TestEmbeddingConfiguration {

    @Bean
    @Primary
    TestEmbeddingModel testEmbeddingModel() {
        return new TestEmbeddingModel();
    }
}
