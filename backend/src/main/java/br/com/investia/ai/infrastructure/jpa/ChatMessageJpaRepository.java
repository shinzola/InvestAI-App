package br.com.investia.ai.infrastructure.jpa;

import br.com.investia.ai.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    void deleteAllByUserId(UUID userId);
}
