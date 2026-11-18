package br.com.investia.goals.application;

import br.com.investia.goals.api.dto.GoalDtos.*;
import br.com.investia.goals.domain.Goal;
import br.com.investia.goals.domain.GoalKind;
import br.com.investia.goals.infrastructure.jpa.GoalContributionJpaRepository;
import br.com.investia.goals.infrastructure.jpa.GoalJpaRepository;
import br.com.investia.identity.application.UserDataContributor;
import br.com.investia.shared.audit.AuditService;
import br.com.investia.shared.domain.ApiException;
import br.com.investia.shared.domain.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Metas: CRUD, progresso e sugestão de aporte mensal necessário
 * (PMTCalculator com taxa mensal equivalente a 10% a.a.).
 */
@Service
@RequiredArgsConstructor
public class GoalService implements UserDataContributor {

    private static final BigDecimal DEFAULT_ANNUAL_RATE = new BigDecimal("10.00");

    private final GoalJpaRepository goals;
    private final GoalContributionJpaRepository contributions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<GoalResponse> list(UUID userId) {
        return goals.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(g -> toResponse(userId, g)).toList();
    }

    @Transactional
    public GoalResponse create(UUID userId, GoalRequest req) {
        Goal goal = goals.save(Goal.builder()
                .userId(userId)
                .name(req.name().trim())
                .kind(req.kind())
                .targetAmount(Money.of(req.targetAmount()))
                .targetDate(req.targetDate())
                .monthlyContribution(req.monthlyContribution() == null
                        ? Money.ZERO : Money.of(req.monthlyContribution()))
                .build());
        audit.record(userId, "GOAL_CREATED", "goals", goal.getId(), null,
                Map.of("meta", goal.getName()));
        return toResponse(userId, goal);
    }

    @Transactional
    public GoalResponse update(UUID userId, UUID id, GoalRequest req) {
        Goal goal = find(userId, id);
        goal.setName(req.name().trim());
        goal.setKind(req.kind());
        goal.setTargetAmount(Money.of(req.targetAmount()));
        goal.setTargetDate(req.targetDate());
        if (req.monthlyContribution() != null) {
            goal.setMonthlyContribution(Money.of(req.monthlyContribution()));
        }
        return toResponse(userId, goals.save(goal));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        goals.delete(find(userId, id));
    }

    @Transactional
    public GoalResponse contribute(UUID userId, UUID id, ContributionRequest req) {
        Goal goal = find(userId, id);
        contributions.save(br.com.investia.goals.domain.GoalContribution.builder()
                .userId(userId)
                .goalId(goal.getId())
                .amount(Money.of(req.amount()))
                .date(req.date())
                .note(req.note())
                .build());
        BigDecimal saved = contributions.totalSavedByGoal(goal.getId());
        goal.completeIfReached(Money.of(saved));
        return toResponse(userId, goals.save(goal));
    }

    private Goal find(UUID userId, UUID id) {
        return goals.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Meta não encontrada"));
    }

    private GoalResponse toResponse(UUID userId, Goal g) {
        BigDecimal saved = contributions.totalSavedByGoal(g.getId());
        double pct = g.getTargetAmount().value().signum() == 0
                ? 0
                : saved.multiply(BigDecimal.valueOf(100)).divide(g.getTargetAmount().value(), 2, RoundingMode.HALF_UP).doubleValue();

        BigDecimal required = BigDecimal.ZERO;
        if (g.getTargetDate() != null) {
            int months = (int) Math.max(1, ChronoUnit.MONTHS.between(LocalDate.now(), g.getTargetDate()));
            required = FinancialMath.requiredMonthlyContribution(
                    g.getTargetAmount().value(),
                    FinancialMath.annualToMonthly(DEFAULT_ANNUAL_RATE), months);
        }
        return new GoalResponse(g.getId().toString(), g.getName(), g.getKind().name(),
                g.getTargetAmount().value(), g.getTargetDate(), g.getMonthlyContribution().value(),
                g.getStatus().name(), saved, pct, required);
    }

    @Override
    public String key() {
        return "metas";
    }

    @Override
    public List<Map<String, Object>> collect(UUID userId) {
        return list(userId).stream()
                .map(g -> Map.<String, Object>of(
                        "nome", g.name(), "tipo", g.kind(),
                        "alvo", String.valueOf(g.targetAmount()),
                        "progresso", String.valueOf(g.progressPct())))
                .toList();
    }
}
