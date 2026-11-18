-- =====================================================================
-- InvestIA — V7: chat do copiloto com ações
-- =====================================================================

CREATE TABLE IF NOT EXISTS chat_messages (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL,
    content    TEXT        NOT NULL,
    actions    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_chat_user_created
    ON chat_messages (user_id, created_at DESC);
