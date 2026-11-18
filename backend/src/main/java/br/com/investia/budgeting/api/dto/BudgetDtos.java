package br.com.investia.budgeting.api.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public final class BudgetDtos {

    private BudgetDtos() {
    }

    public record BudgetItemRequest(
            @NotNull java.util.UUID categoryId,
            @NotNull @DecimalMin("0.00") BigDecimal limitAmount,
            @Min(1) @Max(100) Integer alertThresholdPct) {
    }

    public record BudgetUpsertRequest(@NotNull @Size(min = 1) List<BudgetItemRequest> items) {
    }

    public record BudgetItemResponse(
            String categoryId, String categoryName, BigDecimal limitAmount,
            BigDecimal spent, BigDecimal remaining, double spentPct,
            int alertThresholdPct, String level, boolean atypical) {
    }

    public record BudgetResponse(
            String month, BigDecimal totalLimit, BigDecimal totalSpent,
            double totalSpentPct, List<BudgetItemResponse> items,
            BigDecimal previousMonthSpent, double monthOverMonthPct) {
    }
}
