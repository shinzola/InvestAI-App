package br.com.investia.budgeting.domain;

import br.com.investia.shared.domain.Money;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Orçamento mensal (aggregate root), com itens por categoria (envelopes). */
@Entity
@Table(name = "budgets", uniqueConstraints = @UniqueConstraint(
        name = "uq_budget_user_month", columnNames = {"user_id", "month"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Primeiro dia do mês de referência. */
    @Column(nullable = false)
    private LocalDate month;

    @Column(nullable = false, length = 120)
    @Builder.Default
    private String name = "Mensal";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @Transient
    private List<BudgetItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        month = month.withDayOfMonth(1);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Garante que o mês do orçamento é sempre o primeiro dia. */
    public void setMonthNormalized(LocalDate m) {
        month = m.withDayOfMonth(1);
    }
}
