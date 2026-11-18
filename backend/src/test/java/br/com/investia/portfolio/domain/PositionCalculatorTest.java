package br.com.investia.portfolio.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PositionCalculatorTest {

    @Test
    void deveCalcularPrecoMedioNaCompra() {
        // fórmula do requisito: (100×10 + 100×14) / 200 = 12
        BigDecimal novo = PositionCalculator.averagePriceAfterBuy(
                new BigDecimal("100"), new BigDecimal("10"),
                new BigDecimal("100"), new BigDecimal("14"));
        assertThat(novo).isEqualByComparingTo("12.00000000");
    }

    @Test
    void precoMedioPermaneceNaVenda() {
        // venda não altera preço médio (convenção); resultado realizado é calculado
        BigDecimal resultado = PositionCalculator.realizedResult(
                new BigDecimal("50"), new BigDecimal("15"),
                new BigDecimal("12"), new BigDecimal("10"));
        assertThat(resultado).isEqualByComparingTo("140.00"); // (15−12)×50 − 10
    }

    @Test
    void compraSobrePosicaoDiferenteMédia() {
        // (50×8 + 50×12) / 100 = 10
        assertThat(PositionCalculator.averagePriceAfterBuy(
                new BigDecimal("50"), new BigDecimal("8"),
                new BigDecimal("50"), new BigDecimal("12")))
                .isEqualByComparingTo("10.00000000");
    }
}
