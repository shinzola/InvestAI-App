package br.com.investia.identity.domain;

import br.com.investia.shared.domain.Money;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** Perfil de investidor: estilo, moeda base, fuso e meta de renda passiva. */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "investor_profile", nullable = false, length = 20)
    @Builder.Default
    private InvestorProfile investorProfile = InvestorProfile.MODERADO;

    @Column(name = "base_currency", nullable = false, length = 3)
    @Builder.Default
    private String baseCurrency = "BRL";

    @Column(nullable = false, length = 64)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    /** Renda passiva mensal desejada ("viver de renda"). */
    @Column(name = "desired_monthly_income", nullable = false)
    @Builder.Default
    private Money desiredMonthlyIncome = Money.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = java.time.Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = java.time.Instant.now();
    }
}
