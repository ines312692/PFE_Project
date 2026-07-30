package com.elyadata.feature.etl.application.controller;

import com.elyadata.feature.etl.application.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportService — pure format generation")
class ExportServiceTest {

    @Mock ExportStorageService storageService;

    private ExportService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ExportService(storageService, new ObjectMapper());
        Field f = ExportService.class.getDeclaredField("baseUrl");
        f.setAccessible(true);
        f.set(service, "https://api.example.com");
    }

    private static ExportRequest fileRequest(ExportFormat format) {
        return new ExportRequest(format, ExportDestination.FILE, null, null, null);
    }

    private static List<Map<String, Object>> records() {
        return List.of(
                Map.of("name", "Product A", "price", 9.99, "available", true),
                Map.of("name", "Product B", "price", 19.99, "available", false)
        );
    }



    @Nested
    @DisplayName("JSON format")
    class Json {

        @Test
        @DisplayName("exports valid JSON array")
        void jsonArrayExported() throws Exception {
            when(storageService.store(any(), any(), eq("application/json"))).thenReturn("exp-2");

            service.export(records(), fileRequest(ExportFormat.JSON));

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(storageService).store(captor.capture(), any(), any());
            Object parsed = new ObjectMapper().readValue(captor.getValue(), Object.class);
            assertThat(parsed).isInstanceOf(java.util.List.class);
            assertThat(((java.util.List<?>) parsed)).hasSize(2);
        }
    }


    @Nested
    @DisplayName("Markdown format")
    class Markdown {

        @Test
        @DisplayName("markdown has record count and frontmatter blocks")
        void markdownStructure() {
            when(storageService.store(any(), any(), eq("text/markdown; charset=utf-8")))
                    .thenReturn("exp-3");

            service.export(records(), fileRequest(ExportFormat.MARKDOWN));

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(storageService).store(captor.capture(), any(), any());
            String md = new String(captor.getValue());

            assertThat(md).contains("# Scrape Export");
            assertThat(md).contains("2 records");
            assertThat(md).contains("---");
        }

        @Test
        @DisplayName("empty records → minimal markdown")
        void emptyMarkdown() {
            when(storageService.store(any(), any(), any())).thenReturn("exp-empty");

            service.export(List.of(), fileRequest(ExportFormat.MARKDOWN));

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(storageService).store(captor.capture(), any(), any());
            String md = new String(captor.getValue());
            assertThat(md).contains("No records");
        }
    }

    @Nested
    @DisplayName("LLMS_TXT format")
    class LlmsTxt {

        @Test
        @DisplayName("llms.txt contains metadata section")
        void llmsTxtMetadata() {
            when(storageService.store(any(), any(), eq("text/plain; charset=utf-8")))
                    .thenReturn("exp-4");

            service.export(records(), fileRequest(ExportFormat.LLMS_TXT));

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(storageService).store(captor.capture(), any(), any());
            String txt = new String(captor.getValue());

            assertThat(txt).contains("## Metadata");
            assertThat(txt).contains("Total records: 2");
            assertThat(txt).contains("llmstxt.org");
        }

        @Test
        @DisplayName("llms.txt detects 'name' as title field for list items")
        void detectsTitleField() {
            when(storageService.store(any(), any(), any())).thenReturn("exp-5");

            service.export(records(), fileRequest(ExportFormat.LLMS_TXT));

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(storageService).store(captor.capture(), any(), any());
            String txt = new String(captor.getValue());

            assertThat(txt).contains("Product A");
            assertThat(txt).contains("Product B");
        }

        @Test
        @DisplayName("llms.txt uses URL as hyperlink when 'url' field present")
        void urlFieldUsedAsHyperlink() {
            when(storageService.store(any(), any(), any())).thenReturn("exp-6");

            List<Map<String, Object>> withUrl = List.of(
                    Map.of("title", "Item 1", "url", "https://shop.com/1", "description", "Nice item")
            );

            service.export(withUrl, fileRequest(ExportFormat.LLMS_TXT));

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(storageService).store(captor.capture(), any(), any());
            String txt = new String(captor.getValue());

            assertThat(txt).contains("[Item 1](https://shop.com/1)");
        }
    }

    // ── JSON-LD ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("JSON-LD format")
    class JsonLd {

        @Test
        @DisplayName("JSON-LD output has @context and DataFeed type")
        void jsonLdStructure() throws Exception {
            when(storageService.store(any(), any(), eq("application/ld+json"))).thenReturn("exp-7");

            service.export(records(), fileRequest(ExportFormat.JSON_LD));

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(storageService).store(captor.capture(), any(), any());
            @SuppressWarnings("unchecked")
            Map<String, Object> ld = new ObjectMapper().readValue(captor.getValue(), Map.class);

            assertThat(ld).containsKey("@context");
            assertThat(ld.get("@type")).isEqualTo("DataFeed");
            assertThat(ld.get("dataFeedElement")).isInstanceOf(java.util.List.class);
        }

        @Test
        @DisplayName("infers Product type when price field present")
        void infersProductType() throws Exception {
            when(storageService.store(any(), any(), any())).thenReturn("exp-8");

            service.export(records(), fileRequest(ExportFormat.JSON_LD));

            ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
            verify(storageService).store(captor.capture(), any(), any());
            @SuppressWarnings("unchecked")
            Map<String, Object> ld = new ObjectMapper().readValue(captor.getValue(), Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> elements = (List<Map<String, Object>>) ld.get("dataFeedElement");

            assertThat(elements.getFirst().get("@type")).isEqualTo("Product");
        }
    }

    // ── download URL ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("download URL")
    class DownloadUrl {

        @Test
        @DisplayName("response includes download URL with base URL and export ID")
        void downloadUrlFormatted() {
            when(storageService.store(any(), any(), any())).thenReturn("export-abc-123");

            ExportResponse response = service.export(records(), fileRequest(ExportFormat.JSON));

            assertThat(response.downloadUrl())
                    .startsWith("https://api.example.com/v1/exports/")
                    .contains("export-abc-123");
        }

        @Test
        @DisplayName("response durationMs is non-negative")
        void durationNonNegative() {
            when(storageService.store(any(), any(), any())).thenReturn("exp-x");

            ExportResponse response = service.export(records(), fileRequest(ExportFormat.CSV));

            assertThat(response.durationMs()).isGreaterThanOrEqualTo(0L);
        }
    }
}
