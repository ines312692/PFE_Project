package com.elyadata.feature.visualselector.application.dto;

import jakarta.validation.constraints.NotBlank;


public record HardenSelectorRequest(
        @NotBlank(message = "html is required")
        String html,

        @NotBlank(message = "candidateSelector is required")
        String candidateSelector,

        String url,

        boolean expectList
) {
}