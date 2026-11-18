package br.com.investia.earnings.application;

import br.com.investia.earnings.api.dto.EarningsDtos.*;
import br.com.investia.earnings.application.events.DividendRegisteredEvent;
import br.com.investia.earnings.domain.Dividend;
import br.com.investia.earnings.infrastructure.jpa.DividendJpaRepository;
import br.com.investia.identity.application.UserDataContributor;
import br.com.investia.identity.infrastructure.jpa.UserProfileJpaRepository;
import br.com.investia.portfolio.infrastructure.jpa.AssetJpaRepository;
import br.com.investia.shared.domain.ApiException;
import br.com.investia.shared.domain.Money;
import br.com.investia.shared.domain.PeriodRange;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Renda passiva: histórico, média mensal, projeção 12 meses e
 * "quanto falta para viver de renda" (meta do perfil do usuário).
 */
@Service
@RequiredArgsConstructor
public class EarningsService implements UserDataContributor {

    private final DividendJpaRepository dividends;
    private final AssetJpaRepository assets;
    private final UserProfileJpaRepository profiles;
    private final ApplicationEventPublisher events;

    @Transactional
    public DividendResponse register(UUID userId, DividendRequest req) {
        var asset = assets.findById(req.assetId())
                .filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado"));

        Dividend d = dividends.save(Dividend.builder()
                .userId(userId)
                .assetId(asset.getId())
                .type(req.type())
                .amount(Money.of(req.amount()))
                .withholdingTax(Money.of(req.withholdingTax() == null
                        ? BigDecimal.ZERO : req.withholdingTax()))
                .perShare(req.perShare() == null ? BigDecimal.ZERO : req.perShare())
                .quantity(req.quantity() == null ? BigDecimal.ZERO : req.quantity())
                .date(req.date())
                .build());

        events.publishEvent(new DividendRegisteredEvent(userId, d.getId(), asset.getId(),
                asset.getTicker(), asset.getType().name(), d.getType(),
                d.getAmount().value(), d.getWithholdingTax().value(), d.getDate()));

        return new DividendResponse(d.getId().toString(), asset.getId().toString(),
                asset.getTicker(), d.getType().name(), d.getAmount().value(),
                d.getWithholdingTax().value(), d.netAmount().value(), d.getDate());
    }

    @Transactional(readOnly = true)
    public List<DividendResponse> history(UUID userId) {
        return dividends.findAllByUserIdOrderByDateDesc(userId).stream()
                .map(d -> {
                    String ticker = assets.findById(d.getAssetId())
                            .map(a -> a.getTicker()).orElse("?");
                    return new DividendResponse(d.getId().toString(), d.getAssetId().toString(),
                            ticker, d.getType().name(), d.getAmount().value(),
                            d.getWithholdingTax().value(), d.netAmount().value(), d.getDate());
                })
                .toList();
    }

    /** Série mensal dos últimos 12 meses + projeção e cobertura da meta. */
    @Transactional(readOnly = true)
    public EarningsSummary summary(UUID userId) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(11).withDayOfMonth(1);

        List<Dividend> dividends12m = dividends.findAllByUserIdAndDateBetweenOrderByDateAsc(userId, start, end);
        BigDecimal totalNet = dividends12m.stream()
                .map(d -> d.netAmount().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = totalNet.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        BigDecimal desired = profiles.findByUserId(userId)
                .map(p -> p.getDesiredMonthlyIncome().value())
                .orElse(BigDecimal.ZERO);

        BigDecimal missing = desired.subtract(average).max(BigDecimal.ZERO);
        double coverage = desired.signum() == 0
                ? 0
                : average.multiply(BigDecimal.valueOf(100)).divide(desired, 2, RoundingMode.HALF_UP).doubleValue();

        return new EarningsSummary(totalNet, average, average.multiply(BigDecimal.valueOf(12)),
                desired, missing, coverage);
    }

    @Transactional(readOnly = true)
    public List<MonthlyEarnings> monthlySeries(UUID userId, int months) {
        var period = PeriodRange.lastMonths(months, LocalDate.now());
        Map<String, BigDecimal[]> byMonth = new TreeMap<>();
        for (Dividend d : dividends.findAllByUserIdAndDateBetweenOrderByDateAsc(
                userId, period.start(), period.end())) {
            String key = d.getDate().withDayOfMonth(1).toString().substring(0, 7);
            BigDecimal[] acc = byMonth.computeIfAbsent(key,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(d.netAmount().value());
            acc[1] = acc[1].add(d.getAmount().value());
        }
        return byMonth.entrySet().stream()
                .map(e -> new MonthlyEarnings(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Dividend d = dividends.findById(id)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Provento não encontrado"));
        dividends.delete(d);
    }

    @Override
    public String key() {
        return "proventos";
    }

    @Override
    public List<Map<String, Object>> collect(UUID userId) {
        return history(userId).stream()
                .map(d -> Map.<String, Object>of(
                        "data", d.date().toString(), "ticker", d.ticker(),
                        "tipo", d.type(), "liquido", String.valueOf(d.netAmount())))
                .toList();
    }
}
