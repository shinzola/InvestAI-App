package br.com.investia.goals.domain;

import br.com.investia.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GoalTest {

    @Test
    void metaCompletaQuandoAlcancada() {
        Goal goal = Goal.builder()
                .name("Reserva")
                .kind(GoalKind.RESERVA_EMERGENCIA)
                .targetAmount(Money.of("10000"))
                .build();
        goal.completeIfReached(Money.of("10000"));
        assertThat(goal.getStatus()).isEqualTo(Goal.GoalStatus.COMPLETED);
    }

    @Test
    void metaAtivaQuandoAbaixoDoAlvo() {
        Goal goal = Goal.builder()
                .name("Viagem")
                .kind(GoalKind.VIAGEM)
                .targetAmount(Money.of("10000"))
                .build();
        goal.completeIfReached(Money.of(new BigDecimal("9999.99")));
        assertThat(goal.getStatus()).isEqualTo(Goal.GoalStatus.ACTIVE);
    }
}
