-- Ingestion module: retrievable units of a document, with provenance for citation.
-- Embeddings are a separate concern and are not stored here.
CREATE TABLE document_chunks (
    id           uuid        PRIMARY KEY,
    document_id  uuid        NOT NULL,
    workspace_id uuid        NOT NULL,
    chunk_index  integer     NOT NULL,
    page_number  integer,             -- 1-based PDF page; NULL for unpaged sources (TXT)
    char_start   integer     NOT NULL, -- offsets into the extracted text of that page/source
    char_end     integer     NOT NULL,
    content      text        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    -- Deleting a document removes its derived chunks.
    CONSTRAINT document_chunks_document_fk FOREIGN KEY (document_id, workspace_id)
        REFERENCES documents (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT document_chunks_index_unique UNIQUE (document_id, chunk_index),
    CONSTRAINT document_chunks_index_non_negative CHECK (chunk_index >= 0),
    CONSTRAINT document_chunks_page_positive CHECK (page_number IS NULL OR page_number >= 1),
    CONSTRAINT document_chunks_offsets_valid CHECK (char_start >= 0 AND char_end > char_start),
    CONSTRAINT document_chunks_content_not_empty CHECK (length(content) > 0)
);

-- Retrieval is always workspace-scoped.
CREATE INDEX idx_document_chunks_workspace_document ON document_chunks (workspace_id, document_id);
