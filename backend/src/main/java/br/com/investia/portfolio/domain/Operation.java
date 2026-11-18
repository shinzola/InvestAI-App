package br.com.investia.portfolio.domain;

import br.com.investia.shared.domain.Money;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Operação (compra/venda) com custos de corretagem e taxas. */
@Entity
@Table(name = "operations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private OperationType type;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal price;

    @Column(nullable = false)
    @Builder.Default
    private Money fees = Money.ZERO;

    @Column(nullable = false)
    private Money total;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 80)
    private String broker;

    @Column
    private String notes;

    @Column(name = "day_trade", nullable = false)
    @Builder.Default
    private boolean dayTrade = false;

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

    /** Total financeiro: qtd × preço ± taxas (compra soma, venda subtrai). */
    public Money computeTotal() {
        BigDecimal gross = quantity.multiply(price);
        return type == OperationType.BUY
                ? Money.of(gross).add(fees)
                : Money.of(gross).subtract(fees);
    }
}
