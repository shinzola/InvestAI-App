-- =====================================================================
-- InvestIA — V1: schema inicial
-- Convenções: id UUID, user_id para isolamento multi-tenant,
-- NUMERIC(18,2) para dinheiro, NUMERIC(18,8) para quantidades de ativos.
-- =====================================================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------- Identidade ----------
CREATE TABLE IF NOT EXISTS users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    full_name           VARCHAR(255) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | LOCKED | DELETED
    failed_attempts     INT          NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ  NULL,
    accepted_terms_at   TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_profiles (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    investor_profile        VARCHAR(20)  NOT NULL DEFAULT 'MODERADO', -- CONSERVADOR | MODERADO | ARROJADO
    base_currency           CHAR(3)      NOT NULL DEFAULT 'BRL',
    timezone                VARCHAR(64)  NOT NULL DEFAULT 'America/Sao_Paulo',
    desired_monthly_income  NUMERIC(18,2) NOT NULL DEFAULT 0, -- meta "viver de renda"
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ NULL,
    replaced_by UUID NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE IF NOT EXISTS password_resets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_password_resets_user ON password_resets (user_id);

-- ---------- Contas ----------
CREATE TABLE IF NOT EXISTS accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    type            VARCHAR(30)  NOT NULL, -- CHECKING | SAVINGS | CREDIT_CARD | DIGITAL_WALLET | CASH
    institution     VARCHAR(120),
    initial_balance NUMERIC(18,2) NOT NULL DEFAULT 0,
    color           VARCHAR(9),
    archived        BOOLEAN      NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_accounts_user ON accounts (user_id);

CREATE TABLE IF NOT EXISTS credit_cards (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id   UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    brand        VARCHAR(40),
    closing_day  SMALLINT NOT NULL DEFAULT 1 CHECK (closing_day BETWEEN 1 AND 28),
    due_day      SMALLINT NOT NULL DEFAULT 10 CHECK (due_day BETWEEN 1 AND 28),
    limit_total  NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- Categorias ----------
CREATE TABLE IF NOT EXISTS categories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(120) NOT NULL,
    kind       VARCHAR(10)  NOT NULL, -- INCOME | EXPENSE
    parent_id  UUID REFERENCES categories (id) ON DELETE CASCADE, -- subcategoria
    color      VARCHAR(9),
    icon       VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_category UNIQUE (user_id, name, kind)
);

-- ---------- Transações ----------
CREATE TABLE IF NOT EXISTS transactions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id           UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    destination_account_id UUID REFERENCES accounts (id) ON DELETE SET NULL, -- transferência
    category_id          UUID REFERENCES categories (id) ON DELETE SET NULL,
    subcategory_id       UUID REFERENCES categories (id) ON DELETE SET NULL,
    recurring_rule_id    UUID, -- FK adiada em V2 (referência circular com recurring_rules)
    type                 VARCHAR(12)  NOT NULL, -- INCOME | EXPENSE | TRANSFER
    amount               NUMERIC(18,2) NOT NULL CHECK (amount >= 0),
    date                 DATE         NOT NULL,
    description          VARCHAR(255) NOT NULL,
    notes                TEXT,
    tags                 JSONB        NOT NULL DEFAULT '[]',
    status               VARCHAR(12)  NOT NULL DEFAULT 'CLEARED', -- PENDING | CLEARED
    source               VARCHAR(12)  NOT NULL DEFAULT 'MANUAL',  -- MANUAL | OFX | CSV | RECURRENT
    installment_group    UUID NULL,
    installment_number   SMALLINT NULL,
    installment_total    SMALLINT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_transactions_user_date ON transactions (user_id, date DESC);
CREATE INDEX IF NOT EXISTS ix_transactions_user_category ON transactions (user_id, category_id);

CREATE TABLE IF NOT EXISTS transaction_attachments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    transaction_id UUID NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(120),
    url            TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS recurring_rules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    account_id  UUID NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    category_id UUID REFERENCES categories (id) ON DELETE SET NULL,
    type        VARCHAR(12)   NOT NULL,
    amount      NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    description VARCHAR(255)  NOT NULL,
    frequency   VARCHAR(10)   NOT NULL, -- DAILY | WEEKLY | MONTHLY | YEARLY
    day_of_month SMALLINT     NOT NULL DEFAULT 1,
    next_run    DATE          NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------- Orçamento ----------
CREATE TABLE IF NOT EXISTS budgets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    month       DATE NOT NULL, -- primeiro dia do mês
    name        VARCHAR(120) NOT NULL DEFAULT 'Mensal',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_budget_user_month UNIQUE (user_id, month)
);

CREATE TABLE IF NOT EXISTS budget_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    budget_id           UUID NOT NULL REFERENCES budgets (id) ON DELETE CASCADE,
    category_id         UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    limit_amount        NUMERIC(18,2) NOT NULL CHECK (limit_amount >= 0),
    alert_threshold_pct SMALLINT      NOT NULL DEFAULT 80 CHECK (alert_threshold_pct BETWEEN 1 AND 100),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_budget_item UNIQUE (budget_id, category_id)
);
CREATE INDEX IF NOT EXISTS ix_budget_items_user_category ON budget_items (user_id, category_id);

