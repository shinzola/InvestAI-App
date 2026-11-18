package br.com.investia.portfolio.infrastructure.jpa;

import br.com.investia.portfolio.domain.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionJpaRepository extends JpaRepository<Position, UUID> {
    Optional<Position> findByUserIdAndAssetId(UUID userId, UUID assetId);

    List<Position> findAllByUserId(UUID userId);
}
