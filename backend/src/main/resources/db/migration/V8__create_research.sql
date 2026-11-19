-- Research module: research runs (sessions), their plans, tool activity and gathered sources.
-- Every row belongs to exactly one workspace; deleting the workspace deletes its research.

CREATE TABLE research_runs (
    id               uuid          PRIMARY KEY,
    workspace_id     uuid          NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    objective        text          NOT NULL,
    status           varchar(32)   NOT NULL,
    status_detail    varchar(1000),
    cancel_requested boolean       NOT NULL DEFAULT false,
    resume_count     integer       NOT NULL DEFAULT 0,  -- automatic/manual resumptions (bounded)
    plan_origin      varchar(16),                       -- MODEL or FALLBACK
    model            varchar(200),                      -- generation model used
    report_title     varchar(500),
    report_text      text,                              -- verified report with [n] markers
    report_partial   boolean,                           -- the model said the evidence only partly covers the objective
    report_gaps      text,                              -- newline-separated gaps reported by the model
    tool_call_count  integer       NOT NULL DEFAULT 0,
    source_count     integer       NOT NULL DEFAULT 0,
    started_at       timestamptz,
    finished_at      timestamptz,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT research_runs_objective_not_blank CHECK (btrim(objective) <> ''),
    CONSTRAINT research_runs_status_valid CHECK (status IN
        ('QUEUED', 'PLANNING', 'RUNNING', 'SYNTHESIZING', 'COMPLETED', 'INSUFFICIENT_EVIDENCE', 'FAILED', 'CANCELLED')),
    CONSTRAINT research_runs_plan_origin_valid CHECK (plan_origin IS NULL OR plan_origin IN ('MODEL', 'FALLBACK')),
    CONSTRAINT research_runs_report_only_when_completed CHECK (report_text IS NULL OR status = 'COMPLETED'),
    CONSTRAINT research_runs_counts_non_negative CHECK (tool_call_count >= 0 AND source_count >= 0 AND resume_count >= 0),
    CONSTRAINT research_runs_id_workspace_unique UNIQUE (id, workspace_id)
);
CREATE INDEX idx_research_runs_workspace_created ON research_runs (workspace_id, created_at DESC);
CREATE INDEX idx_research_runs_active ON research_runs (status)
    WHERE status IN ('QUEUED', 'PLANNING', 'RUNNING', 'SYNTHESIZING');

-- The plan: ordered steps, each with the search queries the planner proposed.
CREATE TABLE research_steps (
    id              uuid          PRIMARY KEY,
    run_id          uuid          NOT NULL,
    workspace_id    uuid          NOT NULL,
    ordinal         integer       NOT NULL,
    title           varchar(300)  NOT NULL,
    goal            varchar(1000) NOT NULL,
    planned_queries text          NOT NULL,             -- newline-separated
    status          varchar(16)   NOT NULL,
    status_detail   varchar(1000),
    action_count    integer       NOT NULL DEFAULT 0,
    started_at      timestamptz,
    finished_at     timestamptz,
    CONSTRAINT research_steps_run_fk FOREIGN KEY (run_id, workspace_id)
        REFERENCES research_runs (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT research_steps_ordinal_unique UNIQUE (run_id, ordinal),
    CONSTRAINT research_steps_id_run_unique UNIQUE (id, run_id),
    CONSTRAINT research_steps_ordinal_positive CHECK (ordinal >= 1),
    CONSTRAINT research_steps_status_valid CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'SKIPPED'))
);

-- Audit trail of every tool invocation the agent attempted, including rejected ones.
CREATE TABLE research_tool_calls (
    id             uuid          PRIMARY KEY,
    run_id         uuid          NOT NULL,
    step_id        uuid          NOT NULL,
    workspace_id   uuid          NOT NULL,
    seq            integer       NOT NULL,              -- order within the run
    tool           varchar(64)   NOT NULL,
    arguments      text          NOT NULL,              -- JSON as proposed (data, never executed)
    reason         varchar(1000),                       -- the model's stated reason, or the fallback rule
    status         varchar(16)   NOT NULL,
    result_summary varchar(2000),
    error          varchar(1000),
    duration_ms    integer       NOT NULL DEFAULT 0,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT research_tool_calls_run_fk FOREIGN KEY (run_id, workspace_id)
        REFERENCES research_runs (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT research_tool_calls_step_fk FOREIGN KEY (step_id, run_id)
        REFERENCES research_steps (id, run_id) ON DELETE CASCADE,
    CONSTRAINT research_tool_calls_seq_unique UNIQUE (run_id, seq),
    CONSTRAINT research_tool_calls_status_valid CHECK (status IN ('SUCCEEDED', 'FAILED', 'REJECTED'))
);

-- Sources gathered during a run: each retrieved passage once, with full provenance and a
-- snapshot of its text. "handle" (P1, P2, ...) is how the agent refers to it.
CREATE TABLE research_sources (
    id             uuid             PRIMARY KEY,
    run_id         uuid             NOT NULL,
    workspace_id   uuid             NOT NULL,
    handle         varchar(16)      NOT NULL,
    step_id        uuid             NOT NULL,
    tool_call_seq  integer          NOT NULL,           -- the tool call that first retrieved it
    document_id    uuid             NOT NULL,
    chunk_id       uuid             NOT NULL,
    document_title varchar(500)     NOT NULL,
    page_number    integer,
    chunk_index    integer          NOT NULL,
    char_start     integer          NOT NULL,
    char_end       integer          NOT NULL,
    content        text             NOT NULL,
    score          double precision,                    -- retrieval similarity; NULL for context reads
    created_at     timestamptz      NOT NULL DEFAULT now(),
    CONSTRAINT research_sources_run_fk FOREIGN KEY (run_id, workspace_id)
        REFERENCES research_runs (id, workspace_id) ON DELETE CASCADE,
    CONSTRAINT research_sources_step_fk FOREIGN KEY (step_id, run_id)
        REFERENCES research_steps (id, run_id) ON DELETE CASCADE,
    CONSTRAINT research_sources_chunk_unique UNIQUE (run_id, chunk_id),
    CONSTRAINT research_sources_handle_unique UNIQUE (run_id, handle),
    CONSTRAINT research_sources_handle_format CHECK (handle ~ '^P[0-9]{1,4}$')
);

-- Citation module: a citation now belongs to either a chat answer or a research report.
ALTER TABLE citations ALTER COLUMN message_id DROP NOT NULL;
ALTER TABLE citations ADD COLUMN research_run_id uuid;
ALTER TABLE citations ADD CONSTRAINT citations_research_run_fk FOREIGN KEY (research_run_id, workspace_id)
    REFERENCES research_runs (id, workspace_id) ON DELETE CASCADE;
ALTER TABLE citations ADD CONSTRAINT citations_exactly_one_owner
    CHECK (num_nonnulls(message_id, research_run_id) = 1);
ALTER TABLE citations ADD CONSTRAINT citations_research_ordinal_unique UNIQUE (research_run_id, ordinal);
CREATE INDEX idx_citations_research_run ON citations (research_run_id);
