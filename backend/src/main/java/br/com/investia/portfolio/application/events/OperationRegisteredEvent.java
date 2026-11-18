package br.com.investia.portfolio.application.events;

import br.com.investia.portfolio.domain.OperationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Evento publicado quando uma operação é registrada (módulo tax reage). */
public record OperationRegisteredEvent(
        UUID userId,
        UUID operationId,
        UUID assetId,
        String ticker,
        String assetType,
        OperationType type,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal total,
        BigDecimal fees,
        BigDecimal averagePriceAtSale,
        Instant positionOpenedAt,
        boolean dayTrade,
        LocalDate date) {
}