-- ---------- Carteira ----------
CREATE TABLE IF NOT EXISTS assets (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ticker     VARCHAR(20)  NOT NULL,
    name       VARCHAR(160) NOT NULL,
    type       VARCHAR(30)  NOT NULL, -- ACAO | FII | ETF | TESOURO_DIRETO | CDB | LCI_LCA
                                      -- | DEBENTURE | FUNDO | PREVIDENCIA | CRIPTO | INDICE
    sector     VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_asset UNIQUE (user_id, ticker)
);

CREATE TABLE IF NOT EXISTS asset_quotes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    asset_id   UUID NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    date       DATE NOT NULL,
    price      NUMERIC(18,8) NOT NULL CHECK (price > 0),
    source     VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_quote UNIQUE (asset_id, date)
);
CREATE INDEX IF NOT EXISTS ix_quotes_asset_date ON asset_quotes (asset_id, date);

CREATE TABLE IF NOT EXISTS operations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    asset_id   UUID NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    type       VARCHAR(6)   NOT NULL, -- BUY | SELL
    quantity   NUMERIC(18,8) NOT NULL CHECK (quantity > 0),
    price      NUMERIC(18,8) NOT NULL CHECK (price > 0),
    fees       NUMERIC(18,2) NOT NULL DEFAULT 0, -- corretagem + taxas
    total      NUMERIC(18,2) NOT NULL,
    date       DATE         NOT NULL,
    broker     VARCHAR(80),
    notes      TEXT,
    day_trade  BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_operations_user_date ON operations (user_id, date);
CREATE INDEX IF NOT EXISTS ix_operations_asset_date ON operations (asset_id, date);

CREATE TABLE IF NOT EXISTS positions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    asset_id        UUID NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    quantity        NUMERIC(18,8) NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    average_price   NUMERIC(18,8) NOT NULL DEFAULT 0,
    total_invested  NUMERIC(18,2) NOT NULL DEFAULT 0,
    realized_result NUMERIC(18,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_position UNIQUE (user_id, asset_id)
);

-- ---------- Proventos ----------
CREATE TABLE IF NOT EXISTS dividends (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    asset_id         UUID NOT NULL REFERENCES assets (id) ON DELETE CASCADE,
    type             VARCHAR(20)   NOT NULL, -- DIVIDENDO | JCP | RENDIMENTO_FII | CUPOM
    amount           NUMERIC(18,2) NOT NULL CHECK (amount >= 0),
    withholding_tax  NUMERIC(18,2) NOT NULL DEFAULT 0,
    per_share        NUMERIC(18,8) NOT NULL DEFAULT 0,
    quantity         NUMERIC(18,8) NOT NULL DEFAULT 0,
    date             DATE          NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_dividends_user_date ON dividends (user_id, date);

-- ---------- Metas ----------
CREATE TABLE IF NOT EXISTS goals (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                 VARCHAR(160) NOT NULL,
    kind                 VARCHAR(30)  NOT NULL, -- RESERVA_EMERGENCIA | VIAGEM | IMOVEL
                                                  -- | APOSENTADORIA | OUTRO
    target_amount        NUMERIC(18,2) NOT NULL CHECK (target_amount > 0),
    target_date          DATE NULL,
    monthly_contribution NUMERIC(18,2) NOT NULL DEFAULT 0,
    status               VARCHAR(12) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | COMPLETED | CANCELLED
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS goal_contributions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    goal_id    UUID NOT NULL REFERENCES goals (id) ON DELETE CASCADE,
    amount     NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    date       DATE  NOT NULL,
    note       VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_goal_contrib_user_date ON goal_contributions (user_id, date);

-- ---------- Alocação-alvo ----------
CREATE TABLE IF NOT EXISTS allocation_targets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    asset_class VARCHAR(30) NOT NULL, -- mesmo domínio de assets.type
    target_pct  NUMERIC(5,2) NOT NULL CHECK (target_pct >= 0 AND target_pct <= 100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_allocation UNIQUE (user_id, asset_class)
);

-- ---------- Impostos ----------
CREATE TABLE IF NOT EXISTS tax_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    asset_id       UUID REFERENCES assets (id) ON DELETE SET NULL,
    operation_id   UUID REFERENCES operations (id) ON DELETE SET NULL,
    kind           VARCHAR(30) NOT NULL, -- GANHO_CAPITAL_VARIAVEL | IR_RENDA_FIXA
                                         -- | COME_COTAS | DIVIDENDO | JCP
    reference_month DATE       NOT NULL, -- primeiro dia do mês de apuração
    taxable_amount NUMERIC(18,2) NOT NULL DEFAULT 0, -- ganho líquido / base
    exempt_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    status         VARCHAR(12) NOT NULL DEFAULT 'PENDENTE', -- PENDENTE | PAGO | ISENTO
    description    VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_tax_events_user_month ON tax_events (user_id, reference_month);

-- ---------- Copiloto IA ----------
CREATE TABLE IF NOT EXISTS ai_insights (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind       VARCHAR(20) NOT NULL, -- CATEGORIZATION | NARRATIVE | ALERT | ANSWER | SUGGESTION
    question   TEXT,
    answer     TEXT,
    payload    JSONB NOT NULL DEFAULT '{}', -- números reais usados na resposta
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_ai_insights_user ON ai_insights (user_id, created_at DESC);

-- ---------- Auditoria ----------
CREATE TABLE IF NOT EXISTS audit_logs (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    action     VARCHAR(60) NOT NULL,
    entity     VARCHAR(60),
    entity_id  UUID,
    ip         VARCHAR(64),
    payload    JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_audit_user ON audit_logs (user_id, created_at DESC);
