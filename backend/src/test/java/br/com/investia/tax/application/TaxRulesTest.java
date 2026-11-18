package br.com.investia.tax.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TaxRulesTest {

    @Test
    void acoesAVistaIsentasAte20kNoMes() {
        var r = TaxRules.onStockSale(new BigDecimal("500"), new BigDecimal("20000"), false);
        assertThat(r.tax()).isEqualByComparingTo("0");
        assertThat(r.exemptAmount()).isEqualByComparingTo("500");
    }

    @Test
    void acoesAcimaDe20kPagam15PorCento() {
        var r = TaxRules.onStockSale(new BigDecimal("1000"), new BigDecimal("25000"), false);
        assertThat(r.tax()).isEqualByComparingTo("150.00");
    }

    @Test
    void dayTradePaga20PorCentoSemIsencao() {
        var r = TaxRules.onStockSale(new BigDecimal("1000"), new BigDecimal("1000"), true);
        assertThat(r.tax()).isEqualByComparingTo("200.00");
    }

    @Test
    void fiiPaga20PorCentoSemIsencao() {
        var r = TaxRules.onFiiSale(new BigDecimal("1000"));
        assertThat(r.tax()).isEqualByComparingTo("200.00");
    }

    @Test
    void rendaFixaRegressiva() {
        assertThat(TaxRules.onFixedIncome(new BigDecimal("1000"), 100, false).tax())
                .isEqualByComparingTo("225.00");
        assertThat(TaxRules.onFixedIncome(new BigDecimal("1000"), 200, false).tax())
                .isEqualByComparingTo("200.00");
        assertThat(TaxRules.onFixedIncome(new BigDecimal("1000"), 400, false).tax())
                .isEqualByComparingTo("175.00");
        assertThat(TaxRules.onFixedIncome(new BigDecimal("1000"), 800, false).tax())
                .isEqualByComparingTo("150.00");
    }

    @Test
    void lciLcaIsentas() {
        var r = TaxRules.onFixedIncome(new BigDecimal("1000"), 100, true);
        assertThat(r.tax()).isEqualByComparingTo("0");
        assertThat(r.exemptAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void comeCotasFiiVintePorCento() {
        assertThat(TaxRules.comeCotas(new BigDecimal("500"), true).tax())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void ganhoLiquidoDeVendaDescontaTaxas() {
        assertThat(TaxRules.capitalGain(new BigDecimal("20"), new BigDecimal("15"),
                new BigDecimal("100"), new BigDecimal("25")))
                .isEqualByComparingTo("475.00");
    }
}
