-- =====================================================================
-- InvestIA — V4: CHAR(3) -> VARCHAR(3)
-- O Hibernate lê bpchar como Types#CHAR e não o reconhece como VARCHAR,
-- o que quebra a validação de schema (ddl-auto=validate).
-- =====================================================================
ALTER TABLE user_profiles
    ALTER COLUMN base_currency TYPE VARCHAR(3);
