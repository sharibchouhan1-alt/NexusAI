package com.nexusai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NexusAI backend — a single deployable modular monolith.
 *
 * <p>Business modules (auth, user, workspace, document, ingestion, search, rag, ...) are added
 * as top-level packages under {@code com.nexusai} when each is implemented. Cross-cutting
 * platform concerns live under {@code com.nexusai.platform}.
 */
@SpringBootApplication
public class NexusAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusAiApplication.class, args);
    }
}
