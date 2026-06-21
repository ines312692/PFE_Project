package com.elyadata.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.ws.rs.core.Response;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        T data,
        String message,
        int statusCode,
        OffsetDateTime timestamp,
        PageMeta pagination
) {
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .data(data)
                .statusCode(Response.Status.OK.getStatusCode())
                .timestamp(OffsetDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .data(data)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .timestamp(OffsetDateTime.now())
                .build();
    }

    public static ApiResponse<Void> error(String message) {
        return ApiResponse.<Void>builder()
                .message(message)
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .timestamp(OffsetDateTime.now())
                .build();
    }

    public static <T> ApiResponse<List<T>> paginated(List<T> data,
                                                     long totalElements,
                                                     int page,
                                                     int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        return ApiResponse.<List<T>>builder()
                .data(data)
                .statusCode(Response.Status.OK.getStatusCode())
                .timestamp(OffsetDateTime.now())
                .pagination(PageMeta.builder()
                        .totalElements(totalElements)
                        .totalPages(totalPages)
                        .page(page)
                        .size(size)
                        .hasNext(page < totalPages - 1)
                        .hasPrevious(page > 0)
                        .build())
                .build();
    }
}