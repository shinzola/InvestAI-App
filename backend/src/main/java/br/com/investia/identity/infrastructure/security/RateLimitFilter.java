package br.com.investia.identity.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Rate limiting por IP com janela fixa no Redis.
 * Chave: ratelimit:{bucket}:{ip}:{janela de 1 minuto}
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String GENERAL = "general";
    private static final String AUTH = "auth";

    private final StringRedisTemplate redis;

    @Value("${app.ratelimit.default-per-minute:120}")
    private int defaultLimit;

    @Value("${app.ratelimit.auth-per-minute:10}")
    private int authLimit;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String bucket = request.getRequestURI().startsWith("/api/v1/auth/") ? AUTH : GENERAL;
        int limit = AUTH.equals(bucket) ? authLimit : defaultLimit;
        String window = String.valueOf(Instant.now().getEpochSecond() / 60);
        String key = "ratelimit:%s:%s:%s".formatted(bucket, clientIp(request), window);

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofMinutes(2));
        }
        if (count != null && count > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de requisições excedido. Tente novamente em instantes.");
            response.getWriter().write(
                    "{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"%s\"}"
                            .formatted(pd.getDetail()));
            return;
        }
        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        return fwd != null ? fwd.split(",")[0].trim() : request.getRemoteAddr();
    }
}
