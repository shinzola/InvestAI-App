package br.com.investia.identity.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/** Acesso ao usuário autenticado (isolamento multi-tenant). */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** Id do usuário autenticado (subject do JWT). */
    public static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("Usuário não autenticado");
        }
        return UUID.fromString(auth.getName());
    }
}
