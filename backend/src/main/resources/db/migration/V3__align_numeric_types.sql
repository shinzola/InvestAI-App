-- =====================================================================
-- InvestIA — V3: alinha tipos numéricos com o mapeamento JPA
-- Colunas SMALLINT mapeadas para int/Integer em Java passam a INTEGER,
-- evitando falha de validação de schema do Hibernate (ddl-auto=validate).
-- =====================================================================
ALTER TABLE transactions
    ALTER COLUMN installment_number TYPE INTEGER,
    ALTER COLUMN installment_total  TYPE INTEGER;

ALTER TABLE recurring_rules
    ALTER COLUMN day_of_month TYPE INTEGER;

ALTER TABLE budget_items
    ALTER COLUMN alert_threshold_pct TYPE INTEGER;
