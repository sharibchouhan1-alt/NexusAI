-- Document module: search-index lifecycle of an ingested document (reported by ingestion).
-- NULL until the document's chunks exist (status READY).
ALTER TABLE documents
    ADD COLUMN index_status varchar(32),
    ADD COLUMN index_detail varchar(1000);
ALTER TABLE documents ADD CONSTRAINT documents_index_status_valid
    CHECK (index_status IN ('PENDING', 'INDEXING', 'INDEXED', 'FAILED'));
ALTER TABLE documents ADD CONSTRAINT documents_index_requires_ready
    CHECK (index_status IS NULL OR status = 'READY');

-- Backfill: every document ingested before embeddings existed is queued for indexing.
UPDATE documents SET index_status = 'PENDING' WHERE status = 'READY';

CREATE INDEX idx_documents_index_unfinished ON documents (index_status)
    WHERE index_status IN ('PENDING', 'INDEXING');

-- Ingestion module: vector representations of chunks. Kept apart from document_chunks so a
-- change of embedding model never requires re-deriving chunks; each row records its model.
ALTER TABLE document_chunks ADD CONSTRAINT document_chunks_id_document_workspace_unique
    UNIQUE (id, document_id, workspace_id);

CREATE TABLE chunk_embeddings (
    chunk_id     uuid         NOT NULL,
    document_id  uuid         NOT NULL,
    workspace_id uuid         NOT NULL,
    model        varchar(200) NOT NULL, -- e.g. 'ollama:nomic-embed-text'
    embedding    vector(768)  NOT NULL, -- dimension of nomic-embed-text; see nexusai.embedding.dimensions
    created_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT chunk_embeddings_pk PRIMARY KEY (chunk_id, model),
    -- The embedding always belongs to the same document and workspace as its chunk, and
    -- disappears with it (re-ingestion replaces chunks; deleting a document cascades).
    CONSTRAINT chunk_embeddings_chunk_fk FOREIGN KEY (chunk_id, document_id, workspace_id)
        REFERENCES document_chunks (id, document_id, workspace_id) ON DELETE CASCADE
);

-- Approximate nearest-neighbour search by cosine distance. Queries filter by workspace and
-- model and enable pgvector iterative index scans so filtering cannot starve results.
CREATE INDEX idx_chunk_embeddings_hnsw ON chunk_embeddings USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_chunk_embeddings_workspace_model ON chunk_embeddings (workspace_id, model);
CREATE INDEX idx_chunk_embeddings_document ON chunk_embeddings (document_id, model);
