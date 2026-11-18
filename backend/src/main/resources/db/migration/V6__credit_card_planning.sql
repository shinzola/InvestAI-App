-- =====================================================================
-- InvestIA — V6: gastos no cartão de crédito e limite utilizado
-- =====================================================================

-- Gasto planejado pode ser de cartão de crédito, com fechamento e
-- vencimento da fatura próprios (independentes da data da compra).
ALTER TABLE planned_expenses
    ADD COLUMN IF NOT EXISTS card_account_id      UUID REFERENCES accounts (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS invoice_closing_date DATE NULL,
    ADD COLUMN IF NOT EXISTS invoice_due_date     DATE NULL;

CREATE INDEX IF NOT EXISTS ix_planned_card ON planned_expenses (card_account_id);

-- Limite já utilizado do cartão, informado pelo usuário
ALTER TABLE credit_cards
    ADD COLUMN IF NOT EXISTS limit_used NUMERIC(18,2) NOT NULL DEFAULT 0;
