package de.frank.invoice.worker.infrastructure.http;

import de.frank.invoice.worker.application.persistence.PageResult;

import java.util.List;
import java.util.function.Function;

record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort,
        String direction) {

    static <S, T> PageResponse<T> from(final PageResult<S> pageResult, final Function<S, T> mapper) {
        return new PageResponse<>(
                pageResult.items().stream().map(mapper).toList(),
                pageResult.page(),
                pageResult.size(),
                pageResult.totalElements(),
                pageResult.totalPages(),
                pageResult.sort(),
                pageResult.direction().name());
    }
}