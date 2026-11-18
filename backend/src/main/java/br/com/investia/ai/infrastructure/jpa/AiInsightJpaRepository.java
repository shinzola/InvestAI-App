package br.com.investia.ai.infrastructure.jpa;

import br.com.investia.ai.domain.AiInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AiInsightJpaRepository extends JpaRepository<AiInsight, UUID> {

    @Query("""
            SELECT i FROM AiInsight i
            WHERE i.userId = :userId
            ORDER BY i.createdAt DESC
            LIMIT 50
            """)
    List<AiInsight> findRecent(UUID userId);
}
