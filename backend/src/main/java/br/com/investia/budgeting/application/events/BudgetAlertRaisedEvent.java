package br.com.investia.budgeting.application.events;

import br.com.investia.shared.domain.Money;

import java.util.UUID;

/** Alerta de orçamento (80% e 100% do limite). Publicado pelo módulo budgeting. */
public record BudgetAlertRaisedEvent(
        UUID userId,
        UUID budgetId,
        UUID categoryId,
        String categoryName,
        String level, // AT80 | AT100
        Money spent,
        Money limit) {
}
