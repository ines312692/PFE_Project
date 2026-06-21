package com.elyadata.common.utils;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class PageParam {

    @QueryParam("size")
    @Max(value = 200, message = "sizePage must be less than or equal to 200")
    @Min(value = 1,   message = "sizePage must be greater than 0")
    @Builder.Default
    int size = 20;

    @QueryParam("index")
    @Min(value = 0, message = "indexPage must be greater than or equal to 0")
    @Builder.Default
    int index = 0;

    public int getOffset() {
        return index * size;
    }

    public static PageParam defaultPage() {
        return PageParam.builder()
                .index(0)
                .size(20)
                .build();
    }

    public static PageParam ofSize(int size) {
        return PageParam.builder()
                .index(0)
                .size(size)
                .build();
    }

}