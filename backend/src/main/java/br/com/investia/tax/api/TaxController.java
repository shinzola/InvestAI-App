package br.com.investia.tax.api;

import br.com.investia.identity.infrastructure.security.SecurityUtils;
import br.com.investia.tax.api.dto.TaxDtos.*;
import br.com.investia.tax.application.TaxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Impostos", description = "Apuração de IR, come-cotas e relatório DARF")
@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TaxController {

    private final TaxService service;

    @Operation(summary = "Eventos tributários do mês")
    @GetMapping("/events")
    public List<TaxEventResponse> events(@RequestParam int year, @RequestParam int month) {
        return service.list(SecurityUtils.currentUserId(), year, month);
    }

    @Operation(summary = "Relatório DARF do mês (código, base e imposto)")
    @GetMapping("/darf")
    public DarfReport darf(@RequestParam int year, @RequestParam int month) {
        return service.darf(SecurityUtils.currentUserId(), year, month);
    }

    @Operation(summary = "Apura come-cotas do mês sobre rendimentos de FIIs")
    @PostMapping("/come-cotas/{year}/{month}")
    public ComeCotasResponse comeCotas(@PathVariable int year, @PathVariable int month) {
        return service.calculateComeCotas(SecurityUtils.currentUserId(), year, month);
    }
}
