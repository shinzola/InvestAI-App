package br.com.investia.transactions.api.dto;

import java.util.UUID;

import br.com.investia.transactions.domain.TransactionStatus;
import br.com.investia.transactions.domain.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    public record TransactionRequest(
            @NotNull TransactionType type,
            @NotNull UUID accountId,
            UUID destinationAccountId, // obrigatório para TRANSFER
            UUID categoryId,
            UUID subcategoryId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull LocalDate date,
            @NotBlank @Size(max = 255) String description,
            String notes,
            @Size(max = 10) java.util.List<String> tags,
            TransactionStatus status,
            br.com.investia.transactions.domain.IncomeKind incomeKind,
            @Min(1) @Max(60) Integer installments) {
    }

    public record TransactionResponse(
            String id, String type, String accountId, String destinationAccountId,
            String categoryId, String subcategoryId, BigDecimal amount,
            LocalDate date, String description, String notes,
            java.util.List<String> tags, String status, String source,
            String incomeKind,
            Integer installmentNumber, Integer installmentTotal) {
    }

    public record TransactionPage(
            java.util.List<TransactionResponse> content,
            int page, int size, long totalElements) {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull br.com.investia.transactions.domain.CategoryKind kind,
            UUID parentId,
            @Pattern(regexp = "^#([0-9a-fA-F]{6})$") String color,
            @Size(max = 40) String icon) {
    }

    public record CategoryResponse(
            String id, String name, String kind, String parentId, String color, String icon) {
    }

    public record RecurringRequest(
            @NotNull UUID accountId,
            UUID categoryId,
            @NotNull TransactionType type,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Size(max = 255) String description,
            @NotNull br.com.investia.transactions.domain.RecurringRule.Frequency frequency,
            @Min(1) @Max(28) Integer dayOfMonth,
            @NotNull LocalDate nextRun) {
    }

    public record RecurringResponse(
            String id, String accountId, String categoryId, String type,
            BigDecimal amount, String description, String frequency,
            int dayOfMonth, LocalDate nextRun, boolean active) {
    }

    public record ImportResult(int imported, int skipped, java.util.List<String> errors) {
    }
}
