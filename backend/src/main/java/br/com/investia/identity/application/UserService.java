package br.com.investia.identity.application;

import br.com.investia.identity.api.dto.AuthDtos.ChangePasswordRequest;
import br.com.investia.identity.api.dto.AuthDtos.ProfileResponse;
import br.com.investia.identity.api.dto.AuthDtos.UpdateProfileRequest;
import br.com.investia.identity.domain.InvestorProfile;
import br.com.investia.identity.domain.User;
import br.com.investia.identity.domain.UserProfile;
import br.com.investia.identity.infrastructure.jpa.UserJpaRepository;
import br.com.investia.identity.infrastructure.jpa.UserProfileJpaRepository;
import br.com.investia.shared.audit.AuditService;
import br.com.investia.shared.domain.ApiException;
import br.com.investia.shared.domain.Money;
import br.com.investia.shared.domain.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Perfil do usuário e direitos do titular (LGPD: exportar e excluir). */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserJpaRepository users;
    private final UserProfileJpaRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        User user = user(userId);
        UserProfile profile = profile(userId);
        return new ProfileResponse(user.getEmail(), user.getFullName(),
                profile.getInvestorProfile().name(), profile.getBaseCurrency(), profile.getTimezone(),
                profile.getDesiredMonthlyIncome().value());
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        UserProfile profile = profile(userId);
        if (req.investorProfile() != null) {
            profile.setInvestorProfile(InvestorProfile.valueOf(req.investorProfile()));
        }
        if (req.timezone() != null && !req.timezone().isBlank()) {
            profile.setTimezone(req.timezone());
        }
        if (req.desiredMonthlyIncome() != null) {
            profile.setDesiredMonthlyIncome(Money.of(req.desiredMonthlyIncome()));
        }
        profiles.save(profile);
        return getProfile(userId);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req, String ip) {
        User user = user(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Senha atual incorreta");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        users.save(user);
        audit.record(userId, "PASSWORD_CHANGED", "users", userId, ip, null);
    }

    /** Exportação de dados do titular (LGPD art. 18) — dados de identidade. */
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(UUID userId) {
        User user = user(userId);
        UserProfile profile = profile(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("usuario", Map.of(
                "id", user.getId(), "email", user.getEmail(), "nome", user.getFullName(),
                "criadoEm", user.getCreatedAt()));
        data.put("perfil", Map.of(
                "perfilInvestidor", profile.getInvestorProfile(),
                "moeda", profile.getBaseCurrency(),
                "fuso", profile.getTimezone(),
                "rendaMensalDesejada", profile.getDesiredMonthlyIncome().value()));
        // Os demais módulos (contas, transações, carteira...) são exportados
        // por seus respectivos serviços, acionados pelo controller de exportação.
        return data;
    }

    /** Exclusão da conta e anonimização (LGPD art. 16/18). */
    @Transactional
    public void deleteAccount(UUID userId, String ip) {
        User user = user(userId);
        audit.record(userId, "ACCOUNT_DELETED", "users", userId, ip,
                Map.of("email", user.getEmail()));
        users.delete(user); // ON DELETE CASCADE propaga para os dados do titular
    }

    private User user(UUID userId) {
        return users.findById(userId)
                .filter(u -> u.getStatus() != br.com.investia.identity.domain.UserStatus.DELETED)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
    }

    private UserProfile profile(UUID userId) {
        return profiles.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Perfil não encontrado"));
    }
}
