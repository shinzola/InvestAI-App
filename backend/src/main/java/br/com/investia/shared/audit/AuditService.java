package br.com.investia.shared.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/** Auditoria de operações críticas (LGPD / rastreabilidade). */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogJpaRepository repository;
    private final ObjectMapper objectMapper;

    public void record(UUID userId, String action, String entity, UUID entityId,
                       String ip, Map<String, Object> payload) {
        try {
            repository.save(AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .entity(entity)
                    .entityId(entityId)
                    .ip(ip)
                    .payload(objectMapper.writeValueAsString(payload == null ? Map.of() : payload))
                    .build());
        } catch (JsonProcessingException e) {
            // Auditoria nunca deve interromper a operação de negócio
            log.warn("Falha ao serializar payload de auditoria: {}", e.getMessage());
        }
    }
}
