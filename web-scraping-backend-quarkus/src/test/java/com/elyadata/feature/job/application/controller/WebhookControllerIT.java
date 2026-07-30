package com.elyadata.feature.job.application.controller;

import com.elyadata.feature.job.domain.model.WebhookDeliveryStatus;
import com.elyadata.feature.job.domain.service.webhook.WebhookTriggerService;
import com.elyadata.feature.job.domain.service.webhook.WebhookTriggerService.Outcome;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebhookControllerIT {

    @InjectMock
    WebhookTriggerService triggerService;

    private static final String BASE_URL = "/v1/webhooks/jobs";
    private static final String TEST_TOKEN = "test-webhook-token-abc123";

    @BeforeEach
    void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    @Order(1)
    @DisplayName("POST /v1/webhooks/jobs/{token} - accepted webhook returns 202")
    void trigger_accepted_returns202() {
        when(triggerService.process(eq(TEST_TOKEN), any(), any(), any(), any(), any()))
                .thenReturn(Outcome.accepted());

        given()
                .header("X-Webhook-Timestamp", "2025-01-01T00:00:00Z")
                .header("X-Webhook-Signature", "sha256=abc123")
                .body("{\"event\": \"trigger\"}")
                .when()
                .post(BASE_URL + "/" + TEST_TOKEN)
                .then()
                .statusCode(202)
                .body("status", equalTo("ACCEPTED"))
                .body("message", equalTo("Job launch accepted"));

        verify(triggerService).process(eq(TEST_TOKEN), any(), any(), any(), any(), any());
    }

    @Test
    @Order(2)
    @DisplayName("POST /v1/webhooks/jobs/{token} - unknown token returns 404")
    void trigger_unknownToken_returns404() {
        when(triggerService.process(eq("unknown-token"), any(), any(), any(), any(), any()))
                .thenReturn(Outcome.unknownToken());

        given()
                .when()
                .post(BASE_URL + "/unknown-token")
                .then()
                .statusCode(404)
                .body("status", equalTo("REJECTED_UNKNOWN_TOKEN"));
    }

    @Test
    @Order(3)
    @DisplayName("POST /v1/webhooks/jobs/{token} - disabled webhook returns 403")
    void trigger_disabledWebhook_returns403() {
        when(triggerService.process(eq(TEST_TOKEN), any(), any(), any(), any(), any()))
                .thenReturn(Outcome.disabled());

        given()
                .when()
                .post(BASE_URL + "/" + TEST_TOKEN)
                .then()
                .statusCode(403)
                .body("status", equalTo("REJECTED_DISABLED"));
    }

    @Test
    @Order(4)
    @DisplayName("POST /v1/webhooks/jobs/{token} - bad signature returns 401")
    void trigger_badSignature_returns401() {
        when(triggerService.process(eq(TEST_TOKEN), any(), any(), any(), any(), any()))
                .thenReturn(Outcome.badSignature());

        given()
                .header("X-Webhook-Timestamp", "2025-01-01T00:00:00Z")
                .header("X-Webhook-Signature", "sha256=wrongsig")
                .when()
                .post(BASE_URL + "/" + TEST_TOKEN)
                .then()
                .statusCode(401)
                .body("status", equalTo("REJECTED_BAD_SIGNATURE"));
    }

    @Test
    @Order(5)
    @DisplayName("POST /v1/webhooks/jobs/{token} - stale timestamp returns 401")
    void trigger_staleTimestamp_returns401() {
        when(triggerService.process(eq(TEST_TOKEN), any(), any(), any(), any(), any()))
                .thenReturn(Outcome.staleTimestamp());

        given()
                .header("X-Webhook-Timestamp", "2020-01-01T00:00:00Z")
                .when()
                .post(BASE_URL + "/" + TEST_TOKEN)
                .then()
                .statusCode(401)
                .body("status", equalTo("REJECTED_STALE_TIMESTAMP"));
    }

    @Test
    @Order(6)
    @DisplayName("POST /v1/webhooks/jobs/{token} - internal error returns 500")
    void trigger_internalError_returns500() {
        when(triggerService.process(eq(TEST_TOKEN), any(), any(), any(), any(), any()))
                .thenReturn(Outcome.internal());

        given()
                .when()
                .post(BASE_URL + "/" + TEST_TOKEN)
                .then()
                .statusCode(500)
                .body("status", equalTo("FAILED_INTERNAL"));
    }

    @Test
    @Order(7)
    @DisplayName("POST /v1/webhooks/jobs/{token} - invalid job state returns 409")
    void trigger_invalidState_returns409() {
        when(triggerService.process(eq(TEST_TOKEN), any(), any(), any(), any(), any()))
                .thenReturn(Outcome.invalidState("Job is not in a launchable state"));

        given()
                .when()
                .post(BASE_URL + "/" + TEST_TOKEN)
                .then()
                .statusCode(409)
                .body("status", equalTo("REJECTED_INVALID_STATE"))
                .body("message", equalTo("Job is not in a launchable state"));
    }

    @Test
    @Order(8)
    @DisplayName("POST /v1/webhooks/jobs/{token} - X-Forwarded-For header is extracted as source IP")
    void trigger_xForwardedFor_extractsFirstIp() {
        when(triggerService.process(eq(TEST_TOKEN), any(), any(), any(), eq("10.0.0.1"), any()))
                .thenReturn(Outcome.accepted());

        given()
                .header("X-Forwarded-For", "10.0.0.1, 10.0.0.2, 10.0.0.3")
                .when()
                .post(BASE_URL + "/" + TEST_TOKEN)
                .then()
                .statusCode(202);

        verify(triggerService).process(eq(TEST_TOKEN), any(), any(), any(), eq("10.0.0.1"), any());
    }
}
