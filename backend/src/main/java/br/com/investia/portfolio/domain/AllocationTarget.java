package br.com.investia.portfolio.domain;

import br.com.investia.shared.domain.Percentage;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Alocação-alvo por classe de ativo (%), base do rebalanceamento. */
@Entity
@Table(name = "allocation_targets", uniqueConstraints = @UniqueConstraint(
        name = "uq_allocation", columnNames = {"user_id", "asset_class"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "asset_class", nullable = false, length = 30)
    private String assetClass;

    @Column(name = "target_pct", nullable = false, precision = 5, scale = 2)
    private Percentage targetPct;

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
