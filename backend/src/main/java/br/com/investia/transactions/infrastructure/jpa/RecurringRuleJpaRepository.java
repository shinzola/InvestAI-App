package br.com.investia.transactions.infrastructure.jpa;

import br.com.investia.transactions.domain.RecurringRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RecurringRuleJpaRepository extends JpaRepository<RecurringRule, UUID> {
    List<RecurringRule> findAllByUserIdAndActiveTrue(UUID userId);

    List<RecurringRule> findByActiveTrueAndNextRunLessThanEqual(LocalDate date);
}
