-- Workspace module: the isolation boundary for all knowledge.
CREATE TABLE workspaces (
    id          uuid          PRIMARY KEY,
    name        varchar(200)  NOT NULL,
    description varchar(2000),
    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT workspaces_name_not_blank CHECK (btrim(name) <> '')
);

CREATE INDEX idx_workspaces_created_at ON workspaces (created_at);
