package br.com.investia.transactions.application;

import br.com.investia.transactions.api.dto.TransactionDtos.RecurringRequest;
import br.com.investia.transactions.api.dto.TransactionDtos.RecurringResponse;
import br.com.investia.transactions.domain.RecurringRule;
import br.com.investia.transactions.domain.Transaction;
import br.com.investia.transactions.domain.TransactionSource;
import br.com.investia.transactions.infrastructure.jpa.RecurringRuleJpaRepository;
import br.com.investia.shared.domain.ApiException;
import br.com.investia.shared.domain.BusinessException;
import br.com.investia.shared.domain.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Regras de recorrência: cadastro e geração automática de transações. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringService {

    private final RecurringRuleJpaRepository rules;
    private final TransactionService transactionService;

    @Transactional(readOnly = true)
    public List<RecurringResponse> list(UUID userId) {
        return rules.findAllByUserIdAndActiveTrue(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public RecurringResponse create(UUID userId, RecurringRequest req) {
        if (req.nextRun().isAfter(LocalDate.now().plusYears(1))) {
            throw new BusinessException("Data de início muito distante");
        }
        RecurringRule rule = rules.save(RecurringRule.builder()
                .userId(userId)
                .accountId(req.accountId())
                .categoryId(req.categoryId())
                .type(req.type())
                .amount(Money.of(req.amount()))
                .description(req.description().trim())
                .frequency(req.frequency())
                .dayOfMonth(req.dayOfMonth() == null ? req.nextRun().getDayOfMonth() : req.dayOfMonth())
                .nextRun(req.nextRun())
                .build());
        return toResponse(rule);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        RecurringRule rule = rules.findById(id)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Regra não encontrada"));
        rule.setActive(false);
        rules.save(rule);
    }

    /** Diário: materializa transações das regras vencidas. */
    @Scheduled(cron = "0 0 6 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void processDueRules() {
        LocalDate today = LocalDate.now();
        List<RecurringRule> due = rules.findByActiveTrueAndNextRunLessThanEqual(today);
        for (RecurringRule rule : due) {
            int guard = 0;
            while (!rule.getNextRun().isAfter(today) && guard++ < 60) {
                transactionService.create(rule.getUserId(), new br.com.investia.transactions.api.dto.TransactionDtos.TransactionRequest(
                        rule.getType(), rule.getAccountId(), null, rule.getCategoryId(), null,
                        rule.getAmount().value(), rule.getNextRun(), rule.getDescription(),
                        "Lançamento recorrente", null, null, null, null));
                rule.advance();
            }
            rules.save(rule);
        }
        if (!due.isEmpty()) {
            log.info("Regras recorrentes processadas: {}", due.size());
        }
    }

    private RecurringResponse toResponse(RecurringRule r) {
        return new RecurringResponse(r.getId().toString(), r.getAccountId().toString(),
                r.getCategoryId() == null ? null : r.getCategoryId().toString(),
                r.getType().name(), r.getAmount().value(), r.getDescription(),
                r.getFrequency().name(), r.getDayOfMonth(), r.getNextRun(), r.isActive());
    }
}
