package br.com.investia.goals.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialMathTest {

    @Test
    void aporteNecessarioSemJuros() {
        // meta 12.000 em 12 meses sem juros → 1.000/mês
        assertThat(FinancialMath.requiredMonthlyContribution(
                new BigDecimal("12000"), BigDecimal.ZERO, 12))
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void aporteNecessarioComJuros() {
        // meta 10.000 em 12 meses a 1% a.m. → PMT ≈ 788,49
        BigDecimal pmt = FinancialMath.requiredMonthlyContribution(
                new BigDecimal("10000"), new BigDecimal("0.01"), 12);
        assertThat(pmt).isEqualByComparingTo("788.49");
    }

    @Test
    void taxaAnualParaMensal() {
        // 12% a.a. → ≈ 0,9489% a.m.
        assertThat(FinancialMath.annualToMonthly(new BigDecimal("12")))
                .isEqualByComparingTo("0.00948879");
    }

    @Test
    void jurosCompostos() {
        // 1.000 a 1% a.m. por 12 meses ≈ 1.126,83
        assertThat(FinancialMath.futureValue(new BigDecimal("1000"),
                new BigDecimal("0.01"), 12))
                .isEqualByComparingTo("1126.83");
    }
}
