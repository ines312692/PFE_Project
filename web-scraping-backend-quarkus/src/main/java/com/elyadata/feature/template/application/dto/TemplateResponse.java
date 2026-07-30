package com.elyadata.feature.template.application.dto;

import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponse {
    private UUID id;
    private String name;
    private String description;
    private String category;
    private String jobType;
    private List<String> tags;
    private int popularity;
    private Map<String, Object> config;
}