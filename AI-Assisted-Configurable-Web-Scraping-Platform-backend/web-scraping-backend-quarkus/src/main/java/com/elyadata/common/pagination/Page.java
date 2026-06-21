package com.elyadata.common.pagination;

import java.util.List;


public record Page<T>(
        List<T> content,
        long totalElements,
        int page,
        int size
) {
    public boolean isEmpty() {
        return content.isEmpty();
    }

    public static <T> Page<T> empty(int page, int size) {
        return new Page<>(List.of(), 0L, page, size);
    }

    public <R> Page<R> map(java.util.function.Function<T, R> mapper) {
        return new Page<>(content.stream().map(mapper).toList(), totalElements, page, size);
    }
}