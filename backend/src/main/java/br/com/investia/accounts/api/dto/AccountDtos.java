package br.com.investia.accounts.api.dto;

import br.com.investia.accounts.domain.AccountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull AccountType type,
            @Size(max = 120) String institution,
            @DecimalMin("0.00") BigDecimal initialBalance,
            @Pattern(regexp = "^#([0-9a-fA-F]{6})$") String color,
            /** Conta corrente com cartão de crédito: o cartão é um complemento, não outro tipo. */
            Boolean hasCreditCard,
            @Size(max = 40) String cardBrand,
            @Min(1) @Max(28) Integer closingDay,
            @Min(1) @Max(28) Integer dueDay,
            @DecimalMin("0.00") BigDecimal limitTotal,
            @DecimalMin("0.00") BigDecimal limitUsed) {
    }

    public record AccountResponse(
            String id, String name, String type, String institution,
            BigDecimal initialBalance, BigDecimal balance, String color, boolean archived,
            CreditCardResponse card) {
    }

    public record CreditCardRequest(
            @NotBlank String accountId,
            @Size(max = 40) String brand,
            @Min(1) @Max(28) short closingDay,
            @Min(1) @Max(28) short dueDay,
            @DecimalMin("0.00") BigDecimal limitTotal,
            @DecimalMin("0.00") BigDecimal limitUsed) {
    }

    public record CreditCardResponse(
            String id, String accountId, String brand,
            short closingDay, short dueDay, BigDecimal limitTotal,
            BigDecimal limitUsed, BigDecimal limitAvailable) {
    }
}
