-- Document module: uploaded originals and the ingestion lifecycle.
-- The binary lives in file storage; the row holds only a reference and metadata.
ALTER TABLE documents
    ADD COLUMN storage_key     varchar(512),
    ADD COLUMN file_size_bytes bigint,
    ADD COLUMN content_sha256  varchar(64),
    ADD COLUMN status_detail   varchar(1000),
    ADD COLUMN page_count      integer,
    ADD COLUMN chunk_count     integer;

ALTER TABLE documents DROP CONSTRAINT documents_status_valid;
ALTER TABLE documents ADD CONSTRAINT documents_status_valid
    CHECK (status IN ('REGISTERED', 'PENDING', 'PROCESSING', 'READY', 'FAILED'));

-- Metadata-only documents have no file; every other state is about a stored file.
ALTER TABLE documents ADD CONSTRAINT documents_file_matches_status
    CHECK ((status = 'REGISTERED') = (storage_key IS NULL));
ALTER TABLE documents ADD CONSTRAINT documents_file_fields_complete
    CHECK (storage_key IS NULL OR (file_size_bytes IS NOT NULL AND content_sha256 IS NOT NULL));
ALTER TABLE documents ADD CONSTRAINT documents_sha256_format
    CHECK (content_sha256 ~ '^[0-9a-f]{64}$');
ALTER TABLE documents ADD CONSTRAINT documents_counts_non_negative
    CHECK (file_size_bytes >= 0 AND page_count >= 0 AND chunk_count >= 0);

ALTER TABLE documents ADD CONSTRAINT documents_storage_key_unique UNIQUE (storage_key);

-- Lets derived tables reference (document, workspace) together, so a chunk can never be
-- attached to a document of a different workspace.
ALTER TABLE documents ADD CONSTRAINT documents_id_workspace_unique UNIQUE (id, workspace_id);

-- Startup recovery looks up unfinished ingestion work.
CREATE INDEX idx_documents_status_unfinished ON documents (status)
    WHERE status IN ('PENDING', 'PROCESSING');
