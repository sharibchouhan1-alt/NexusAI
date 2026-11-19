-- Chat module: conversations and their ordered messages. A conversation belongs to exactly
-- one workspace; deleting the workspace deletes its conversations (documents still block it).
CREATE TABLE conversations (
    id           uuid         PRIMARY KEY,
    workspace_id uuid         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    title        varchar(200) NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT conversations_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT conversations_id_workspace_unique UNIQUE (id, workspace_id)
);
CREATE INDEX idx_conversations_workspace_updated ON conversations (workspace_id, updated_at DESC);

CREATE TABLE messages (
    id              uuid          PRIMARY KEY,
    conversation_id uuid          NOT NULL,
    workspace_id    uuid          NOT NULL,
    seq             integer       NOT NULL,          -- turn order within the conversation
    role            varchar(16)   NOT NULL,
    content         text          NOT NULL,
    status          varchar(16)   NOT NULL,
    status_detail   varchar(1000),                    -- why an answer abstained or failed
    model           varchar(200),                     -- generation model of an assistant answer
    retrieved_count integer,                          -- passages given to the model as evidence
    latency_ms      integer,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT messages_conversation_fk FOREIGN KEY (conversation_id, workspace_id)
        REFERENCES conversations (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT messages_seq_unique UNIQUE (conversation_id, seq),
    CONSTRAINT messages_id_workspace_unique UNIQUE (id, workspace_id),
    CONSTRAINT messages_role_valid CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT messages_status_valid CHECK (status IN ('COMPLETE', 'PENDING', 'ABSTAINED', 'FAILED')),
    CONSTRAINT messages_user_complete CHECK (role = 'ASSISTANT' OR status = 'COMPLETE'),
    CONSTRAINT messages_seq_non_negative CHECK (seq >= 0)
);
CREATE INDEX idx_messages_pending ON messages (status) WHERE status = 'PENDING';

-- Citation module: durable evidence links from an answer to the passages that support it.
-- Each row snapshots the cited passage, so the answer stays auditable even if the source
-- document is later re-ingested or deleted (document/chunk ids are kept as plain references).
CREATE TABLE citations (
    id             uuid             PRIMARY KEY,
    message_id     uuid             NOT NULL,
    workspace_id   uuid             NOT NULL,
    ordinal        integer          NOT NULL,          -- the [n] marker number in the answer
    source_kind    varchar(32)      NOT NULL DEFAULT 'DOCUMENT_CHUNK',
    document_id    uuid             NOT NULL,
    chunk_id       uuid             NOT NULL,
    document_title varchar(500)     NOT NULL,
    page_number    integer,
    chunk_index    integer          NOT NULL,
    char_start     integer          NOT NULL,
    char_end       integer          NOT NULL,
    quote          text             NOT NULL,          -- verified to occur in the passage
    passage        text             NOT NULL,          -- the retrieved chunk text at answer time
    score          double precision NOT NULL,          -- retrieval similarity
    created_at     timestamptz      NOT NULL DEFAULT now(),
    CONSTRAINT citations_message_fk FOREIGN KEY (message_id, workspace_id)
        REFERENCES messages (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT citations_ordinal_unique UNIQUE (message_id, ordinal),
    CONSTRAINT citations_ordinal_positive CHECK (ordinal >= 1),
    CONSTRAINT citations_source_kind_valid CHECK (source_kind IN ('DOCUMENT_CHUNK')),
    CONSTRAINT citations_quote_not_empty CHECK (length(quote) > 0),
    CONSTRAINT citations_page_positive CHECK (page_number IS NULL OR page_number >= 1)
);
CREATE INDEX idx_citations_message ON citations (message_id);
CREATE INDEX idx_citations_document ON citations (workspace_id, document_id);
