package br.com.investia.accounts.api;

import br.com.investia.accounts.api.dto.AccountDtos.*;
import br.com.investia.accounts.application.AccountService;
import br.com.investia.identity.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Contas", description = "Contas bancárias, cartões e carteiras")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService service;

    @Operation(summary = "Lista contas com saldo consolidado")
    @GetMapping
    public List<AccountResponse> list() {
        return service.list(SecurityUtils.currentUserId());
    }

    @Operation(summary = "Detalhe de conta")
    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return service.get(SecurityUtils.currentUserId(), id);
    }

    @Operation(summary = "Cria conta")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody AccountRequest req) {
        return service.create(SecurityUtils.currentUserId(), req);
    }

    @Operation(summary = "Atualiza conta")
    @PutMapping("/{id}")
    public AccountResponse update(@PathVariable UUID id, @Valid @RequestBody AccountRequest req) {
        return service.update(SecurityUtils.currentUserId(), id, req);
    }

    @Operation(summary = "Remove conta")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Configura cartão de crédito da conta")
    @PostMapping("/credit-card")
    public CreditCardResponse configureCard(@Valid @RequestBody CreditCardRequest req) {
        return service.configureCreditCard(SecurityUtils.currentUserId(), req);
    }

    @Operation(summary = "Lista cartões de crédito")
    @GetMapping("/credit-card")
    public List<CreditCardResponse> listCards() {
        return service.listCreditCards(SecurityUtils.currentUserId());
    }
}
