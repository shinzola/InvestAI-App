package br.com.investia.goals.api.dto;

import java.util.List;
import java.util.UUID;

import br.com.investia.goals.domain.GoalKind;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class GoalDtos {

    private GoalDtos() {
    }

    public record GoalRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull GoalKind kind,
            @NotNull @DecimalMin("0.01") BigDecimal targetAmount,
            LocalDate targetDate,
            @DecimalMin("0.00") BigDecimal monthlyContribution) {
    }

    public record GoalResponse(
            String id, String name, String kind, BigDecimal targetAmount,
            LocalDate targetDate, BigDecimal monthlyContribution, String status,
            BigDecimal saved, double progressPct, BigDecimal requiredMonthlyContribution) {
    }

    public record ContributionRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull LocalDate date,
            @Size(max = 255) String note) {
    }

    /** Simulação de juros compostos com correção pela inflação. */
    public record SimulationRequest(
            @NotNull @DecimalMin("0.00") BigDecimal initialAmount,
            @NotNull @DecimalMin("0.00") BigDecimal monthlyContribution,
            @Min(1) @Max(60) int years,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal annualRate,
            @DecimalMin("0.00") @DecimalMax("50.00") BigDecimal annualInflation) {
    }

    public record SimulationPoint(int month, BigDecimal balance, BigDecimal invested,
                                  BigDecimal realBalance) {
    }

    public record SimulationResponse(
            BigDecimal finalBalance, BigDecimal totalInvested, BigDecimal totalInterest,
            BigDecimal finalRealBalance, List<SimulationPoint> series) {
    }
}
