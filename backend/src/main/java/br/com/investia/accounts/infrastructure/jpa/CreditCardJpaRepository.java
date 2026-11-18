package br.com.investia.accounts.infrastructure.jpa;

import br.com.investia.accounts.domain.CreditCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditCardJpaRepository extends JpaRepository<CreditCard, UUID> {
    Optional<CreditCard> findByUserIdAndAccountId(UUID userId, UUID accountId);

    List<CreditCard> findAllByUserId(UUID userId);
}
