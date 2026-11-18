package br.com.investia.transactions.application;

import br.com.investia.transactions.domain.Category;
import br.com.investia.transactions.domain.CategoryKind;
import br.com.investia.transactions.infrastructure.jpa.CategoryJpaRepository;
import br.com.investia.identity.application.UserDataContributor;
import br.com.investia.shared.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** CRUD de categorias e subcategorias. */
@Service
@RequiredArgsConstructor
public class CategoryService implements UserDataContributor {

    private final CategoryJpaRepository categories;

    @Transactional(readOnly = true)
    public List<Category> list(UUID userId) {
        return categories.findAllByUserIdOrderByNameAsc(userId);
    }

    @Transactional
    public Category create(UUID userId, String name, CategoryKind kind, UUID parentId,
                           String color, String icon) {
        if (parentId != null) {
            Category parent = find(userId, parentId);
            if (parent.isSubcategory()) {
                throw new BusinessException("Subcategoria não pode ter subcategoria");
            }
        }
        categories.findByUserIdAndNameIgnoreCaseAndKind(userId, name.trim(), kind)
                .ifPresent(c -> {
                    throw new BusinessException("Já existe categoria com este nome");
                });
        return categories.save(Category.builder()
                .userId(userId)
                .name(name.trim())
                .kind(kind)
                .parentId(parentId)
                .color(color)
                .icon(icon)
                .build());
    }

    @Transactional
    public Category rename(UUID userId, UUID id, String name, String color, String icon) {
        Category category = find(userId, id);
        category.setName(name.trim());
        category.setColor(color);
        category.setIcon(icon);
        return categories.save(category);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        categories.delete(find(userId, id));
    }

    public Category find(UUID userId, UUID id) {
        Category category = categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("Categoria não encontrada"));
        if (!category.getUserId().equals(userId)) {
            throw ApiException.notFound("Categoria não encontrada");
        }
        return category;
    }

    @Override
    public String key() {
        return "categorias";
    }

    @Override
    public List<Map<String, Object>> collect(UUID userId) {
        return list(userId).stream()
                .map(c -> Map.<String, Object>of(
                        "nome", c.getName(), "tipo", c.getKind().name(),
                        "subcategoria", c.isSubcategory()))
                .toList();
    }
}
