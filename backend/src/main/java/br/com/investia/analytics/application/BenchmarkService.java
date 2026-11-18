package br.com.investia.analytics.application;

import br.com.investia.portfolio.application.QuoteQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Benchmarks: acumulação de CDI, IBOV e IPCA a partir de cotações do
 * tipo INDICE. Convenções de cotação do índice:
 *  - CDI: preço = taxa % a.a. do dia  -> fator diário (1+i)^(1/252)
 *  - IPCA: preço = variação % do mês  -> fator mensal
 *  - IBOV: preço = nível do índice    -> retorno relativo entre dias
 */
@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private final QuoteQueryPort quotes;

    @Cacheable(value = "benchmark", key = "#userId + ':' + #ticker + ':' + #start + ':' + #end")
    public BigDecimal accumulatedReturn(UUID userId, String ticker, LocalDate start, LocalDate end) {
        List<QuoteQueryPort.QuotePoint> series = quotes.quotesOfTicker(userId, ticker, start, end);
        if (series.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal factor = BigDecimal.ONE;
        for (int i = 1; i < series.size(); i++) {
            factor = factor.multiply(stepFactor(ticker, series.get(i - 1).price(), series.get(i).price()));
        }
        return factor.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal stepFactor(String ticker, BigDecimal prev, BigDecimal curr) {
        if ("CDI".equalsIgnoreCase(ticker)) {
            double annual = curr.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP).doubleValue();
            return BigDecimal.valueOf(1 + Math.pow(1 + annual, 1.0 / 252) - 1);
        }
        if ("IPCA".equalsIgnoreCase(ticker)) {
            double monthly = curr.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP).doubleValue();
            return BigDecimal.valueOf(1 + monthly);
        }
        // IBOV e outros índices de nível: fator relativo entre dias
        if (prev.signum() == 0) {
            return BigDecimal.ONE;
        }
        return curr.divide(prev, 12, RoundingMode.HALF_UP);
    }
}
