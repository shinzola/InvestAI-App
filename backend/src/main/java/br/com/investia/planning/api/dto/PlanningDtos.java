package br.com.investia.planning.api.dto;

import br.com.investia.planning.domain.PlannedExpenseKind;
import br.com.investia.planning.domain.PlannedExpenseStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class PlanningDtos {

    private PlanningDtos() {
    }

    public record PlannedExpenseRequest(
            @NotBlank @Size(max = 255) String description,
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            UUID categoryId,
            @NotNull LocalDate dueDate,
            @NotNull PlannedExpenseKind kind,
            PlannedExpenseStatus status,
            @Size(max = 255) String notes,
            UUID cardAccountId) {
    }

    public record PlannedExpenseResponse(
            String id, String description, BigDecimal amount, String categoryId,
            LocalDate dueDate, String kind, String status, String notes,
            String cardAccountId, LocalDate invoiceClosingDate, LocalDate invoiceDueDate) {
    }

    /** Totais do mês e quanto sobra para aportar. */
    public record PlanningSummary(
            BigDecimal totalFixed, BigDecimal totalVariable, BigDecimal totalCreditCard,
            BigDecimal total, BigDecimal paid, BigDecimal pending,
            int count, int paidCount,
            BigDecimal monthIncome, BigDecimal availableToContribute) {
    }
}
