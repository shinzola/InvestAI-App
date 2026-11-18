package br.com.investia.transactions.application;

import br.com.investia.identity.application.events.UserRegisteredEvent;
import br.com.investia.transactions.domain.CategoryKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cria categorias padrão (sem dados fictícios: são categorias de produto
 * recém-criadas para o usuário, comportamento esperado do onboarding).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultCategorySeeder {

    private final CategoryService categoryService;

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        UUID userId = event.userId();
        try {
            UUID income = categoryService.create(userId, "Salário", CategoryKind.INCOME, null, "#34D399", "wallet").getId();
            UUID invest = categoryService.create(userId, "Investimentos", CategoryKind.INCOME, null, "#22D3EE", "trending-up").getId();
            categoryService.create(userId, "Dividendos", CategoryKind.INCOME, invest, "#22D3EE", "coins");

            UUID housing = categoryService.create(userId, "Moradia", CategoryKind.EXPENSE, null, "#8B5CF6", "home").getId();
            categoryService.create(userId, "Aluguel", CategoryKind.EXPENSE, housing, "#8B5CF6", "home");
            UUID food = categoryService.create(userId, "Alimentação", CategoryKind.EXPENSE, null, "#A78BFA", "utensils").getId();
            categoryService.create(userId, "Mercado", CategoryKind.EXPENSE, food, "#A78BFA", "shopping-cart");
            categoryService.create(userId, "Delivery", CategoryKind.EXPENSE, food, "#FBBF24", "pizza");
            UUID transport = categoryService.create(userId, "Transporte", CategoryKind.EXPENSE, null, "#FBBF24", "car").getId();
            UUID health = categoryService.create(userId, "Saúde", CategoryKind.EXPENSE, null, "#34D399", "heart").getId();
            UUID education = categoryService.create(userId, "Educação", CategoryKind.EXPENSE, null, "#22D3EE", "book").getId();
            UUID leisure = categoryService.create(userId, "Lazer", CategoryKind.EXPENSE, null, "#F87171", "gamepad").getId();
            categoryService.create(userId, "Assinaturas", CategoryKind.EXPENSE, leisure, "#F87171", "tv");
            categoryService.create(userId, "Compras", CategoryKind.EXPENSE, null, "#A79FC4", "shopping-bag");
            categoryService.create(userId, "Outros", CategoryKind.EXPENSE, null, "#A79FC4", "ellipsis");
        } catch (Exception e) {
            log.error("Falha ao semear categorias padrão do usuário {}: {}", userId, e.getMessage());
        }
    }
}
