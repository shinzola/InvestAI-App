package br.com.investia.identity.api.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** DTOs do módulo identity. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 255) String fullName,
            @NotNull Boolean acceptTerms) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ForgotPasswordRequest(@Email @NotBlank String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresInMinutes) {
    }

    public record ProfileResponse(
            String email,
            String fullName,
            String investorProfile,
            String baseCurrency,
            String timezone,
            java.math.BigDecimal desiredMonthlyIncome) {
    }

    public record UpdateProfileRequest(
            @Pattern(regexp = "CONSERVADOR|MODERADO|ARROJADO") String investorProfile,
            String timezone,
            @Min(0) java.math.BigDecimal desiredMonthlyIncome) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
