package br.com.investia.ai.infrastructure.jpa;

import br.com.investia.ai.domain.AiSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiSettingsJpaRepository extends JpaRepository<AiSettings, UUID> {
}
