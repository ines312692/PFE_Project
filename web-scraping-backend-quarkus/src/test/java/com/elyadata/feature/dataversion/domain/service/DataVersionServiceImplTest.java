package com.elyadata.feature.dataversion.domain.service;

import com.elyadata.feature.dataversion.domain.model.DataVersion;
import com.elyadata.feature.dataversion.domain.service.port.DataVersionPort;
import com.elyadata.feature.scraperesult.domain.model.ScrapeResult;
import com.elyadata.feature.scraperesult.domain.service.port.ScrapeResultPort;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataVersionServiceImpl")
class DataVersionServiceImplTest {

    @Mock DataVersionPort dataVersionPort;
    @Mock ScrapeResultPort scrapeResultPort;

    private DataVersionServiceImpl service;

    private static final UUID SCRAPE_ID = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID VERSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DataVersionServiceImpl(dataVersionPort, scrapeResultPort);
    }

    private ScrapeResult scrapeResult() {
        return ScrapeResult.builder()
                .id(SCRAPE_ID)
                .jobId(UUID.randomUUID())
                .status("COMPLETED")
                .records(List.of(Map.of("key", "value")))
                .scrapedAt(Instant.now())
                .build();
    }

    private DataVersion version(int number) {
        return DataVersion.builder()
                .id(VERSION_ID)
                .scrapeResultId(SCRAPE_ID)
                .versionNumber(number)
                .records(List.of(Map.of("k", "v")))
                .appliedSteps(List.of())
                .recordCount(1)
                .label("v" + number)
                .createdAt(Instant.now())
                .build();
    }


    @Nested
    @DisplayName("createVersion()")
    class CreateVersion {

        @Test
        @DisplayName("first version gets versionNumber=1 and no parentVersionId")
        void firstVersion_number1_noParent() {
            when(scrapeResultPort.findById(SCRAPE_ID)).thenReturn(Optional.of(scrapeResult()));
            when(dataVersionPort.findLatest(SCRAPE_ID)).thenReturn(Optional.empty());
            ArgumentCaptor<DataVersion> captor = ArgumentCaptor.forClass(DataVersion.class);
            when(dataVersionPort.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            var cmd = new DataVersionService.NewVersion(
                    SCRAPE_ID, List.of(Map.of("a", 1)), List.of(), "First run", USER_ID);
            service.createVersion(cmd);

            DataVersion saved = captor.getValue();
            assertThat(saved.versionNumber()).isEqualTo(1);
            assertThat(saved.parentVersionId()).isNull();
        }

        @Test
        @DisplayName("second version gets versionNumber=2 and parentVersionId from latest")
        void secondVersion_number2_parentSet() {
            DataVersion latest = version(1);
            when(scrapeResultPort.findById(SCRAPE_ID)).thenReturn(Optional.of(scrapeResult()));
            when(dataVersionPort.findLatest(SCRAPE_ID)).thenReturn(Optional.of(latest));
            ArgumentCaptor<DataVersion> captor = ArgumentCaptor.forClass(DataVersion.class);
            when(dataVersionPort.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            var cmd = new DataVersionService.NewVersion(
                    SCRAPE_ID, List.of(), List.of(), null, USER_ID);
            service.createVersion(cmd);

            DataVersion saved = captor.getValue();
            assertThat(saved.versionNumber()).isEqualTo(2);
            assertThat(saved.parentVersionId()).isEqualTo(VERSION_ID);
        }

        @Test
        @DisplayName("null records and steps default to empty lists")
        void nullRecordsAndSteps_defaultToEmpty() {
            when(scrapeResultPort.findById(SCRAPE_ID)).thenReturn(Optional.of(scrapeResult()));
            when(dataVersionPort.findLatest(SCRAPE_ID)).thenReturn(Optional.empty());
            ArgumentCaptor<DataVersion> captor = ArgumentCaptor.forClass(DataVersion.class);
            when(dataVersionPort.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            var cmd = new DataVersionService.NewVersion(
                    SCRAPE_ID, null, null, "label", USER_ID);
            service.createVersion(cmd);

            assertThat(captor.getValue().records()).isEmpty();
            assertThat(captor.getValue().appliedSteps()).isEmpty();
        }

        @Test
        @DisplayName("null label generates default label with step count")
        void nullLabel_generatesDefault() {
            when(scrapeResultPort.findById(SCRAPE_ID)).thenReturn(Optional.of(scrapeResult()));
            when(dataVersionPort.findLatest(SCRAPE_ID)).thenReturn(Optional.empty());
            ArgumentCaptor<DataVersion> captor = ArgumentCaptor.forClass(DataVersion.class);
            when(dataVersionPort.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            var steps = List.of(Map.<String, Object>of("type", "TRIM"), Map.<String, Object>of("type", "CAPITALIZE"));
            var cmd = new DataVersionService.NewVersion(
                    SCRAPE_ID, List.of(), steps, null, USER_ID);
            service.createVersion(cmd);

            String label = captor.getValue().label();
            assertThat(label).contains("2").contains("step");
        }

        @Test
        @DisplayName("throws NotFoundException when scrapeResultId not found")
        void scrapeResultNotFound_throws() {
            when(scrapeResultPort.findById(any())).thenReturn(Optional.empty());

            var cmd = new DataVersionService.NewVersion(
                    SCRAPE_ID, List.of(), List.of(), null, USER_ID);
            assertThatThrownBy(() -> service.createVersion(cmd))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("recordCount matches records list size")
        void recordCount_matchesListSize() {
            var records = List.of(Map.<String, Object>of("a", 1), Map.<String, Object>of("b", 2));
            when(scrapeResultPort.findById(SCRAPE_ID)).thenReturn(Optional.of(scrapeResult()));
            when(dataVersionPort.findLatest(SCRAPE_ID)).thenReturn(Optional.empty());
            ArgumentCaptor<DataVersion> captor = ArgumentCaptor.forClass(DataVersion.class);
            when(dataVersionPort.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            var cmd = new DataVersionService.NewVersion(SCRAPE_ID, records, List.of(), "v1", USER_ID);
            service.createVersion(cmd);

            assertThat(captor.getValue().recordCount()).isEqualTo(2);
        }
    }


    @Nested
    @DisplayName("listVersions()")
    class ListVersions {

        @Test
        @DisplayName("includes raw version (v0) at the start of the list")
        void includesRawVersion() {
            when(scrapeResultPort.findById(SCRAPE_ID)).thenReturn(Optional.of(scrapeResult()));
            when(dataVersionPort.findByScrapeResultId(SCRAPE_ID)).thenReturn(List.of(version(1)));

            List<DataVersion> versions = service.listVersions(SCRAPE_ID);

            assertThat(versions).hasSize(2);
            assertThat(versions.getFirst().versionNumber()).isEqualTo(DataVersion.RAW_VERSION);
            assertThat(versions.get(1).versionNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("raw version has label 'Raw scrape result'")
        void rawVersion_hasDefaultLabel() {
            when(scrapeResultPort.findById(SCRAPE_ID)).thenReturn(Optional.of(scrapeResult()));
            when(dataVersionPort.findByScrapeResultId(SCRAPE_ID)).thenReturn(List.of());

            List<DataVersion> versions = service.listVersions(SCRAPE_ID);

            assertThat(versions.getFirst().label()).isEqualTo("Raw scrape result");
        }

        @Test
        @DisplayName("throws NotFoundException when scrapeResultId not found")
        void scrapeResultNotFound_throws() {
            when(scrapeResultPort.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listVersions(SCRAPE_ID))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getVersion()")
    class GetVersion {

        @Test
        @DisplayName("versionNumber=0 returns raw scrape result")
        void version0_returnsRaw() {
            when(scrapeResultPort.findById(SCRAPE_ID)).thenReturn(Optional.of(scrapeResult()));

            DataVersion v = service.getVersion(SCRAPE_ID, DataVersion.RAW_VERSION);

            assertThat(v.versionNumber()).isEqualTo(DataVersion.RAW_VERSION);
            assertThat(v.isRaw()).isTrue();
        }

        @Test
        @DisplayName("versionNumber>0 delegates to port")
        void versionN_delegatesToPort() {
            DataVersion v1 = version(1);
            when(dataVersionPort.findByScrapeResultIdAndVersionNumber(SCRAPE_ID, 1))
                    .thenReturn(Optional.of(v1));

            assertThat(service.getVersion(SCRAPE_ID, 1)).isEqualTo(v1);
        }

        @Test
        @DisplayName("throws NotFoundException when version not found")
        void versionNotFound_throws() {
            when(dataVersionPort.findByScrapeResultIdAndVersionNumber(any(), anyInt()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getVersion(SCRAPE_ID, 99))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("rollback()")
    class Rollback {

        @Test
        @DisplayName("creates a new version with the target version's records")
        void createsNewVersionWithTargetRecords() {
            DataVersion target = version(1);
            when(dataVersionPort.findByScrapeResultIdAndVersionNumber(SCRAPE_ID, 1))
                    .thenReturn(Optional.of(target));
            when(scrapeResultPort.findById(SCRAPE_ID)).thenReturn(Optional.of(scrapeResult()));
            when(dataVersionPort.findLatest(SCRAPE_ID)).thenReturn(Optional.of(version(2)));
            ArgumentCaptor<DataVersion> captor = ArgumentCaptor.forClass(DataVersion.class);
            when(dataVersionPort.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

            service.rollback(SCRAPE_ID, 1, USER_ID);

            DataVersion saved = captor.getValue();
            assertThat(saved.label()).contains("Rollback to v1");
            assertThat(saved.records()).isEqualTo(target.records());
        }
    }
}
