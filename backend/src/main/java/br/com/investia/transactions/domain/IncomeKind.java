package br.com.investia.transactions.domain;

/** Natureza da entrada de dinheiro (usada quando type = INCOME). */
public enum IncomeKind {
    /** Salário, pró-labore, 13º. */
    SALARY,
    /** Aporte que o usuário faz para investir. */
    CONTRIBUTION,
    /** Valor estimado, ainda não confirmado. */
    ESTIMATE,
    /** Feriado remunerado, folga paga, adicional de férias. */
    PAID_HOLIDAY,
    /** Renda extra, freelas, bônus. */
    EXTRA,
    /** Dividendos, JCP, rendimentos. */
    DIVIDEND,
    /** Reembolso, venda de item, outras entradas. */
    OTHER
}
