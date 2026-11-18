package br.com.investia.planning.application;

import br.com.investia.accounts.domain.CreditCard;
import br.com.investia.accounts.infrastructure.jpa.CreditCardJpaRepository;
import br.com.investia.planning.api.dto.PlanningDtos.*;
import br.com.investia.planning.domain.PlannedExpense;
import br.com.investia.planning.domain.PlannedExpenseKind;
import br.com.investia.planning.domain.PlannedExpenseStatus;
import br.com.investia.planning.infrastructure.jpa.PlannedExpenseJpaRepository;
import br.com.investia.shared.domain.ApiException;
import br.com.investia.shared.domain.Money;
import br.com.investia.transactions.application.TransactionQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Planejamento mensal de gastos: fixos e individuais, agrupados por data. */
@Service
@RequiredArgsConstructor
public class PlanningService {

    private final PlannedExpenseJpaRepository planned;
    private final TransactionQueryPort transactions;
    private final CreditCardJpaRepository creditCards;

    @Transactional(readOnly = true)
    public List<PlannedExpenseResponse> list(UUID userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        return planned.findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(
                        userId, ym.atDay(1), ym.atEndOfMonth())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public PlannedExpenseResponse create(UUID userId, PlannedExpenseRequest req) {
        PlannedExpense e = PlannedExpense.builder()
                .userId(userId)
                .description(req.description().trim())
                .amount(Money.of(req.amount()))
                .categoryId(req.categoryId())
                .dueDate(req.dueDate())
                .kind(req.kind())
                .status(req.status() == null ? PlannedExpenseStatus.PLANNED : req.status())
                .notes(req.notes())
                .cardAccountId(req.cardAccountId())
                .build();
        applyInvoiceDates(e);
        PlannedExpense saved = planned.save(e);
        if (saved.getStatus() == PlannedExpenseStatus.PAID) {
            adjustLimit(saved, saved.getAmount().value());
        }
        return toResponse(saved);
    }

    @Transactional
    public PlannedExpenseResponse update(UUID userId, UUID id, PlannedExpenseRequest req) {
        PlannedExpense e = find(userId, id);
        BigDecimal previousAmount = e.getAmount().value();
        e.setDescription(req.description().trim());
        e.setAmount(Money.of(req.amount()));
        e.setCategoryId(req.categoryId());
        e.setDueDate(req.dueDate());
        e.setKind(req.kind());
        e.setStatus(req.status() == null ? e.getStatus() : req.status());
        e.setNotes(req.notes());
        e.setCardAccountId(req.cardAccountId());
        applyInvoiceDates(e);
        PlannedExpense saved = planned.save(e);
        if (saved.getStatus() == PlannedExpenseStatus.PAID) {
            // o limite já havia sido liberado pelo valor antigo: ajusta a diferença
            adjustLimit(saved, previousAmount.subtract(saved.getAmount().value()));
        }
        return toResponse(saved);
    }

    /**
     * Alterna entre previsto e pago. No cartão de crédito, quitar a fatura
     * devolve o valor ao limite disponível; desmarcar volta a consumir.
     */
    @Transactional
    public PlannedExpenseResponse togglePaid(UUID userId, UUID id) {
        PlannedExpense e = find(userId, id);
        boolean paying = e.getStatus() != PlannedExpenseStatus.PAID;
        e.setStatus(paying ? PlannedExpenseStatus.PAID : PlannedExpenseStatus.PLANNED);
        PlannedExpense saved = planned.save(e);
        adjustLimit(saved, paying ? saved.getAmount().value() : saved.getAmount().value().negate());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        planned.delete(find(userId, id));
    }

    @Transactional(readOnly = true)
    public PlanningSummary summary(UUID userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<PlannedExpense> items = planned.findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(
                userId, ym.atDay(1), ym.atEndOfMonth());

        BigDecimal fixed = sum(items, PlannedExpenseKind.FIXED);
        BigDecimal variable = sum(items, PlannedExpenseKind.VARIABLE);
        BigDecimal creditCard = sum(items, PlannedExpenseKind.CREDIT_CARD);
        BigDecimal total = fixed.add(variable).add(creditCard);
        BigDecimal paid = items.stream()
                .filter(i -> i.getStatus() == PlannedExpenseStatus.PAID)
                .map(i -> i.getAmount().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = total.subtract(paid);
        int paidCount = (int) items.stream()
                .filter(i -> i.getStatus() == PlannedExpenseStatus.PAID).count();

        BigDecimal income = transactions.income(userId, ym.atDay(1), ym.atEndOfMonth());
        BigDecimal available = income.subtract(total);

        return new PlanningSummary(fixed, variable, creditCard, total, paid, pending,
                items.size(), paidCount, income, available);
    }

    private BigDecimal sum(List<PlannedExpense> items, PlannedExpenseKind kind) {
        return items.stream()
                .filter(i -> i.getKind() == kind)
                .map(i -> i.getAmount().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Deriva fechamento e vencimento da fatura a partir do cartão da conta.
     * O usuário não digita essas datas: elas vêm do cadastro do cartão.
     */
    private void applyInvoiceDates(PlannedExpense e) {
        CreditCard card = cardOf(e);
        if (card == null) {
            e.setInvoiceClosingDate(null);
            e.setInvoiceDueDate(null);
            return;
        }
        YearMonth ref = YearMonth.from(e.getDueDate());
        e.setInvoiceClosingDate(clampDay(ref, card.getClosingDay()));
        YearMonth dueRef = card.getDueDay() > card.getClosingDay() ? ref : ref.plusMonths(1);
        e.setInvoiceDueDate(clampDay(dueRef, card.getDueDay()));
    }

    private LocalDate clampDay(YearMonth ym, int day) {
        return ym.atDay(Math.min(Math.max(day, 1), ym.lengthOfMonth()));
    }

    private CreditCard cardOf(PlannedExpense e) {
        if (e.getKind() != PlannedExpenseKind.CREDIT_CARD || e.getCardAccountId() == null) {
            return null;
        }
        return creditCards.findByUserIdAndAccountId(e.getUserId(), e.getCardAccountId()).orElse(null);
    }

    /**
     * Ajusta o limite usado do cartão. Valor positivo libera limite (fatura paga),
     * valor negativo volta a consumir (fatura reaberta).
     */
    private void adjustLimit(PlannedExpense e, BigDecimal releaseDelta) {
        if (releaseDelta.signum() == 0) {
            return;
        }
        CreditCard card = cardOf(e);
        if (card == null) {
            return;
        }
        BigDecimal used = card.getLimitUsed() == null ? BigDecimal.ZERO : card.getLimitUsed().value();
        BigDecimal next = used.subtract(releaseDelta);
        if (next.signum() < 0) {
            next = BigDecimal.ZERO;
        }
        card.setLimitUsed(Money.of(next));
        creditCards.save(card);
    }

    private PlannedExpense find(UUID userId, UUID id) {
        PlannedExpense e = planned.findById(id)
                .orElseThrow(() -> ApiException.notFound("Gasto planejado não encontrado"));
        if (!e.getUserId().equals(userId)) {
            throw ApiException.notFound("Gasto planejado não encontrado");
        }
        return e;
    }

    private PlannedExpenseResponse toResponse(PlannedExpense e) {
        return new PlannedExpenseResponse(
                e.getId().toString(), e.getDescription(), e.getAmount().value(),
                e.getCategoryId() == null ? null : e.getCategoryId().toString(),
                e.getDueDate(), e.getKind().name(), e.getStatus().name(), e.getNotes(),
                e.getCardAccountId() == null ? null : e.getCardAccountId().toString(),
                e.getInvoiceClosingDate(), e.getInvoiceDueDate());
    }
}
