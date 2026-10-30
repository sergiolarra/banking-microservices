package com.bank.clients.infrastructure.rest.dto;

import com.bank.clients.domain.pagination.PagedResult;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(PagedResult<T> result) {
        return new PageResponse<>(result.items(), result.page(), result.size(), result.totalElements(),
                result.totalPages());
    }
}
