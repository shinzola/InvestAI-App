package br.com.investia.portfolio.infrastructure.jpa;

import br.com.investia.portfolio.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetJpaRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findAllByUserIdOrderByTickerAsc(UUID userId);

    Optional<Asset> findByUserIdAndTickerIgnoreCase(UUID userId, String ticker);
}
