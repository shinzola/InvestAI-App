-- =====================================================================
-- InvestIA — V8: provedor de LLM configurado pelo usuário
-- =====================================================================

CREATE TABLE IF NOT EXISTS ai_settings (
    user_id    UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    base_url   VARCHAR(255) NOT NULL,
    model      VARCHAR(120) NOT NULL,
    api_key    VARCHAR(512) NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
