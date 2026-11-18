package br.com.investia.transactions.application;

import br.com.investia.accounts.application.AccountService;
import br.com.investia.transactions.api.dto.TransactionDtos.TransactionRequest;
import br.com.investia.transactions.api.dto.TransactionDtos.TransactionResponse;
import br.com.investia.transactions.api.dto.TransactionDtos.TransactionPage;
import br.com.investia.transactions.application.events.TransactionCreatedEvent;
import br.com.investia.transactions.domain.*;
import br.com.investia.transactions.infrastructure.jpa.TransactionAttachmentJpaRepository;
import br.com.investia.transactions.infrastructure.jpa.TransactionJpaRepository;
import br.com.investia.shared.api.PageResult;
import br.com.investia.shared.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

/** Casos de uso de transações: lançamento, parcelamento, transferências. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionJpaRepository transactions;
    private final TransactionAttachmentJpaRepository attachments;
    private final CategoryService categoryService;
    private final AccountService accountService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public PageResult<TransactionResponse> list(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "date"));
        var result = transactions.findAllByUserIdOrderByDateDesc(userId, pageable);
        List<TransactionResponse> content = result.getContent().stream().map(t -> toResponse(t)).toList();
        return new PageResult<>(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listByPeriod(UUID userId, LocalDate start, LocalDate end) {
        return transactions.findAllByUserIdAndDateBetweenOrderByDateAsc(userId, start, end)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID userId, UUID id) {
        return toResponse(find(userId, id));
    }

    /** Cria transação; para compras no cartão com "installments" gera parcelas. */
    @Transactional
    public TransactionResponse create(UUID userId, TransactionRequest req) {
        validate(userId, req);
        int installments = req.installments() == null ? 1 : req.installments();
        UUID group = installments > 1 ? UUID.randomUUID() : null;

        Transaction first = null;
        for (int i = 1; i <= installments; i++) {
            Transaction t = transactions.save(Transaction.builder()
                    .userId(userId)
                    .accountId(req.accountId())
                    .destinationAccountId(req.destinationAccountId())
                    .categoryId(req.categoryId())
                    .subcategoryId(req.subcategoryId())
                    .type(req.type())
                    .incomeKind(req.type() == TransactionType.INCOME ? req.incomeKind() : null)
                    .amount(Money.of(req.amount())) // valor por parcela; total informado na UI
                    .date(req.date().plusMonths(i - 1L))
                    .description(installments > 1
                            ? "%s (%d/%d)".formatted(req.description().trim(), i, installments)
                            : req.description().trim())
                    .notes(req.notes())
                    .tags(req.tags() == null ? List.of() : req.tags())
                    .status(req.status() == null ? TransactionStatus.CLEARED : req.status())
                    .installmentGroup(group)
                    .installmentNumber(installments > 1 ? i : null)
                    .installmentTotal(installments > 1 ? installments : null)
                    .build());
            if (i == 1) {
                first = t;
            }
            publishCreated(t);
        }
        return toResponse(first);
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID id, TransactionRequest req) {
        validate(userId, req);
        Transaction t = find(userId, id);
        t.setType(req.type());
        t.setIncomeKind(req.type() == TransactionType.INCOME ? req.incomeKind() : null);
        t.setAccountId(req.accountId());
        t.setDestinationAccountId(req.destinationAccountId());
        t.setCategoryId(req.categoryId());
        t.setSubcategoryId(req.subcategoryId());
        t.setAmount(Money.of(req.amount()));
        t.setDate(req.date());
        t.setDescription(req.description().trim());
        t.setNotes(req.notes());
        t.setTags(req.tags() == null ? List.of() : req.tags());
        t.setStatus(req.status() == null ? TransactionStatus.CLEARED : req.status());
        return toResponse(transactions.save(t));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        transactions.delete(find(userId, id));
    }

    @Transactional
    public void addAttachment(UUID userId, UUID transactionId, String fileName,
                              String contentType, String url) {
        Transaction t = find(userId, transactionId);
        attachments.save(TransactionAttachment.builder()
                .userId(userId)
                .transactionId(t.getId())
                .fileName(fileName)
                .contentType(contentType)
                .url(url)
                .build());
    }

    private void validate(UUID userId, TransactionRequest req) {
        accountService.get(userId, req.accountId()); // garante posse da conta
        if (req.type() == TransactionType.TRANSFER && req.destinationAccountId() == null) {
            throw new BusinessException("Transferência exige conta de destino");
        }
        if (req.destinationAccountId() != null) {
            accountService.get(userId, req.destinationAccountId());
        }
        if (req.categoryId() != null) {
            categoryService.find(userId, req.categoryId());
        }
        if (req.subcategoryId() != null) {
            categoryService.find(userId, req.subcategoryId());
        }
    }

    public Transaction find(UUID userId, UUID id) {
        Transaction t = transactions.findById(id)
                .orElseThrow(() -> ApiException.notFound("Transação não encontrada"));
        if (!t.getUserId().equals(userId)) {
            throw ApiException.notFound("Transação não encontrada");
        }
        return t;
    }

    private void publishCreated(Transaction t) {
        events.publishEvent(new TransactionCreatedEvent(
                t.getUserId(), t.getId(), t.getCategoryId() != null ? t.getCategoryId() : t.getSubcategoryId(),
                t.getDescription(), t.getAmount(), t.getType(), t.getSource(), t.getDate()));
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId().toString(), t.getType().name(), t.getAccountId().toString(),
                t.getDestinationAccountId() == null ? null : t.getDestinationAccountId().toString(),
                t.getCategoryId() == null ? null : t.getCategoryId().toString(),
                t.getSubcategoryId() == null ? null : t.getSubcategoryId().toString(),
                t.getAmount().value(), t.getDate(), t.getDescription(), t.getNotes(),
                t.getTags(), t.getStatus().name(), t.getSource().name(),
                t.getIncomeKind() == null ? null : t.getIncomeKind().name(),
                t.getInstallmentNumber(), t.getInstallmentTotal());
    }
}
