package br.com.investia.earnings.application.events;

import br.com.investia.earnings.domain.DividendType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Evento publicado quando um provento é registrado (módulo tax reage). */
public record DividendRegisteredEvent(
        UUID userId,
        UUID dividendId,
        UUID assetId,
        String ticker,
        String assetType,
        DividendType type,
        BigDecimal amount,
        BigDecimal withholdingTax,
        LocalDate date) {
}
