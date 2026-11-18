package br.com.investia.portfolio.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Porta de leitura de cotações por ticker (ex.: benchmarks CDI/IBOV/IPCA). */
public interface QuoteQueryPort {

    record QuotePoint(LocalDate date, BigDecimal price) {
    }

    List<QuotePoint> quotesOfTicker(UUID userId, String ticker, LocalDate start, LocalDate end);
}
