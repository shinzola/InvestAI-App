package br.com.investia.budgeting.application;

import br.com.investia.budgeting.api.dto.BudgetDtos.*;
import br.com.investia.budgeting.application.events.BudgetAlertRaisedEvent;
import br.com.investia.budgeting.domain.Budget;
import br.com.investia.budgeting.domain.BudgetItem;
import br.com.investia.budgeting.infrastructure.jpa.BudgetItemJpaRepository;
import br.com.investia.budgeting.infrastructure.jpa.BudgetJpaRepository;
import br.com.investia.shared.domain.BusinessException;
import br.com.investia.shared.domain.Money;
import br.com.investia.shared.domain.Percentage;
import br.com.investia.transactions.application.CategoryService;
import br.com.investia.transactions.application.TransactionQueryPort;
import br.com.investia.transactions.domain.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Orçamento mensal (método de envelopes): cálculo de gasto por categoria,
 * alertas em 80%/100% e detecção de gastos atípicos (vs média de 3 meses).
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetJpaRepository budgets;
    private final BudgetItemJpaRepository items;
    private final CategoryService categoryService;
    private final TransactionQueryPort transactionQuery;
    private final ApplicationEventPublisher events;

    /** Retorna (criando se necessário) o orçamento do mês com números calculados. */
    @Transactional
    public BudgetResponse getBudget(UUID userId, int year, int month) {
        if (month < 1 || month > 12) {
            throw new BusinessException("Mês inválido");
        }
        LocalDate ref = LocalDate.of(year, month, 1);
        Budget budget = budgets.findByUserIdAndMonth(userId, ref).orElseGet(() ->
                budgets.save(Budget.builder().userId(userId).month(ref).build()));

        var period = br.com.investia.shared.domain.PeriodRange.ofMonth(year, month);
        var prev = ref.minusMonths(1);
        var prevPeriod = br.com.investia.shared.domain.PeriodRange.ofMonth(prev.getYear(), prev.getMonthValue());

        Map<UUID, BigDecimal> spentByCategory =
                transactionQuery.expenseTotalsByCategory(userId, period.start(), period.end());
        BigDecimal previousMonthSpent = transactionQuery.expense(userId, prevPeriod.start(), prevPeriod.end());

        List<BudgetItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalLimit = BigDecimal.ZERO;
        BigDecimal totalSpent = BigDecimal.ZERO;

        Map<UUID, BigDecimal> avg3m = transactionQuery.expenseTotalsByCategory(userId,
                ref.minusMonths(3).withDayOfMonth(1), ref.minusDays(1));

        for (BudgetItem item : items.findAllByBudgetId(budget.getId())) {
            Category category = categoryService.find(userId, item.getCategoryId());
            BigDecimal spent = spentByCategory.getOrDefault(item.getCategoryId(), BigDecimal.ZERO);
            BigDecimal limit = item.getLimitAmount().value();
            double pct = limit.signum() == 0
                    ? 0
                    : spent.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP).doubleValue();
            BigDecimal remaining = limit.subtract(spent);
            String level = pct >= 100 ? "AT100" : pct >= item.getAlertThresholdPct() ? "AT80" : "OK";
            boolean atypical = isAtypical(spent, avg3m.get(item.getCategoryId()));

            itemResponses.add(new BudgetItemResponse(category.getId().toString(), category.getName(),
                    limit, spent, remaining, pct, item.getAlertThresholdPct(), level, atypical));

            totalLimit = totalLimit.add(limit);
            totalSpent = totalSpent.add(spent);

            if (!"OK".equals(level) && period.contains(LocalDate.now())) {
                events.publishEvent(new BudgetAlertRaisedEvent(userId, budget.getId(),
                        item.getCategoryId(), category.getName(), level,
                        Money.of(spent), Money.of(limit)));
            }
        }

        double totalPct = totalLimit.signum() == 0
                ? 0
                : totalSpent.multiply(BigDecimal.valueOf(100)).divide(totalLimit, 2, RoundingMode.HALF_UP).doubleValue();
        double momPct = previousMonthSpent.signum() == 0
                ? 0
                : totalSpent.subtract(previousMonthSpent)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(previousMonthSpent, 2, RoundingMode.HALF_UP).doubleValue();

        return new BudgetResponse(ref.toString(), totalLimit, totalSpent, totalPct,
                itemResponses, previousMonthSpent, momPct);
    }

    /** Substitui os envelopes do mês (upsert simples e transacional). */
    @Transactional
    public BudgetResponse upsertBudget(UUID userId, int year, int month, BudgetUpsertRequest req) {
        LocalDate ref = LocalDate.of(year, month, 1);
        Budget budget = budgets.findByUserIdAndMonth(userId, ref).orElseGet(() ->
                budgets.save(Budget.builder().userId(userId).month(ref).build()));

        for (BudgetItemRequest itemReq : req.items()) {
            categoryService.find(userId, itemReq.categoryId()); // valida posse
            BudgetItem item = items.findByBudgetIdAndCategoryId(budget.getId(), itemReq.categoryId())
                    .orElseGet(() -> BudgetItem.builder()
                            .userId(userId)
                            .budgetId(budget.getId())
                            .categoryId(itemReq.categoryId())
                            .build());
            item.setLimitAmount(Money.of(itemReq.limitAmount()));
            if (itemReq.alertThresholdPct() != null) {
                item.setAlertThresholdPct(itemReq.alertThresholdPct());
            }
            items.save(item);
        }
        return getBudget(userId, year, month);
    }

    /** Gasto é atípico quando supera 2x a média dos 3 meses anteriores (mín. R$ 100). */
    private boolean isAtypical(BigDecimal spent, BigDecimal avg3m) {
        if (avg3m == null || avg3m.signum() == 0) {
            return false;
        }
        return spent.compareTo(new BigDecimal("100")) > 0
                && spent.compareTo(avg3m.multiply(BigDecimal.valueOf(2))) > 0;
    }
}
