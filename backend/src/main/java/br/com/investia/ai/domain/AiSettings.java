package br.com.investia.ai.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Provedor de LLM configurado pelo próprio usuário em Ajustes. */
@Entity
@Table(name = "ai_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Column(nullable = false, length = 120)
    private String model;

    @Column(name = "api_key", nullable = false, length = 512)
    private String apiKey;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
