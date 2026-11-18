package br.com.investia.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AuditLogJpaRepository extends JpaRepository<AuditLog, UUID> {

    @Query("select a from AuditLog a where a.userId = ?1 order by a.createdAt desc limit 100")
    List<AuditLog> findRecentByUser(UUID userId);
}
