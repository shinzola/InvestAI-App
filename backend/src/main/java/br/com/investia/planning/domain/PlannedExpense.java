package br.com.investia.planning.domain;

import br.com.investia.shared.domain.Money;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Gasto previsto para o mês, com data de vencimento e situação. */
@Entity
@Table(name = "planned_expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannedExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private Money amount;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Conta de cartão de crédito usada quando kind = CREDIT_CARD. */
    @Column(name = "card_account_id")
    private UUID cardAccountId;

    @Column(name = "invoice_closing_date")
    private LocalDate invoiceClosingDate;

    @Column(name = "invoice_due_date")
    private LocalDate invoiceDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private PlannedExpenseKind kind = PlannedExpenseKind.VARIABLE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private PlannedExpenseStatus status = PlannedExpenseStatus.PLANNED;

    @Column(length = 255)
    private String notes;

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
}
