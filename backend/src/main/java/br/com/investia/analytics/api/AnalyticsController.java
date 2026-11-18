package br.com.investia.analytics.api;

import br.com.investia.analytics.application.AnalyticsService;
import br.com.investia.analytics.application.BenchmarkService;
import br.com.investia.analytics.application.HealthScoreService;
import br.com.investia.analytics.application.RiskService;
import br.com.investia.identity.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Analytics", description = "Dashboard, risco, benchmarks e rebalanceamento")
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final AnalyticsService analytics;
    private final HealthScoreService health;
    private final BenchmarkService benchmarks;

    @Operation(summary = "Sumário do mês: receitas, despesas, patrimônio")
    @GetMapping("/summary")
    public AnalyticsService.Summary summary(@RequestParam int year, @RequestParam int month) {
        return analytics.summary(SecurityUtils.currentUserId(), year, month);
    }

    @Operation(summary = "Alocação atual por classe")
    @GetMapping("/allocation")
    public Object allocation() {
        return analytics.allocation(SecurityUtils.currentUserId());
    }

    @Operation(summary = "Sugestões de rebalanceamento")
    @GetMapping("/rebalancing")
    public Object rebalancing() {
        return analytics.rebalancing(SecurityUtils.currentUserId());
    }

    @Operation(summary = "Score de saúde financeira (0–100)")
    @GetMapping("/health")
    public HealthScoreService.HealthResponse healthScore(@RequestParam int year, @RequestParam int month) {
        return health.compute(SecurityUtils.currentUserId(), year, month);
    }

    @Operation(summary = "Métricas de risco da carteira")
    @GetMapping("/risk")
    public RiskService.RiskMetrics risk(@RequestParam(defaultValue = "12") int months) {
        return analytics.risk(SecurityUtils.currentUserId(), months);
    }

    @Operation(summary = "Comparação de retornos: carteira vs CDI/IBOV/IPCA")
    @GetMapping("/benchmarks")
    public Object benchmarks(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        UUID userId = SecurityUtils.currentUserId();
        return List.of(
                Map.of("indice", "CDI", "retornoAcumuladoPct",
                        benchmarks.accumulatedReturn(userId, "CDI", start, end)),
                Map.of("indice", "IBOV", "retornoAcumuladoPct",
                        benchmarks.accumulatedReturn(userId, "IBOV", start, end)),
                Map.of("indice", "IPCA", "retornoAcumuladoPct",
                        benchmarks.accumulatedReturn(userId, "IPCA", start, end)));
    }
}
