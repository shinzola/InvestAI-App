package br.com.investia.portfolio.application;

import br.com.investia.portfolio.infrastructure.jpa.AllocationTargetJpaRepository;
import br.com.investia.portfolio.infrastructure.jpa.PositionJpaRepository;
import br.com.investia.portfolio.domain.AllocationTarget;
import br.com.investia.portfolio.domain.Asset;
import br.com.investia.portfolio.infrastructure.jpa.AssetJpaRepository;
import br.com.investia.shared.domain.Money;
import br.com.investia.shared.domain.Percentage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Alocação atual por classe vs alocação-alvo, com sugestões de
 * rebalanceamento (maior desvio primeiro).
 */
@Service
@RequiredArgsConstructor
public class RebalancingService {

    private final AllocationTargetJpaRepository targets;
    private final PositionJpaRepository positions;
    private final AssetJpaRepository assets;

    @Transactional(readOnly = true)
    public AllocationReport allocation(UUID userId) {
        BigDecimal total = BigDecimal.ZERO;
        Map<String, BigDecimal> byClass = new LinkedHashMap<>();

        for (var p : positions.findAllByUserId(userId)) {
            if (p.getQuantity().signum() == 0) {
                continue;
            }
            var asset = assets.findById(p.getAssetId()).orElse(null);
            if (asset == null) {
                continue;
            }
            BigDecimal marketValue = Money.of(p.getQuantity().multiply(p.getAveragePrice())).value();
            byClass.merge(asset.getType().name(), marketValue, BigDecimal::add);
            total = total.add(marketValue);
        }

        List<AllocationRow> rows = new ArrayList<>();
        for (AllocationTarget target : targets.findAllByUserId(userId)) {
            BigDecimal current = byClass.getOrDefault(target.getAssetClass(), BigDecimal.ZERO);
            Percentage currentPct = current.signum() == 0
                    ? Percentage.zero()
                    : Percentage.of(current.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP));
            rows.add(new AllocationRow(target.getAssetClass(), target.getTargetPct().value(),
                    current, currentPct.value(),
                    currentPct.value().subtract(target.getTargetPct().value())));
        }

        // Sugestões ordenadas pelo maior desvio absoluto
        List<RebalancingSuggestion> suggestions = rows.stream()
                .filter(r -> r.deviationPct().abs()
                        .compareTo(BigDecimal.valueOf(5)) >= 0)
                .sorted(Comparator.comparing(r -> r.deviationPct().abs(), Comparator.reverseOrder()))
                .map(r -> new RebalancingSuggestion(
                        r.assetClass(),
                        r.deviationPct().signum() > 0 ? "REDUZIR" : "AUMENTAR",
                        Money.of(r.currentValue().multiply(r.deviationPct().abs()
                                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))))
                .toList();

        return new AllocationReport(Money.of(total).value(), rows, suggestions);
    }

    public record AllocationRow(String assetClass, BigDecimal targetPct, BigDecimal currentValue,
                                BigDecimal currentPct, BigDecimal deviationPct) {
    }

    public record RebalancingSuggestion(String assetClass, String action, Money amount) {
    }

    public record AllocationReport(BigDecimal portfolioValue, List<AllocationRow> rows,
                                   List<RebalancingSuggestion> suggestions) {
    }
}
