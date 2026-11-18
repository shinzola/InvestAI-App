package br.com.investia.earnings.api.dto;

import br.com.investia.earnings.domain.DividendType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class EarningsDtos {

    private EarningsDtos() {
    }

    public record DividendRequest(
            @NotNull java.util.UUID assetId,
            @NotNull DividendType type,
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            @DecimalMin("0.00") BigDecimal withholdingTax,
            @DecimalMin("0.00000001") BigDecimal perShare,
            @DecimalMin("0.00000001") BigDecimal quantity,
            @NotNull LocalDate date) {
    }

    public record DividendResponse(
            String id, String assetId, String ticker, String type,
            BigDecimal amount, BigDecimal withholdingTax, BigDecimal netAmount,
            LocalDate date) {
    }

    public record MonthlyEarnings(String month, BigDecimal net, BigDecimal gross) {
    }

    public record EarningsSummary(
            BigDecimal totalLast12m, BigDecimal averageLast12m,
            BigDecimal projectionNext12m, BigDecimal desiredMonthlyIncome,
            BigDecimal missingForDesiredIncome, double coveragePct) {
    }
}
