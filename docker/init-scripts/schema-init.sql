-- Schema initialization for the Document Management Service.
-- Executed automatically by the bitnami/postgresql image on first boot
-- (files under /docker-entrypoint-initdb.d are run in alphabetical order).

CREATE SCHEMA IF NOT EXISTS document_schema;
SET SCHEMA 'document_schema';

CREATE TABLE IF NOT EXISTS documents (
    id           UUID PRIMARY KEY,
    user_name    VARCHAR(255)  NOT NULL,
    name         VARCHAR(512)  NOT NULL,
    minio_path   VARCHAR(1024) NOT NULL,
    size_bytes   BIGINT,
    content_type VARCHAR(127),
    status       VARCHAR(32)   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);


CREATE INDEX IF NOT EXISTS idx_documents_user ON documents (user_name);

CREATE TABLE IF NOT EXISTS document_tags (
    document_id UUID         NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tag         VARCHAR(128) NOT NULL,
    PRIMARY KEY (document_id, tag)
);

CREATE INDEX IF NOT EXISTS idx_document_tags_tag ON document_tags (tag);
