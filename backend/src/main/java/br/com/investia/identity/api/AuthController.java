package br.com.investia.identity.api;

import br.com.investia.identity.api.dto.AuthDtos.*;
import br.com.investia.identity.application.AuthService;
import br.com.investia.identity.domain.User;
import br.com.investia.identity.application.JwtService;
import br.com.investia.shared.web.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Auth", description = "Autenticação e ciclo de vida da sessão")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @Operation(summary = "Cadastro de usuário")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest req,
                                        HttpServletRequest http) {
        User user = authService.register(req, clientIp(http));
        return Map.of("id", user.getId(), "email", user.getEmail());
    }

    @Operation(summary = "Login (retorna access + refresh token)")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authService.login(req, clientIp(http));
    }

    @Operation(summary = "Rotaciona o refresh token")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        return authService.refresh(req, clientIp(http));
    }

    @Operation(summary = "Revoga o refresh token atual")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Solicita redefinição de senha")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Redefine a senha com o token recebido")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.noContent().build();
    }

    private String clientIp(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        return fwd != null ? fwd.split(",")[0].trim() : http.getRemoteAddr();
    }
}
