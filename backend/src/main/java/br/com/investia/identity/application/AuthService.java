package br.com.investia.identity.application;

import br.com.investia.identity.api.dto.AuthDtos.*;
import br.com.investia.identity.application.events.UserRegisteredEvent;
import br.com.investia.identity.domain.User;
import br.com.investia.identity.infrastructure.jpa.*;
import br.com.investia.shared.audit.AuditService;
import br.com.investia.shared.domain.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Casos de uso de autenticação: registro, login com bloqueio progressivo,
 * refresh token rotativo, recuperação de senha.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration RESET_TTL = Duration.ofMinutes(30);

    private final UserJpaRepository users;
    private final UserProfileJpaRepository profiles;
    private final RefreshTokenJpaRepository refreshTokens;
    private final PasswordResetJpaRepository passwordResets;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenGenerator tokens;
    private final ApplicationEventPublisher events;
    private final AuditService audit;

    @Transactional
    public User register(RegisterRequest req, String ip) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        User user = users.save(User.builder()
                .email(req.email().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName().trim())
                .status(br.com.investia.identity.domain.UserStatus.ACTIVE)
                .acceptedTermsAt(Boolean.TRUE.equals(req.acceptTerms()) ? Instant.now() : null)
                .build());

        profiles.save(br.com.investia.identity.domain.UserProfile.builder()
                .userId(user.getId())
                .build());

        audit.record(user.getId(), "USER_REGISTERED", "users", user.getId(), ip, null);
        // Módulos interessados (ex.: categorias padrão) reagem a este evento.
        events.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail()));
        return user;
    }

    @Transactional
    public TokenResponse login(LoginRequest req, String ip) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas"));

        Instant now = Instant.now();
        if (user.isLocked(now)) {
            throw new ApiException(HttpStatus.LOCKED, "Conta temporariamente bloqueada. Tente mais tarde.");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            user.registerFailure(MAX_FAILURES, LOCK_DURATION, now);
            users.save(user);
            audit.record(user.getId(), "LOGIN_FAILED", "users", user.getId(), ip, null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
        }
        user.registerSuccess();
        users.save(user);

        return issueTokens(user, ip);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest req, String ip) {
        String hash = tokens.sha256(req.refreshToken());
        var stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token inválido"));

        Instant now = Instant.now();
        if (stored.getRevokedAt() != null) {
            // Reuso de token rotacionado: revoga toda a família por segurança
            refreshTokens.revokeAllByUserId(stored.getUserId(), Instant.now());
            audit.record(stored.getUserId(), "REFRESH_TOKEN_REUSE", "refresh_tokens", stored.getId(), ip, null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token revogado");
        }
        if (!stored.isValid(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expirado");
        }
        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuário inexistente"));

        stored.setRevokedAt(now);
        refreshTokens.save(stored);
        return issueTokens(user, ip);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(tokens.sha256(refreshToken))
                .ifPresent(t -> {
                    t.setRevokedAt(Instant.now());
                    refreshTokens.save(t);
                });
    }

    /** Sempre responde 202 para não revelar existência do e-mail. */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        users.findByEmailIgnoreCase(req.email()).ifPresent(user -> {
            String token = tokens.newOpaqueToken();
            passwordResets.save(br.com.investia.identity.infrastructure.jpa.PasswordResetRecord.builder()
                    .userId(user.getId())
                    .tokenHash(tokens.sha256(token))
                    .expiresAt(Instant.now().plus(RESET_TTL))
                    .build());
            // Em produção, envio por e-mail via SMTP; no MVP registramos o link no log.
            log.info("Token de redefinição gerado para usuário {}: /reset-password?token={}", user.getId(), token);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        var record = passwordResets.findByTokenHash(tokens.sha256(req.token()))
                .filter(r -> r.isValid(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Token de redefinição inválido ou expirado"));

        User user = users.findById(record.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.registerSuccess();
        users.save(user);
        record.setUsedAt(Instant.now());
        passwordResets.save(record);
        refreshTokens.revokeAllByUserId(user.getId(), Instant.now());
        audit.record(user.getId(), "PASSWORD_RESET", "users", user.getId(), null, null);
    }

    private TokenResponse issueTokens(User user, String ip) {
        String refresh = tokens.newOpaqueToken();
        var record = refreshTokens.save(br.com.investia.identity.infrastructure.jpa.RefreshTokenRecord.builder()
                .userId(user.getId())
                .tokenHash(tokens.sha256(refresh))
                .expiresAt(Instant.now().plus(Duration.ofDays(30)))
                .build());
        audit.record(user.getId(), "LOGIN_SUCCESS", "users", user.getId(), ip, null);
        return new TokenResponse(
                jwtService.generateAccessToken(user.getId(), user.getEmail()),
                refresh,
                jwtService.accessTtl().toMinutes());
    }
}
