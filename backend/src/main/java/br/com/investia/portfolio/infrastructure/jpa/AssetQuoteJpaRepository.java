package br.com.investia.portfolio.infrastructure.jpa;

import br.com.investia.portfolio.domain.AssetQuote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetQuoteJpaRepository extends JpaRepository<AssetQuote, UUID> {

    List<AssetQuote> findAllByAssetIdOrderByDateAsc(UUID assetId);

    List<AssetQuote> findAllByAssetIdAndDateBetweenOrderByDateAsc(UUID assetId, LocalDate start, LocalDate end);

    Optional<AssetQuote> findFirstByAssetIdOrderByDateDesc(UUID assetId);

    Optional<AssetQuote> findByAssetIdAndDate(UUID assetId, LocalDate date);
}
