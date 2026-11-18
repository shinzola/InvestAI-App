package br.com.investia.transactions.api;

import br.com.investia.identity.infrastructure.security.SecurityUtils;
import br.com.investia.transactions.api.dto.TransactionDtos.CategoryRequest;
import br.com.investia.transactions.api.dto.TransactionDtos.CategoryResponse;
import br.com.investia.transactions.application.CategoryService;
import br.com.investia.transactions.domain.Category;
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

@Tag(name = "Categorias", description = "Categorias e subcategorias")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

    private final CategoryService service;

    @Operation(summary = "Lista categorias do usuário")
    @GetMapping
    public List<CategoryResponse> list() {
        return service.list(SecurityUtils.currentUserId()).stream()
                .map(CategoryController::toResponse)
                .toList();
    }

    @Operation(summary = "Cria categoria")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest req) {
        return toResponse(service.create(SecurityUtils.currentUserId(), req.name(),
                req.kind(), req.parentId(), req.color(), req.icon()));
    }

    @Operation(summary = "Renomeia categoria")
    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest req) {
        return toResponse(service.rename(SecurityUtils.currentUserId(), id,
                req.name(), req.color(), req.icon()));
    }

    @Operation(summary = "Remove categoria")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId().toString(), c.getName(), c.getKind().name(),
                c.getParentId() == null ? null : c.getParentId().toString(),
                c.getColor(), c.getIcon());
    }
}
