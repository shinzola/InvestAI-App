package br.com.investia.identity.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Porta de aplicação: permite que cada módulo contribua com seus dados
 * para a exportação LGPD do titular, sem acoplamento direto.
 */
public interface UserDataContributor {

    /** Chave da seção no JSON de exportação (ex.: "transacoes"). */
    String key();

    List<Map<String, Object>> collect(UUID userId);
}
