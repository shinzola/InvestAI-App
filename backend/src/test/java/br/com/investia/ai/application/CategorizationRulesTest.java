package br.com.investia.ai.application;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CategorizationRulesTest {

    private final CategorizationRules rules = new CategorizationRules();

    @Test
    void ifoodVaiParaDelivery() {
        assertThat(rules.suggestCategory("IFOOD *RESTAURANTE X", false))
                .isEqualTo(Optional.of("Delivery"));
    }

    @Test
    void mercadoVaiParaMercado() {
        assertThat(rules.suggestCategory("SUPERMERCADO ABC LTDA", false))
                .isEqualTo(Optional.of("Mercado"));
    }

    @Test
    void uberVaiParaTransporte() {
        assertThat(rules.suggestCategory("UBER TRIP HELP", false))
                .isEqualTo(Optional.of("Transporte"));
    }

    @Test
    void semRegraRetornaVazio() {
        assertThat(rules.suggestCategory("xyz sem categoria", false)).isEmpty();
    }

    @Test
    void salarioDetectadoComoReceita() {
        assertThat(rules.suggestCategory("SALÁRIO MENSAL", true))
                .isEqualTo(Optional.of("Salário"));
    }
}
