package br.com.investia.budgeting.infrastructure.jpa;

import br.com.investia.budgeting.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BudgetJpaRepository extends JpaRepository<Budget, UUID> {
    Optional<Budget> findByUserIdAndMonth(UUID userId, java.time.LocalDate month);
}
