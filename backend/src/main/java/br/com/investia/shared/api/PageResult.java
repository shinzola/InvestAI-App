package br.com.investia.shared.api;

import java.util.List;

/** Página paginada padrão (page começa em 0). */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
