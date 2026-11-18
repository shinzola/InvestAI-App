package br.com.investia.budgeting.infrastructure.jpa;

import br.com.investia.budgeting.domain.BudgetItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetItemJpaRepository extends JpaRepository<BudgetItem, UUID> {
    List<BudgetItem> findAllByBudgetId(UUID budgetId);

    Optional<BudgetItem> findByBudgetIdAndCategoryId(UUID budgetId, UUID categoryId);

    void deleteByBudgetIdAndIdIn(UUID budgetId, List<UUID> ids);
}
