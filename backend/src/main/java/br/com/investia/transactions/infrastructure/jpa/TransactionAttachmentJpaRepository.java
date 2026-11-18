package br.com.investia.transactions.infrastructure.jpa;

import br.com.investia.transactions.domain.TransactionAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionAttachmentJpaRepository extends JpaRepository<TransactionAttachment, UUID> {
    List<TransactionAttachment> findAllByUserIdAndTransactionId(UUID userId, UUID transactionId);
}
