package br.com.investia.ai.application;

import br.com.investia.ai.domain.AiInsight;
import br.com.investia.ai.infrastructure.jpa.AiInsightJpaRepository;
import br.com.investia.budgeting.application.events.BudgetAlertRaisedEvent;
import br.com.investia.shared.domain.Money;
import br.com.investia.transactions.application.TransactionQueryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock
    private TransactionQueryPort transactions;

    @Mock
    private AiInsightJpaRepository repository;

    @InjectMocks
    private InsightService service;

    @Test
    void alertaNoLimiteDe80PorCento() {
        var event = new BudgetAlertRaisedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Alimentação", "AT80", Money.of("800"), Money.of("1000"));

        service.onBudgetAlert(event);

        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(AiInsight.InsightKind.ALERT);
        assertThat(captor.getValue().getAnswer()).contains("80");
    }

    @Test
    void alertaNoEstouroDe100PorCento() {
        var event = new BudgetAlertRaisedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Lazer", "AT100", Money.of("1200"), Money.of("1000"));

        service.onBudgetAlert(event);

        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAnswer()).contains("estourado");
    }

    @Test
    void gasto42PorCentoAcimaDaMediaGeraAlerta() {
        var userId = UUID.randomUUID();
        var now = java.time.LocalDate.now();
        var month = br.com.investia.shared.domain.PeriodRange.ofMonth(now.getYear(), now.getMonthValue());

        org.mockito.Mockito.when(transactions.avgMonthlyExpense(userId, 3))
                .thenReturn(new BigDecimal("1000"));
        org.mockito.Mockito.when(transactions.expense(userId, month.start(), month.end()))
                .thenReturn(new BigDecimal("1450"));

        var event = new br.com.investia.transactions.application.events.TransactionCreatedEvent(
                userId, UUID.randomUUID(), UUID.randomUUID(), "Compra",
                Money.of("50"), br.com.investia.transactions.domain.TransactionType.EXPENSE,
                br.com.investia.transactions.domain.TransactionSource.MANUAL, now);

        service.onTransaction(event);

        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(AiInsight.InsightKind.ALERT);
        assertThat(captor.getValue().getAnswer()).contains("45%");
    }
}
