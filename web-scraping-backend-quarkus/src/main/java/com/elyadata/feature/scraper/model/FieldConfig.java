package com.elyadata.feature.scraper.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FieldConfig {

    String name;
    String selector;

    @Builder.Default
    FieldType type = FieldType.TEXT;

    String attribute;

    @Builder.Default
    boolean required = true;
}