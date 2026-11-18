package br.com.investia.identity.api;

import br.com.investia.identity.api.dto.AuthDtos.ChangePasswordRequest;
import br.com.investia.identity.api.dto.AuthDtos.ProfileResponse;
import br.com.investia.identity.api.dto.AuthDtos.UpdateProfileRequest;
import br.com.investia.identity.application.UserDataContributor;
import br.com.investia.identity.application.UserService;
import br.com.investia.identity.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Usuário", description = "Perfil e direitos do titular (LGPD)")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final java.util.List<UserDataContributor> contributors;

    @Operation(summary = "Perfil do usuário autenticado")
    @GetMapping("/me")
    public ProfileResponse me() {
        return userService.getProfile(SecurityUtils.currentUserId());
    }

    @Operation(summary = "Atualiza perfil de investidor")
    @PutMapping("/me")
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateProfile(SecurityUtils.currentUserId(), req);
    }

    @Operation(summary = "Altera a senha")
    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                               HttpServletRequest http) {
        userService.changePassword(SecurityUtils.currentUserId(), req, http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Exportação de dados do titular (LGPD)")
    @GetMapping("/me/export")
    public Map<String, Object> export() {
        var userId = SecurityUtils.currentUserId();
        Map<String, Object> data = new LinkedHashMap<>(userService.exportUserData(userId));
        for (UserDataContributor contributor : contributors) {
            data.put(contributor.key(), contributor.collect(userId));
        }
        return data;
    }

    @Operation(summary = "Exclusão da conta e dos dados pessoais (LGPD)")
    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(HttpServletRequest http) {
        userService.deleteAccount(SecurityUtils.currentUserId(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
