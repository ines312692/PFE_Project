package com.elyadata.feature.proxy.application.controller;

import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.common.pagination.Page;
import com.elyadata.common.utils.PageParam;
import com.elyadata.feature.auth.application.BffTokenService;
import com.elyadata.feature.proxy.application.dto.*;
import com.elyadata.feature.proxy.application.mapper.ProxyRestMapper;
import com.elyadata.feature.proxy.domain.model.*;
import com.elyadata.feature.proxy.domain.model.antibot.AntiBotProvider;
import com.elyadata.feature.proxy.domain.model.antibot.DetectionResult;
import com.elyadata.feature.proxy.domain.service.ProxyService;
import com.elyadata.feature.proxy.domain.service.ProxyService.PoolStats;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProxyControllerIT {

    @InjectMock
    ProxyService proxyService;
    @InjectMock
    ProxyRestMapper mapper;
    @InjectMock
    BffTokenService bffToken;

    private RequestSpecification authSpec;

    private static final UUID PROXY_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String BASE_URL = "/v1/proxies";

    private Proxy aProxy(ProxyStatus status) {
        return Proxy.builder()
                .id(PROXY_ID)
                .host("1.2.3.4")
                .port(8080)
                .protocol(ProxyProtocol.HTTP)
                .country("US")
                .anonymity(ProxyAnonymity.ANONYMOUS)
                .source(ProxySource.MANUAL)
                .status(status)
                .latencyMs(120)
                .successRate(0.95)
                .totalUses(10L)
                .successfulUses(9L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private ProxyResponse aProxyResponse(ProxyStatus status) {
        return new ProxyResponse(
                PROXY_ID, "1.2.3.4", 8080, ProxyProtocol.HTTP,
                "US", ProxyAnonymity.ANONYMOUS, ProxySource.MANUAL, status,
                120, 0.95, 10L, 9L,
                Instant.now(), Instant.now(), Instant.now(), Instant.now(),
                Map.of()
        );
    }

    @BeforeEach
    void setUp() {
        when(bffToken.resolveCurrentUserId(any())).thenReturn(USER_ID);
        when(bffToken.hasRole(anyString())).thenReturn(false);

        authSpec = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer test-token")
                .setContentType(ContentType.JSON)
                .build();

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }


    @Test
    @Order(1)
    @DisplayName("GET /v1/proxies - should return 200 with paged list")
    void list_returns200WithPagedProxies() {
        Proxy proxy = aProxy(ProxyStatus.ACTIVE);
        Page<Proxy> domainPage = new Page<>(List.of(proxy), 1L, 0, 20);
        ProxyResponse response = aProxyResponse(ProxyStatus.ACTIVE);

        when(proxyService.findAll(ArgumentMatchers.any(ProxySearchCriteria.class), ArgumentMatchers.any(PageParam.class)))
                .thenReturn(domainPage);
        when(proxyService.getAntiBotStatusesGrouped(anyList()))
                .thenReturn(Map.of(PROXY_ID, List.of()));
        when(mapper.toRest(anyList(), anyMap())).thenReturn(List.of(response));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(200)
                .body("data.content", hasSize(1))
                .body("data.content[0].id", equalTo(PROXY_ID.toString()))
                .body("data.content[0].host", equalTo("1.2.3.4"))
                .body("data.totalElements", equalTo(1));
    }

    @Test
    @Order(2)
    @DisplayName("GET /v1/proxies - should forward filters into ProxySearchCriteria")
    void list_withFilters_buildsCriteria() {
        Page<Proxy> emptyPage = new Page<>(List.of(), 0L, 0, 20);
        when(proxyService.findAll(ArgumentMatchers.any(ProxySearchCriteria.class), ArgumentMatchers.any(PageParam.class)))
                .thenReturn(emptyPage);
        when(proxyService.getAntiBotStatusesGrouped(anyList())).thenReturn(Map.of());
        when(mapper.toRest(anyList(), anyMap())).thenReturn(List.of());

        given()
                .spec(authSpec)
                .queryParam("status", "ACTIVE")
                .queryParam("protocol", "HTTP")
                .queryParam("search", "datacenter")
                .when()
                .get(BASE_URL)
                .then()
                .statusCode(200)
                .body("data.content", hasSize(0));

        verify(proxyService).findAll(ArgumentMatchers.any(ProxySearchCriteria.class), ArgumentMatchers.any(PageParam.class));
    }


    @Test
    @Order(3)
    @DisplayName("GET /v1/proxies/{id} - should return 200 with proxy details")
    void getById_existingId_returns200() {
        Proxy proxy = aProxy(ProxyStatus.ACTIVE);
        ProxyResponse response = aProxyResponse(ProxyStatus.ACTIVE);

        when(proxyService.findById(PROXY_ID)).thenReturn(proxy);
        when(proxyService.getAntiBotStatuses(PROXY_ID)).thenReturn(List.of());
        when(mapper.toRest(eq(proxy), anyList())).thenReturn(response);

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + PROXY_ID)
                .then()
                .statusCode(200)
                .body("data.id", equalTo(PROXY_ID.toString()))
                .body("data.status", equalTo("ACTIVE"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /v1/proxies/{id} - should return 404 when proxy not found")
    void getById_notFound_returns404() {
        when(proxyService.findById(PROXY_ID))
                .thenThrow(DomainException.notFound(
                        "Proxy not found: " + PROXY_ID, "/v1/proxies/" + PROXY_ID));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + PROXY_ID)
                .then()
                .statusCode(404);
    }

    // ------------------------------------------------------------ GET /stats

    @Test
    @Order(5)
    @DisplayName("GET /v1/proxies/stats - should return 200 with pool stats")
    void stats_returns200() {
        PoolStats poolStats = new PoolStats(5L, 2L, 1L, 3L, 4L,
                new EnumMap<>(AntiBotProvider.class));
        ProxyStatsResponse statsResponse =
                new ProxyStatsResponse(8L, 5L, 2L, 1L, 3L, 4L);

        when(proxyService.stats()).thenReturn(poolStats);
        when(mapper.toStats(poolStats)).thenReturn(statsResponse);

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/stats")
                .then()
                .statusCode(200)
                .body("data.active", equalTo(5))
                .body("data.blacklisted", equalTo(1));
    }


    @Test
    @Order(6)
    @DisplayName("POST /v1/proxies - should return 201 with created proxy")
    void create_validRequest_returns201() {
        String body = """
                {
                  "host": "10.0.0.1",
                  "port": 3128,
                  "protocol": "HTTP",
                  "country": "FR",
                  "anonymity": "ANONYMOUS"
                }
                """;

        Proxy created = aProxy(ProxyStatus.ACTIVE);
        ProxyResponse response = aProxyResponse(ProxyStatus.ACTIVE);

        when(proxyService.createManual(
                anyString(), anyInt(), ArgumentMatchers.any(ProxyProtocol.class),
                any(), any(), any(), ArgumentMatchers.any(ProxyAnonymity.class)))
                .thenReturn(created);
        when(mapper.toRest(created)).thenReturn(response);

        given()
                .spec(authSpec)
                .body(body)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(201)
                .body("data.id", equalTo(PROXY_ID.toString()));
    }

    @Test
    @Order(7)
    @DisplayName("POST /v1/proxies - should return 400 when host is blank")
    void create_blankHost_returns400() {
        String invalid = """
                {
                  "host": "",
                  "port": 3128,
                  "protocol": "HTTP"
                }
                """;

        given()
                .spec(authSpec)
                .body(invalid)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);

        verify(proxyService, never()).createManual(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @Order(8)
    @DisplayName("POST /v1/proxies - should return 400 when port is out of range")
    void create_portOutOfRange_returns400() {
        String invalid = """
                {
                  "host": "10.0.0.1",
                  "port": 70000,
                  "protocol": "HTTP"
                }
                """;

        given()
                .spec(authSpec)
                .body(invalid)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(400);

        verify(proxyService, never()).createManual(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @Order(9)
    @DisplayName("POST /v1/proxies - should return 409 when proxy already exists")
    void create_duplicate_returns409() {
        String body = """
                {
                  "host": "10.0.0.1",
                  "port": 3128,
                  "protocol": "HTTP"
                }
                """;

        when(proxyService.createManual(
                anyString(), anyInt(), ArgumentMatchers.any(ProxyProtocol.class),
                any(), any(), any(), any()))
                .thenThrow(DomainException.alreadyExists(
                        "Proxy already exists: 10.0.0.1:3128 (HTTP)"));

        given()
                .spec(authSpec)
                .body(body)
                .when()
                .post(BASE_URL)
                .then()
                .statusCode(409); // adjust to your DomainExceptionHandler mapping for alreadyExists
    }


    @Test
    @Order(10)
    @DisplayName("POST /v1/proxies/refresh - should return 200 with count added")
    void refresh_returns200WithAddedCount() {
        when(proxyService.refreshPool()).thenReturn(42);

        given()
                .spec(authSpec)
                .when()
                .post(BASE_URL + "/refresh")
                .then()
                .statusCode(200)
                .body("data.newProxies", equalTo(42));

        verify(proxyService).refreshPool();
    }

    // ----------------------------------------------- POST /{id}/blacklist etc.

    @Test
    @Order(11)
    @DisplayName("POST /v1/proxies/{id}/blacklist - should return 204")
    void blacklist_returns204() {
        doNothing().when(proxyService).blacklist(PROXY_ID);

        given()
                .spec(authSpec)
                .when()
                .post(BASE_URL + "/" + PROXY_ID + "/blacklist")
                .then()
                .statusCode(204);

        verify(proxyService).blacklist(PROXY_ID);
    }

    @Test
    @Order(12)
    @DisplayName("POST /v1/proxies/{id}/reactivate - should return 204")
    void reactivate_returns204() {
        doNothing().when(proxyService).reactivate(PROXY_ID);

        given()
                .spec(authSpec)
                .when()
                .post(BASE_URL + "/" + PROXY_ID + "/reactivate")
                .then()
                .statusCode(204);

        verify(proxyService).reactivate(PROXY_ID);
    }

    // ------------------------------------------------ POST /{id}/health-check

    @Test
    @Order(13)
    @DisplayName("POST /v1/proxies/{id}/health-check - should return 200 with result")
    void triggerHealthCheck_returns200() {
        ProxyHealthCheck result = mock(ProxyHealthCheck.class);
        when(proxyService.triggerHealthCheck(PROXY_ID)).thenReturn(result);

        given()
                .spec(authSpec)
                .when()
                .post(BASE_URL + "/" + PROXY_ID + "/health-check")
                .then()
                .statusCode(200);

        verify(proxyService).triggerHealthCheck(PROXY_ID);
    }

    @Test
    @Order(14)
    @DisplayName("POST /v1/proxies/{id}/antibot-check - no provider triggers full check")
    void antiBotCheck_noProvider_triggersFullCheck() {
        ProxyHealthCheck result = mock(ProxyHealthCheck.class);
        when(proxyService.triggerFullAntiBotCheck(PROXY_ID)).thenReturn(result);

        given()
                .spec(authSpec)
                .when()
                .post(BASE_URL + "/" + PROXY_ID + "/antibot-check")
                .then()
                .statusCode(200);

        verify(proxyService).triggerFullAntiBotCheck(PROXY_ID);
        verify(proxyService, never()).triggerSingleAntiBotCheck(any(), any());
    }

    @Test
    @Order(15)
    @DisplayName("POST /v1/proxies/{id}/antibot-check - with provider triggers single check")
    void antiBotCheck_withProvider_triggersSingleCheck() {
        DetectionResult result = mock(DetectionResult.class);
        when(proxyService.triggerSingleAntiBotCheck(eq(PROXY_ID), eq(AntiBotProvider.CLOUDFLARE)))
                .thenReturn(result);

        given()
                .spec(authSpec)
                .queryParam("provider", "CLOUDFLARE")
                .when()
                .post(BASE_URL + "/" + PROXY_ID + "/antibot-check")
                .then()
                .statusCode(200);

        verify(proxyService).triggerSingleAntiBotCheck(PROXY_ID, AntiBotProvider.CLOUDFLARE);
        verify(proxyService, never()).triggerFullAntiBotCheck(any());
    }


    @Test
    @Order(16)
    @DisplayName("GET /v1/proxies/{id}/antibot-statuses - should return 200")
    void getAntiBotStatuses_returns200() {
        when(proxyService.getAntiBotStatuses(PROXY_ID)).thenReturn(List.of());

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/" + PROXY_ID + "/antibot-statuses")
                .then()
                .statusCode(200)
                .body("data", hasSize(0));
    }

    // ------------------------------------------------------------ DELETE /{id}

    @Test
    @Order(17)
    @DisplayName("DELETE /v1/proxies/{id} - should return 204 on success")
    void delete_existingId_returns204() {
        doNothing().when(proxyService).delete(PROXY_ID);

        given()
                .spec(authSpec)
                .when()
                .delete(BASE_URL + "/" + PROXY_ID)
                .then()
                .statusCode(204);

        verify(proxyService).delete(PROXY_ID);
    }

    @Test
    @Order(18)
    @DisplayName("DELETE /v1/proxies/{id} - should return 404 when proxy not found")
    void delete_notFound_returns404() {
        doThrow(DomainException.notFound("Proxy not found: " + PROXY_ID))
                .when(proxyService).delete(PROXY_ID);

        given()
                .spec(authSpec)
                .when()
                .delete(BASE_URL + "/" + PROXY_ID)
                .then()
                .statusCode(404);
    }

    // ----------------------------------------------- GET /antibot-providers

    @Test
    @Order(19)
    @DisplayName("GET /v1/proxies/antibot-providers - should return 200 with provider list")
    void antiBotProviders_returns200() {
        when(proxyService.registeredAntiBotProviders())
                .thenReturn(List.of(AntiBotProvider.CLOUDFLARE));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/antibot-providers")
                .then()
                .statusCode(200)
                .body("data", hasSize(1))
                .body("data[0]", equalTo("CLOUDFLARE"));
    }


    @Test
    @Order(20)
    @DisplayName("GET /v1/proxies/dashboard - should return 200 with dashboard")
    void dashboard_returns200() {
        ProxyDashboard domain = mock(ProxyDashboard.class);
        ProxyDashboardResponse response = new ProxyDashboardResponse(
                10L, 6L, 3L, 1L, 150.0, 0.9, 4L,
                Map.of(), Map.of(), Map.of(), Map.of(),
                new ProxyDashboardResponse.LatencyBucketsDto(2L, 3L, 1L, 4L),
                Map.of(), List.of(), Instant.now());

        when(proxyService.dashboard()).thenReturn(domain);
        when(mapper.toDashboard(domain)).thenReturn(response);

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/dashboard")
                .then()
                .statusCode(200)
                .body("data.total", equalTo(10))
                .body("data.active", equalTo(6));
    }


    @Test
    @Order(21)
    @DisplayName("GET /v1/proxies/countries - should return 200 with country stats")
    void availableCountries_returns200() {
        ProxyCountryStat stat = mock(ProxyCountryStat.class);
        when(stat.code()).thenReturn("US");
        when(stat.count()).thenReturn(7L);
        when(proxyService.availableCountries()).thenReturn(List.of(stat));

        given()
                .spec(authSpec)
                .when()
                .get(BASE_URL + "/countries")
                .then()
                .statusCode(200)
                .body("data", hasSize(1))
                .body("data[0].code", equalTo("US"))
                .body("data[0].count", equalTo(7));
    }
}