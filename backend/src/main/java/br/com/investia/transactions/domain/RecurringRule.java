package br.com.investia.transactions.domain;

import br.com.investia.shared.domain.Money;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Regra de recorrência gera transações automaticamente quando vence. */
@Entity
@Table(name = "recurring_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TransactionType type;

    @Column(nullable = false)
    private Money amount;

    @Column(nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Frequency frequency;

    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "next_run", nullable = false)
    private LocalDate nextRun;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum Frequency {
        DAILY, WEEKLY, MONTHLY, YEARLY
    }

    /** Avança a próxima execução a partir de uma data. */
    public void advance() {
        nextRun = switch (frequency) {
            case DAILY -> nextRun.plusDays(1);
            case WEEKLY -> nextRun.plusWeeks(1);
            case MONTHLY -> nextRun.plusMonths(1);
            case YEARLY -> nextRun.plusYears(1);
        };
    }
}
