package br.com.investia.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    void deveArredondarComEscala2HalfUp() {
        assertThat(Money.of(new BigDecimal("10.005")).value()).isEqualByComparingTo("10.01");
        assertThat(Money.of(new BigDecimal("10.004")).value()).isEqualByComparingTo("10.00");
    }

    @Test
    void deveSomarESubtrair() {
        Money total = Money.of("100.50").add(Money.of("50.25")).subtract(Money.of("25.00"));
        assertThat(total.value()).isEqualByComparingTo("125.75");
    }

    @Test
    void deveCalcularPercentualDeParticipacao() {
        Percentage pct = Money.of("250").shareOf(Money.of("1000"));
        assertThat(pct.value()).isEqualByComparingTo("25.00");
    }

    @Test
    void percentualSobreTotalZeroNaoDividePorZero() {
        assertThat(Money.of("10").shareOf(Money.ZERO).value()).isEqualByComparingTo("0");
    }

    @Test
    void deveMultiplicar() {
        assertThat(Money.of("12.34").multiply(new BigDecimal("3")).value())
                .isEqualByComparingTo("37.02");
    }
}
