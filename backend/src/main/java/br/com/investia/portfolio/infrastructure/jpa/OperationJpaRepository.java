package br.com.investia.portfolio.infrastructure.jpa;

import br.com.investia.portfolio.domain.Operation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OperationJpaRepository extends JpaRepository<Operation, UUID> {
    List<Operation> findAllByUserIdOrderByDateAsc(UUID userId);

    List<Operation> findAllByUserIdAndDateBetweenOrderByDateAsc(UUID userId, LocalDate start, LocalDate end);

    List<Operation> findAllByUserIdAndAssetIdOrderByDateAsc(UUID userId, UUID assetId);
}
