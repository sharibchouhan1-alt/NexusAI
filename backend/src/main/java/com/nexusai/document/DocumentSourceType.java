package com.nexusai.document;

/** Where a knowledge source comes from. */
public enum DocumentSourceType {
    /** An uploaded file (PDF, DOCX, image, ...). Upload itself is not implemented yet. */
    FILE,
    /** A web resource identified by URL. */
    URL
}
