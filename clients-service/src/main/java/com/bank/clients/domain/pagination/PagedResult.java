package com.bank.clients.domain.pagination;

import java.util.List;
import java.util.function.Function;

/** One page of a listing, independent of any persistence framework. */
public record PagedResult<T>(List<T> items, int page, int size, long totalElements) {

    public PagedResult {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return (int) Math.ceil((double) totalElements / size);
    }

    public <R> PagedResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PagedResult<>(items.stream().<R>map(mapper).toList(), page, size, totalElements);
    }
}
