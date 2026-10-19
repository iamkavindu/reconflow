CREATE SCHEMA IF NOT EXISTS ai;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE ai.knowledge_documents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    kind VARCHAR(30) NOT NULL CHECK (kind IN ('POLICY', 'PROVIDER_DOCUMENTATION', 'PAST_CASE')),
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL CHECK (length(content) BETWEEN 1 AND 20000),
    digest VARCHAR(64) NOT NULL,
    imported_by VARCHAR(255) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, digest),
    UNIQUE (tenant_id, id)
);

CREATE TABLE ai.knowledge_chunks (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL,
    embedding vector(1536) NOT NULL,
    tenant_id UUID GENERATED ALWAYS AS ((metadata->>'tenantId')::UUID) STORED NOT NULL,
    document_id UUID GENERATED ALWAYS AS ((metadata->>'documentId')::UUID) STORED NOT NULL,
    FOREIGN KEY (tenant_id, document_id) REFERENCES ai.knowledge_documents (tenant_id, id),
    CHECK (length(content) BETWEEN 1 AND 2000)
);

CREATE INDEX idx_knowledge_chunk_metadata ON ai.knowledge_chunks USING GIN (metadata);
CREATE INDEX idx_knowledge_document_tenant ON ai.knowledge_documents (tenant_id, imported_at DESC);

CREATE FUNCTION ai.reject_knowledge_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'knowledge evidence is immutable; import a new document version';
END;
$$;

CREATE TRIGGER immutable_knowledge_documents BEFORE UPDATE OR DELETE
    ON ai.knowledge_documents FOR EACH ROW EXECUTE FUNCTION ai.reject_knowledge_mutation();
CREATE TRIGGER immutable_knowledge_chunks BEFORE UPDATE OR DELETE
    ON ai.knowledge_chunks FOR EACH ROW EXECUTE FUNCTION ai.reject_knowledge_mutation();
CREATE TRIGGER no_truncate_knowledge_documents BEFORE TRUNCATE
    ON ai.knowledge_documents FOR EACH STATEMENT EXECUTE FUNCTION ai.reject_knowledge_mutation();
CREATE TRIGGER no_truncate_knowledge_chunks BEFORE TRUNCATE
    ON ai.knowledge_chunks FOR EACH STATEMENT EXECUTE FUNCTION ai.reject_knowledge_mutation();
