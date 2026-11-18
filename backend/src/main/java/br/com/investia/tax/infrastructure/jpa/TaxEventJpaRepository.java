package br.com.investia.tax.infrastructure.jpa;

import br.com.investia.tax.domain.TaxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TaxEventJpaRepository extends JpaRepository<TaxEvent, UUID> {

    List<TaxEvent> findAllByUserIdAndReferenceMonthOrderByCreatedAtAsc(UUID userId, LocalDate month);

    List<TaxEvent> findAllByUserIdOrderByReferenceMonthDesc(UUID userId);

    @Query("""
            SELECT t FROM TaxEvent t
            WHERE t.userId = :userId AND t.kind = :kind
              AND t.referenceMonth = :month AND t.status = 'PENDENTE'
            """)
    List<TaxEvent> findPending(UUID userId, br.com.investia.tax.domain.TaxEventKind kind, LocalDate month);
}
