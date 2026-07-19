package de.frank.invoice.worker.application.persistence;

import java.util.List;
import java.util.Objects;

/**
 * Repository page result without HTTP dependencies.
 *
 * @param items page items
 * @param page zero-based page index
 * @param size requested page size
 * @param totalElements total matching rows
 * @param sort sort field
 * @param direction sort direction
 * @param <T> item type
 */
public record PageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        String sort,
        SortDirection direction) {

    /**
     * Creates an immutable page result.
     */
    public PageResult {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        Objects.requireNonNull(sort, "sort must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must not be negative");
        }
    }

    /**
     * Returns total page count.
     *
     * @return total pages
     */
    public int totalPages() {
        return (int) Math.ceil(totalElements / (double) size);
    }
}