package com.elyadata.feature.template.domain.service;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.feature.template.domain.model.Template;
import com.elyadata.feature.template.domain.model.TemplateCategory;
import com.elyadata.feature.template.domain.service.port.TemplatePort;
import com.elyadata.feature.job.domain.model.JobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock TemplatePort templatePort;
    @InjectMocks TemplateServiceImpl service;

    private static final UUID ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private Template aTemplate() {
        return Template.builder()
                .id(ID).name("E-Commerce Template")
                .description("For product pages")
                .category(TemplateCategory.E_COMMERCE)
                .jobType(JobType.SCRAPER)
                .tags(List.of("ecommerce")).popularity(50)
                .config(Map.of()).createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getAll() returns all templates from port")
    void getAll_returnsList() {
        Template template = aTemplate();
        when(templatePort.findAll()).thenReturn(List.of(template));

        List<Template> result = service.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("E-Commerce Template");
        verify(templatePort).findAll();
    }

    @Test
    @DisplayName("getAll() returns empty list when no templates exist")
    void getAll_empty_returnsEmptyList() {
        when(templatePort.findAll()).thenReturn(List.of());

        List<Template> result = service.getAll();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getById() returns template when found")
    void getById_found_returnsTemplate() {
        Template template = aTemplate();
        when(templatePort.findById(ID)).thenReturn(Optional.of(template));

        Template result = service.getById(ID);

        assertThat(result.id()).isEqualTo(ID);
        assertThat(result.name()).isEqualTo("E-Commerce Template");
    }

    @Test
    @DisplayName("getById() throws notFound when template does not exist")
    void getById_notFound_throwsException() {
        when(templatePort.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(ID))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Template not found");
    }
}
