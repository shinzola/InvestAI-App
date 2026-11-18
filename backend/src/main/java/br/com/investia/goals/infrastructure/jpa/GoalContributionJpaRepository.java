package br.com.investia.goals.infrastructure.jpa;

import br.com.investia.goals.domain.GoalContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalContributionJpaRepository extends JpaRepository<GoalContribution, UUID> {

    List<GoalContribution> findAllByGoalIdOrderByDateAsc(UUID goalId);

    @Query(value = "SELECT COALESCE(SUM(c.amount), 0) FROM goal_contributions c WHERE c.goal_id = :goalId",
            nativeQuery = true)
    BigDecimal totalSavedByGoal(@Param("goalId") UUID goalId);

    @Query(value = "SELECT COALESCE(SUM(c.amount), 0) FROM goal_contributions c WHERE c.user_id = :userId",
            nativeQuery = true)
    BigDecimal totalSavedByUser(@Param("userId") UUID userId);
}
