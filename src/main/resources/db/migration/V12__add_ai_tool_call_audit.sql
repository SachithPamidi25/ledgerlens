CREATE TABLE IF NOT EXISTS ai_tool_call_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tool_name VARCHAR(120) NOT NULL,
    arguments JSONB NOT NULL,
    result_status VARCHAR(40) NOT NULL,
    latency_ms BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_tool_call_audit_user_created
ON ai_tool_call_audit(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_tool_call_audit_tool
ON ai_tool_call_audit(tool_name);
