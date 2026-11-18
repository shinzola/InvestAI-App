package br.com.investia.identity.application.events;

import java.util.UUID;

/** Evento de domínio publicado quando um usuário se registra. */
public record UserRegisteredEvent(UUID userId, String email) {
}
