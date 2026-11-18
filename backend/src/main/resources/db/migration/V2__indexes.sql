-- V2: idempotência defensiva — índice GIN para busca por tags (JSONB)
-- e índice complementar de transações por conta.
CREATE INDEX IF NOT EXISTS ix_transactions_tags_gin ON transactions USING GIN (tags);
CREATE INDEX IF NOT EXISTS ix_transactions_user_account ON transactions (user_id, account_id, date);
