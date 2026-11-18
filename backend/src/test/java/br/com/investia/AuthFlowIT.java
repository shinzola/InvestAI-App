package br.com.investia;

import br.com.investia.identity.domain.User;
import br.com.investia.identity.infrastructure.jpa.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração do fluxo de autenticação (requer Docker).
 * Executado via: mvn verify -DskipITs=false
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    UserJpaRepository users;

    @Test
    void registrarLoginEAcessarPerfil() {
        var email = "joao@test.com";
        var register = http.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "senha-segura-123",
                        "fullName", "João Silva", "acceptTerms", true), Map.class);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var login = http.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "senha-segura-123"), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).containsKeys("accessToken", "refreshToken");

        String token = (String) login.getBody().get("accessToken");
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);

        var me = http.exchange("/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email")).isEqualTo(email);

        User persisted = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(persisted.getPasswordHash()).startsWith("$2"); // bcrypt
    }

    @Test
    void senhaIncorretaRetorna401() {
        var email = "maria@test.com";
        http.postForEntity("/api/v1/auth/register",
                Map.of("email", email, "password", "senha-segura-123",
                        "fullName", "Maria", "acceptTerms", true), Map.class);

        var bad = http.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "errada"), Map.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rotasProtegidasRetornam401SemToken() {
        var me = http.getForEntity("/api/v1/transactions", Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
