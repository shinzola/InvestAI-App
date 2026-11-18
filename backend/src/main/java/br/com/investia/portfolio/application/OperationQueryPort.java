package br.com.investia.portfolio.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Porta de leitura de operações para outros módulos (ex.: apuração de IR). */
public interface OperationQueryPort {

    /** Soma do total de VENDAS do usuário no período (para isenção de 20k/mês). */
    BigDecimal salesTotal(UUID userId, LocalDate start, LocalDate end);
}
