package br.com.investia.identity.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetJpaRepository extends JpaRepository<PasswordResetRecord, UUID> {
    Optional<PasswordResetRecord> findByTokenHash(String tokenHash);

    long deleteByExpiresAtBefore(Instant cutoff);
}
