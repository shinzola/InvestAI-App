package br.com.investia.transactions.application.events;

import br.com.investia.shared.domain.Money;
import br.com.investia.transactions.domain.TransactionSource;
import br.com.investia.transactions.domain.TransactionType;

import java.time.LocalDate;
import java.util.UUID;

/** Evento publicado quando uma transação é criada (orçamentos, IA, auditoria). */
public record TransactionCreatedEvent(
        UUID userId,
        UUID transactionId,
        UUID categoryId,
        String description,
        Money amount,
        TransactionType type,
        TransactionSource source,
        LocalDate date) {
}
