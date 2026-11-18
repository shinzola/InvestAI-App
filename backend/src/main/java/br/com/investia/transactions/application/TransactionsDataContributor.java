package br.com.investia.transactions.application;

import br.com.investia.identity.application.UserDataContributor;
import br.com.investia.transactions.infrastructure.jpa.TransactionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contribui transações para a exportação LGPD. */
@Component
@RequiredArgsConstructor
public class TransactionsDataContributor implements UserDataContributor {

    private final TransactionJpaRepository transactions;

    @Override
    public String key() {
        return "transacoes";
    }

    @Override
    public List<Map<String, Object>> collect(UUID userId) {
        return transactions.findAllByUserIdOrderByDateDesc(userId, org.springframework.data.domain.PageRequest.of(0, 5000))
                .getContent().stream()
                .map(t -> Map.<String, Object>of(
                        "data", t.getDate().toString(),
                        "descricao", t.getDescription(),
                        "tipo", t.getType().name(),
                        "valor", t.getAmount().value()))
                .toList();
    }
}
