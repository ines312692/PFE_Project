package com.elyadata.feature.schema.domain.service;

import com.elyadata.feature.notification.domain.model.NotificationType;
import com.elyadata.feature.notification.domain.service.NotificationService;
import com.elyadata.feature.schema.domain.model.ExtractionSchema;
import com.elyadata.feature.schema.domain.model.SchemaField;
import com.elyadata.feature.schema.domain.model.SchemaFieldType;
import com.elyadata.feature.schema.domain.service.SelectorHealer.HealResult;
import com.elyadata.feature.schema.domain.service.port.ExtractionSchemaPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectorHealerTest {

    @Mock
    ExtractionSchemaPort schemaPort;
    @Mock
    NotificationService notificationService;
    @Mock
    HtmlSampler htmlSampler;

    @InjectMocks
    SelectorHealer healer;

    private final UUID jobId = UUID.randomUUID();
    private final UUID createdBy = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        healer.groqApiKey = "";
    }

    @Test
    void heal_skipsWhenApiKeyMissing() {
        HealResult result = healer.heal(jobId, createdBy, "<html><body><p>x</p></body></html>");

        assertThat(result.repaired()).isFalse();
        assertThat(result.reason()).contains("not configured");
        verify(schemaPort, never()).findByJobId(any());
    }

    @Test
    void heal_skipsWhenHtmlBlank() {
        healer.groqApiKey = "fake-key";

        HealResult result = healer.heal(jobId, createdBy, "   ");

        assertThat(result.repaired()).isFalse();
        assertThat(result.reason()).contains("Empty HTML");
    }

    @Test
    void heal_skipsWhenHtmlNull() {
        healer.groqApiKey = "fake-key";

        HealResult result = healer.heal(jobId, createdBy, null);

        assertThat(result.repaired()).isFalse();
        assertThat(result.reason()).contains("Empty HTML");
    }

    @Test
    void heal_skipsWhenSchemaAbsent() {
        healer.groqApiKey = "fake-key";
        when(schemaPort.findByJobId(jobId)).thenReturn(Optional.empty());

        HealResult result = healer.heal(jobId, createdBy, "<html><body><p>x</p></body></html>");

        assertThat(result.repaired()).isFalse();
        assertThat(result.reason()).contains("No CSS schema");
    }

    @Test
    void heal_skipsWhenSchemaHasNoFields() {
        healer.groqApiKey = "fake-key";
        ExtractionSchema schema = ExtractionSchema.builder()
                .id(UUID.randomUUID()).jobId(jobId).fields(List.of()).build();
        when(schemaPort.findByJobId(jobId)).thenReturn(Optional.of(schema));

        HealResult result = healer.heal(jobId, createdBy, "<html><body><p>x</p></body></html>");

        assertThat(result.repaired()).isFalse();
        assertThat(result.reason()).contains("No CSS schema");
    }

    @Test
    void heal_skipsWhenAllSelectorsStillMatch() {
        healer.groqApiKey = "fake-key";
        // selector ".price" matches the html -> field not broken -> skip
        SchemaField field = SchemaField.builder()
                .name("price").selector(".price").type(SchemaFieldType.TEXT).build();
        ExtractionSchema schema = ExtractionSchema.builder()
                .id(UUID.randomUUID()).jobId(jobId).fields(List.of(field)).build();
        when(schemaPort.findByJobId(jobId)).thenReturn(Optional.of(schema));

        String html = "<html><body><span class=\"price\">10 €</span></body></html>";

        HealResult result = healer.heal(jobId, createdBy, html);

        assertThat(result.repaired()).isFalse();
        assertThat(result.reason()).contains("still match");
        // sampler/notification never reached
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }


    @Test
    void healResult_successCarriesFields() {
        HealResult r = HealResult.success(java.util.Map.of("price", ".new-price"));

        assertThat(r.repaired()).isTrue();
        assertThat(r.repairedFields()).containsEntry("price", ".new-price");
        assertThat(r.reason()).isNull();
    }

    @Test
    void healResult_skippedAndFailedAreNotRepaired() {
        assertThat(HealResult.skipped("nope").repaired()).isFalse();
        assertThat(HealResult.failed("boom").repaired()).isFalse();
        assertThat(HealResult.skipped("nope").repairedFields()).isEmpty();
        assertThat(HealResult.failed("boom").reason()).isEqualTo("boom");
    }


    @Test
    void heal_notifiesFailureWhenBrokenButLlmReturnsNothing() {
        healer.groqApiKey = "fake-key";
        // broken selector (matches 0 elements)
        SchemaField field = SchemaField.builder()
                .name("title").selector(".missing-xyz").type(SchemaFieldType.TEXT).build();
        ExtractionSchema schema = ExtractionSchema.builder()
                .id(UUID.randomUUID()).jobId(jobId).fields(List.of(field)).build();
        when(schemaPort.findByJobId(jobId)).thenReturn(Optional.of(schema));
        // sampler returns an empty-ish sample; askLlm will fail/throw on the fake key
        lenient().when(htmlSampler.sample(any()))
                .thenReturn(new HtmlSampler.Sample("<html></html>", List.of()));

        String html = "<html><body><h2 class=\"real-title\">Hi</h2></body></html>";

        HealResult result = healer.heal(jobId, createdBy, html);

        // LLM call fails with the fake key -> no candidates -> failed + failure notification
        assertThat(result.repaired()).isFalse();
        assertThat(result.reason()).contains("No validated candidates");

        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        verify(notificationService).notify(
                any(UUID.class), any(UUID.class), typeCaptor.capture(), any(), any());
        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.JOB_FAILED);
    }

    @Test
    void heal_doesNotNotifyWhenCreatedByIsNull() {
        healer.groqApiKey = "fake-key";
        SchemaField field = SchemaField.builder()
                .name("title").selector(".missing-xyz").type(SchemaFieldType.TEXT).build();
        ExtractionSchema schema = ExtractionSchema.builder()
                .id(UUID.randomUUID()).jobId(jobId).fields(List.of(field)).build();
        when(schemaPort.findByJobId(jobId)).thenReturn(Optional.of(schema));
        lenient().when(htmlSampler.sample(any()))
                .thenReturn(new HtmlSampler.Sample("<html></html>", List.of()));

        HealResult result = healer.heal(jobId, null,
                "<html><body><h2 class=\"real-title\">Hi</h2></body></html>");

        assertThat(result.repaired()).isFalse();
        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }
}
