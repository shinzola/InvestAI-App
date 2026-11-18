-- =====================================================================
-- InvestIA — V5: planejamento de gastos e natureza das entradas
-- =====================================================================

-- Natureza da receita (salário, aporte, estimativa, feriado remunerado...)
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS income_kind VARCHAR(30) NULL;

-- Gastos planejados do mês: fixos ou individuais, com data de vencimento
CREATE TABLE IF NOT EXISTS planned_expenses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    description VARCHAR(255) NOT NULL,
    amount      NUMERIC(18,2) NOT NULL CHECK (amount >= 0),
    category_id UUID REFERENCES categories (id) ON DELETE SET NULL,
    due_date    DATE         NOT NULL,
    kind        VARCHAR(12)  NOT NULL DEFAULT 'VARIABLE', -- FIXED | VARIABLE
    status      VARCHAR(12)  NOT NULL DEFAULT 'PLANNED',  -- PLANNED | PAID
    notes       VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_planned_user_due ON planned_expenses (user_id, due_date);
