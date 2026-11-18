package br.com.investia.tax.domain;

import br.com.investia.shared.domain.Money;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Evento tributário apurado (ganho de capital, IR, come-cotas, proventos). */
@Entity
@Table(name = "tax_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "operation_id")
    private UUID operationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaxEventKind kind;

    /** Primeiro dia do mês de apuração. */
    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "taxable_amount", nullable = false)
    @Builder.Default
    private Money taxableAmount = Money.ZERO;

    @Column(name = "exempt_amount", nullable = false)
    @Builder.Default
    private Money exemptAmount = Money.ZERO;

    @Column(name = "tax_amount", nullable = false)
    @Builder.Default
    private Money taxAmount = Money.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private TaxStatus status = TaxStatus.PENDENTE;

    @Column(length = 255)
    private String description;

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

    public enum TaxStatus {
        PENDENTE, PAGO, ISENTO
    }
}
