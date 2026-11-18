package br.com.investia.analytics.application;

import br.com.investia.portfolio.application.QuoteQueryPort;
import br.com.investia.portfolio.infrastructure.jpa.AssetJpaRepository;
import br.com.investia.portfolio.infrastructure.jpa.AssetQuoteJpaRepository;
import br.com.investia.portfolio.infrastructure.jpa.PositionJpaRepository;
import br.com.investia.shared.domain.PeriodRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Série de valuation da carteira e métricas de risco:
 * volatilidade anualizada, drawdown máximo, Sharpe e beta vs IBOV.
 */
@Service
@RequiredArgsConstructor
public class RiskService {

    private final PositionJpaRepository positions;
    private final AssetJpaRepository assets;
    private final AssetQuoteJpaRepository quotes;
    private final QuoteQueryPort quotePort;

    /** Valores diários da carteira no período (posição atual × última cotação). */
    @Transactional(readOnly = true)
    public TreeMap<LocalDate, BigDecimal> valuationSeries(UUID userId, LocalDate start, LocalDate end) {
        TreeMap<LocalDate, BigDecimal> series = new TreeMap<>();
        var pos = positions.findAllByUserId(userId);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            BigDecimal value = BigDecimal.ZERO;
            for (var p : pos) {
                var last = quotes.findFirstByAssetIdOrderByDateDesc(p.getAssetId());
                // última cotação <= d
                var dayQuote = quotes.findAllByAssetIdAndDateBetweenOrderByDateAsc(p.getAssetId(), start, d);
                BigDecimal price = dayQuote.isEmpty()
                        ? (last.map(q -> q.getPrice()).orElse(p.getAveragePrice()))
                        : dayQuote.get(dayQuote.size() - 1).getPrice();
                value = value.add(p.getQuantity().multiply(price));
            }
            series.put(d, value.setScale(2, RoundingMode.HALF_UP));
        }
        return series;
    }

    @Transactional(readOnly = true)
    public RiskMetrics metrics(UUID userId, LocalDate start, LocalDate end) {
        var series = valuationSeries(userId, start, end);
        double[] returns = dailyReturns(series);
        double vol = annualizedVolatility(returns);
        double maxDrawdown = maxDrawdown(series);
        double annualReturn = Math.pow(1 + (series.size() > 1 ? totalReturn(series) : 0),
                252.0 / Math.max(series.size(), 2)) - 1;

        double cdiAnnual = benchmarkAnnual(userId, "CDI", start, end);
        double sharpe = vol == 0 ? 0 : (annualReturn - cdiAnnual) / vol;
        double beta = betaVsIbov(userId, returns, start, end, series.size());

        return new RiskMetrics(
                round(vol * 100), round(maxDrawdown * 100), round(annualReturn * 100),
                round(sharpe), round(beta));
    }

    public record RiskMetrics(double volatilityPct, double maxDrawdownPct,
                              double annualReturnPct, double sharpe, double beta) {
    }

    double[] dailyReturns(TreeMap<LocalDate, BigDecimal> series) {
        List<Double> rets = new ArrayList<>();
        BigDecimal prev = null;
        for (BigDecimal v : series.values()) {
            if (prev != null && prev.signum() > 0) {
                rets.add(v.subtract(prev).divide(prev, 8, RoundingMode.HALF_UP).doubleValue());
            }
            prev = v;
        }
        return rets.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** Volatilidade anualizada: desvio-padrão × √252. */
    double annualizedVolatility(double[] returns) {
        if (returns.length < 2) {
            return 0;
        }
        double mean = Arrays.stream(returns).average().orElse(0);
        double variance = Arrays.stream(returns).map(r -> (r - mean) * (r - mean)).sum() / (returns.length - 1);
        return Math.sqrt(variance) * Math.sqrt(252);
    }

    /** Drawdown máximo: maior queda pico-vale da série. */
    double maxDrawdown(TreeMap<LocalDate, BigDecimal> series) {
        double peak = 0;
        double maxDd = 0;
        for (BigDecimal v : series.values()) {
            double value = v.doubleValue();
            peak = Math.max(peak, value);
            if (peak > 0) {
                maxDd = Math.max(maxDd, (peak - value) / peak);
            }
        }
        return maxDd;
    }

    double totalReturn(TreeMap<LocalDate, BigDecimal> series) {
        if (series.isEmpty()) {
            return 0;
        }
        double first = series.firstEntry().getValue().doubleValue();
        double last = series.lastEntry().getValue().doubleValue();
        return first == 0 ? 0 : (last - first) / first;
    }

    /** Beta = cov(Rp, Rb) / var(Rb) usando retornos diários da carteira e do IBOV. */
    double betaVsIbov(UUID userId, double[] portfolioReturns, LocalDate start, LocalDate end, int days) {
        var ibovSeries = quotePort.quotesOfTicker(userId, "IBOV", start, end);
        if (ibovSeries.size() < 3 || portfolioReturns.length < 2) {
            return 0;
        }
        List<Double> ibovRets = new ArrayList<>();
        for (int i = 1; i < ibovSeries.size(); i++) {
            double prev = ibovSeries.get(i - 1).price().doubleValue();
            double curr = ibovSeries.get(i).price().doubleValue();
            if (prev > 0) {
                ibovRets.add((curr - prev) / prev);
            }
        }
        int n = Math.min(ibovRets.size(), portfolioReturns.length);
        if (n < 2) {
            return 0;
        }
        double[] a = new double[n];
        double[] b = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = portfolioReturns[portfolioReturns.length - n + i];
            b[i] = ibovRets.get(ibovRets.size() - n + i);
        }
        double ma = Arrays.stream(a).average().orElse(0);
        double mb = Arrays.stream(b).average().orElse(0);
        double cov = 0;
        double var = 0;
        for (int i = 0; i < n; i++) {
            cov += (a[i] - ma) * (b[i] - mb);
            var += (b[i] - mb) * (b[i] - mb);
        }
        return var == 0 ? 0 : cov / var;
    }

    private double benchmarkAnnual(UUID userId, String ticker, LocalDate start, LocalDate end) {
        // Média das taxas CDI registradas no período (% a.a.)
        var series = quotePort.quotesOfTicker(userId, ticker, start, end);
        return series.stream().mapToDouble(q -> q.price().doubleValue()).average().orElse(0);
    }

    private double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
