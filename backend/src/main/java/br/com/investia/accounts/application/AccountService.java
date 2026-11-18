package br.com.investia.accounts.application;

import br.com.investia.accounts.api.dto.AccountDtos.*;
import br.com.investia.accounts.domain.Account;
import br.com.investia.accounts.domain.AccountType;
import br.com.investia.accounts.domain.CreditCard;
import br.com.investia.accounts.infrastructure.jpa.AccountJpaRepository;
import br.com.investia.accounts.infrastructure.jpa.CreditCardJpaRepository;
import br.com.investia.identity.application.UserDataContributor;
import br.com.investia.shared.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Casos de uso de contas e cartões. */
@Service
@RequiredArgsConstructor
public class AccountService implements UserDataContributor {

    private final AccountJpaRepository accounts;
    private final CreditCardJpaRepository creditCards;

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UUID userId) {
        Map<UUID, CreditCard> byAccount = creditCards.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(CreditCard::getAccountId, c -> c, (a, b) -> a));
        return accounts.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(a -> toResponse(userId, a, byAccount.get(a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID userId, UUID id) {
        Account account = find(userId, id);
        return toResponse(userId, account,
                creditCards.findByUserIdAndAccountId(userId, account.getId()).orElse(null));
    }

    @Transactional
    public AccountResponse create(UUID userId, AccountRequest req) {
        Account account = accounts.save(Account.builder()
                .userId(userId)
                .name(req.name().trim())
                .type(req.type())
                .institution(req.institution())
                .initialBalance(req.initialBalance() == null
                        ? Money.ZERO : Money.of(req.initialBalance()))
                .color(req.color())
                .build());

        applyCard(userId, account, req);
        return toResponse(userId, account,
                creditCards.findByUserIdAndAccountId(userId, account.getId()).orElse(null));
    }

    @Transactional
    public AccountResponse update(UUID userId, UUID id, AccountRequest req) {
        Account account = find(userId, id);
        account.setName(req.name().trim());
        account.setType(req.type());
        account.setInstitution(req.institution());
        account.setInitialBalance(req.initialBalance() == null
                ? Money.ZERO : Money.of(req.initialBalance()));
        account.setColor(req.color());
        Account saved = accounts.save(account);

        applyCard(userId, saved, req);
        return toResponse(userId, saved,
                creditCards.findByUserIdAndAccountId(userId, saved.getId()).orElse(null));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        accounts.delete(find(userId, id));
    }

    @Transactional
    public CreditCardResponse configureCreditCard(UUID userId, CreditCardRequest req) {
        Account account = find(userId, UUID.fromString(req.accountId()));
        // Qualquer conta pode ter cartão de crédito (ex.: conta corrente Nubank com débito e crédito)
        CreditCard card = creditCards.findByUserIdAndAccountId(userId, account.getId())
                .orElseGet(() -> CreditCard.builder().userId(userId).accountId(account.getId()).build());
        card.setBrand(req.brand());
        card.setClosingDay(req.closingDay());
        card.setDueDay(req.dueDay());
        card.setLimitTotal(Money.of(req.limitTotal()));
        card.setLimitUsed(req.limitUsed() == null ? Money.ZERO : Money.of(req.limitUsed()));
        creditCards.save(card);
        return toCardResponse(card);
    }

    /**
     * Cria, atualiza ou remove o cartão de crédito vinculado à conta.
     * O cartão é um complemento da conta, não um tipo de conta separado.
     */
    private void applyCard(UUID userId, Account account, AccountRequest req) {
        var existing = creditCards.findByUserIdAndAccountId(userId, account.getId());

        if (Boolean.FALSE.equals(req.hasCreditCard())) {
            existing.ifPresent(creditCards::delete);
            return;
        }

        boolean wantsCard = Boolean.TRUE.equals(req.hasCreditCard())
                || account.getType() == AccountType.CREDIT_CARD
                || req.closingDay() != null || req.dueDay() != null
                || req.limitTotal() != null || req.limitUsed() != null;
        if (!wantsCard) {
            return;
        }

        CreditCard card = existing.orElseGet(() -> CreditCard.builder()
                .userId(userId)
                .accountId(account.getId())
                .closingDay((short) 1)
                .dueDay((short) 10)
                .build());
        if (req.cardBrand() != null) {
            card.setBrand(req.cardBrand());
        }
        if (req.closingDay() != null) {
            card.setClosingDay(req.closingDay().shortValue());
        }
        if (req.dueDay() != null) {
            card.setDueDay(req.dueDay().shortValue());
        }
        if (req.limitTotal() != null) {
            card.setLimitTotal(Money.of(req.limitTotal()));
        }
        if (req.limitUsed() != null) {
            card.setLimitUsed(Money.of(req.limitUsed()));
        }
        creditCards.save(card);
    }

    @Transactional(readOnly = true)
    public List<CreditCardResponse> listCreditCards(UUID userId) {
        return creditCards.findAllByUserId(userId).stream()
                .map(this::toCardResponse)
                .toList();
    }

    private CreditCardResponse toCardResponse(CreditCard c) {
        BigDecimal total = c.getLimitTotal().value();
        BigDecimal used = c.getLimitUsed() == null ? BigDecimal.ZERO : c.getLimitUsed().value();
        return new CreditCardResponse(c.getId().toString(), c.getAccountId().toString(),
                c.getBrand(), c.getClosingDay(), c.getDueDay(), total, used, total.subtract(used));
    }

    private Account find(UUID userId, UUID id) {
        Account account = accounts.findById(id)
                .orElseThrow(() -> ApiException.notFound("Conta não encontrada"));
        if (!account.getUserId().equals(userId)) {
            // isolamento: nunca expor dado de outro usuário
            throw ApiException.notFound("Conta não encontrada");
        }
        return account;
    }

    private AccountResponse toResponse(UUID userId, Account a, CreditCard card) {
        var balance = accounts.computedBalance(userId, a.getId());
        return new AccountResponse(a.getId().toString(), a.getName(), a.getType().name(),
                a.getInstitution(), a.getInitialBalance().value(), balance, a.getColor(), a.isArchived(),
                card == null ? null : toCardResponse(card));
    }

    /** Contribuição para a exportação LGPD. */
    @Override
    public String key() {
        return "contas";
    }

    @Override
    public List<Map<String, Object>> collect(UUID userId) {
        return list(userId).stream()
                .map(a -> Map.<String, Object>of(
                        "nome", a.name(), "tipo", a.type(), "saldo", String.valueOf(a.balance())))
                .toList();
    }
}
