-- Document module: registered knowledge sources and their metadata.
-- Original binaries are NOT stored here; the storage reference is added with ingestion.
CREATE TABLE documents (
    id           uuid          PRIMARY KEY,
    -- RESTRICT: a workspace that still holds documents cannot be deleted.
    workspace_id uuid          NOT NULL REFERENCES workspaces (id) ON DELETE RESTRICT,
    title        varchar(500)  NOT NULL,
    source_type  varchar(32)   NOT NULL,
    media_type   varchar(255),
    origin       varchar(2048),
    status       varchar(32)   NOT NULL,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT documents_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT documents_source_type_valid CHECK (source_type IN ('FILE', 'URL')),
    CONSTRAINT documents_status_valid CHECK (status IN ('REGISTERED'))
);

-- Every read is workspace-scoped and listed newest first.
CREATE INDEX idx_documents_workspace_created_at ON documents (workspace_id, created_at DESC);
