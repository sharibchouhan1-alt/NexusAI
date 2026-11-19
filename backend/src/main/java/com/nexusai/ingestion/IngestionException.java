package com.nexusai.ingestion;

/**
 * An expected ingestion failure. The message is user-facing: it is stored as the
 * document's status detail, so it must not contain internal paths or stack details.
 */
class IngestionException extends Exception {

    IngestionException(String userMessage) {
        super(userMessage);
    }

    IngestionException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
