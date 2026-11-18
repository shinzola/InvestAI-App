package br.com.investia.goals.domain;

import br.com.investia.shared.domain.Money;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Meta financeira do usuário (aggregate root). */
@Entity
@Table(name = "goals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GoalKind kind;

    @Column(name = "target_amount", nullable = false)
    private Money targetAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "monthly_contribution", nullable = false)
    @Builder.Default
    private Money monthlyContribution = Money.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private GoalStatus status = GoalStatus.ACTIVE;

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

    public void completeIfReached(Money saved) {
        if (status == GoalStatus.ACTIVE && saved.value().compareTo(targetAmount.value()) >= 0) {
            status = GoalStatus.COMPLETED;
        }
    }

    public enum GoalStatus {
        ACTIVE, COMPLETED, CANCELLED
    }
}
