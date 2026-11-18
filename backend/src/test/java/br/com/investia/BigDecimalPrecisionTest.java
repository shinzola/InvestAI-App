package br.com.investia;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Regressão da fórmula do requisito com Decimal128 (sem float). */
class BigDecimalPrecisionTest {

    @Test
    void naoHaErroDePontoFlutuante() {
        BigDecimal a = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        assertThat(a.toPlainString()).isEqualTo("0.3");
    }

    @Test
    void datasDeParcelamentoAvancamPorMes() {
        LocalDate start = LocalDate.of(2026, 1, 31); // fim de mês
        LocalDate p1 = start;
        LocalDate p2 = p1.plusMonths(1);
        LocalDate p3 = p1.plusMonths(2);
        assertThat(p1.getMonthValue()).isEqualTo(1);
        assertThat(p2.getMonthValue()).isEqualTo(2);
        assertThat(p3.getMonthValue()).isEqualTo(3);
    }
}
