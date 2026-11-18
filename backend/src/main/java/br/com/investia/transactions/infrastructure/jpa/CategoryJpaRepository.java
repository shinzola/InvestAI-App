package br.com.investia.transactions.infrastructure.jpa;

import br.com.investia.transactions.domain.Category;
import br.com.investia.transactions.domain.CategoryKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryJpaRepository extends JpaRepository<Category, UUID> {

    List<Category> findAllByUserIdOrderByNameAsc(UUID userId);

    List<Category> findAllByUserIdAndKind(UUID userId, CategoryKind kind);

    List<Category> findAllByUserIdAndParentId(UUID userId, UUID parentId);

    Optional<Category> findByUserIdAndNameIgnoreCaseAndKind(UUID userId, String name, CategoryKind kind);
}
