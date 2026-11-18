package br.com.investia.earnings.domain;

import br.com.investia.shared.domain.Money;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Provento recebido (dividendo, JCP, rendimento de FII ou cupom). */
@Entity
@Table(name = "dividends")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dividend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DividendType type;

    @Column(nullable = false)
    private Money amount;

    @Column(name = "withholding_tax", nullable = false)
    @Builder.Default
    private Money withholdingTax = Money.ZERO;

    @Column(name = "per_share", nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal perShare = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 8)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDate date;

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

    /** Valor líquido recebido (após IR retido na fonte). */
    public Money netAmount() {
        return amount.subtract(withholdingTax);
    }
}
