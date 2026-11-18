package br.com.investia.planning.infrastructure.jpa;

import br.com.investia.planning.domain.PlannedExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PlannedExpenseJpaRepository extends JpaRepository<PlannedExpense, UUID> {

    List<PlannedExpense> findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(
            UUID userId, LocalDate start, LocalDate end);
}
