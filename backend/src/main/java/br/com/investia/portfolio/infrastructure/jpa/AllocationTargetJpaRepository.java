package br.com.investia.portfolio.infrastructure.jpa;

import br.com.investia.portfolio.domain.AllocationTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllocationTargetJpaRepository extends JpaRepository<AllocationTarget, UUID> {
    List<AllocationTarget> findAllByUserId(UUID userId);

    Optional<AllocationTarget> findByUserIdAndAssetClass(UUID userId, String assetClass);
}
