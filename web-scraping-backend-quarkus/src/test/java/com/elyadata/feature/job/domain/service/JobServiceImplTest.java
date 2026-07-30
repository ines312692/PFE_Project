package com.elyadata.feature.job.domain.service;

import com.elyadata.common.config.SessionCrypto;
import com.elyadata.common.exceptions.domain.DomainException;
import com.elyadata.common.pagination.Page;
import com.elyadata.feature.job.domain.model.ExtractionMode;
import com.elyadata.feature.job.domain.model.Job;
import com.elyadata.feature.job.domain.model.JobPriority;
import com.elyadata.feature.job.domain.model.JobSearchCriteria;
import com.elyadata.feature.job.domain.model.JobStats;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.model.JobTriggerType;
import com.elyadata.feature.job.domain.model.JobType;
import com.elyadata.feature.job.domain.model.WebhookCredentials;
import com.elyadata.feature.job.domain.service.port.JobPort;
import com.elyadata.feature.job.domain.service.webhook.WebhookCredentialGenerator;
import com.elyadata.feature.proxy.domain.model.ProxyConfig;
import com.elyadata.feature.proxy.domain.service.port.ProxyConfigPort;
import com.elyadata.feature.schema.domain.model.ExtractionSchema;
import com.elyadata.feature.schema.domain.service.port.ExtractionSchemaPort;
import com.elyadata.feature.scraper.model.InternalScrapeRequest;
import com.elyadata.feature.scraper.pipeline.ScrapeEngine;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceImplTest {

    @Mock
    JobPort jobPort;
    @Mock
    JobScheduler jobScheduler;
    @Mock
    ExtractionSchemaPort schemaPort;
    @Mock
    ProxyConfigPort proxyConfigPort;
    @Mock
    ScrapeEngine scrapeEngine;
    @Mock
    ManagedExecutor managedExecutor;
    @Mock
    WebhookCredentialGenerator webhookGenerator;
    @Mock
    SessionCrypto sessionCrypto;

    @InjectMocks
    JobServiceImpl service;

    private UUID jobId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }


    private Job.JobBuilder baseScraperJob() {
        return Job.builder()
                .id(jobId)
                .name("test-job")
                .targetUrl("https://example.com")
                .jobType(JobType.SCRAPER)
                .status(JobStatus.CREATED)
                .priority(JobPriority.NORMAL)
                .triggerType(JobTriggerType.MANUAL)
                .extractionMode(ExtractionMode.CSS_SELECTOR)
                .createdBy(userId);
    }

    private void stubSaveAndUpdateEcho() {
        lenient().when(jobPort.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(jobPort.update(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubProxyAndSchemaPassthrough() {
        lenient().when(proxyConfigPort.saveOrUpdate(nullable(UUID.class), any(ProxyConfig.class)))
                .thenAnswer(inv -> inv.getArgument(1));
        lenient().when(proxyConfigPort.findByJobId(any(UUID.class)))
                .thenReturn(Optional.empty());
        lenient().when(schemaPort.findByJobId(any(UUID.class)))
                .thenReturn(Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private Page<Job> buildPage(List<Job> content) {
        Page<Job> page = (Page<Job>) org.mockito.Mockito.mock(Page.class);
        lenient().when(page.map(any())).thenAnswer(inv -> {
            java.util.function.Function<Job, Job> fn = inv.getArgument(0);
            content.replaceAll(fn::apply);
            return page;
        });
        return page;
    }


    @Nested
    @DisplayName("createJob")
    class CreateJob {

        @Test
        @DisplayName("persists a CREATED job and schedules it")
        void createsAndSchedules() {
            stubSaveAndUpdateEcho();
            stubProxyAndSchemaPassthrough();
            Job input = baseScraperJob().status(null).build();

            Job result = service.createJob(input, userId);

            assertThat(result).isNotNull();
            ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
            verify(jobPort).save(saved.capture());
            assertThat(saved.getValue().status()).isEqualTo(JobStatus.CREATED);
            assertThat(saved.getValue().createdBy()).isEqualTo(userId);
            verify(jobScheduler).schedule(any(Job.class));
        }

        @Test
        @DisplayName("scheduling failure does not propagate (safeSchedule swallows)")
        void schedulingFailureIsSwallowed() {
            stubSaveAndUpdateEcho();
            stubProxyAndSchemaPassthrough();
            org.mockito.Mockito.doThrow(new RuntimeException("scheduler down"))
                    .when(jobScheduler).schedule(any(Job.class));

            Job result = service.createJob(baseScraperJob().build(), userId);

            assertThat(result).isNotNull(); // no exception bubbles up
            verify(jobPort).save(any(Job.class));
        }

        @Test
        @DisplayName("SCHEDULED job without cron is rejected")
        void scheduledWithoutCronThrows() {
            Job input = baseScraperJob()
                    .triggerType(JobTriggerType.SCHEDULED)
                    .cronExpression(null)
                    .build();

            assertThatThrownBy(() -> service.createJob(input, userId))
                    .isInstanceOf(DomainException.class);
            verify(jobPort, never()).save(any());
        }

        @Test
        @DisplayName("SCHEDULED job with invalid cron is rejected")
        void scheduledWithBadCronThrows() {
            Job input = baseScraperJob()
                    .triggerType(JobTriggerType.SCHEDULED)
                    .cronExpression("not-a-cron")
                    .build();

            assertThatThrownBy(() -> service.createJob(input, userId))
                    .isInstanceOf(DomainException.class);
            verify(jobPort, never()).save(any());
        }

        @Test
        @DisplayName("SCHEDULED job with valid UNIX cron is accepted")
        void scheduledWithValidCronPasses() {
            stubSaveAndUpdateEcho();
            stubProxyAndSchemaPassthrough();
            Job input = baseScraperJob()
                    .triggerType(JobTriggerType.SCHEDULED)
                    .cronExpression("0 12 * * *")
                    .build();

            Job result = service.createJob(input, userId);

            assertThat(result).isNotNull();
            verify(jobPort).save(any(Job.class));
        }

        @Test
        @DisplayName("CRAWLER job forces AUTO mode and drops the schema")
        void crawlerForcesAutoAndDropsSchema() {
            stubSaveAndUpdateEcho();
            stubProxyAndSchemaPassthrough();
            Job input = baseScraperJob()
                    .jobType(JobType.CRAWLER)
                    .extractionMode(ExtractionMode.CSS_SELECTOR)
                    .extractionSchema(ExtractionSchema.builder().jobId(jobId).build())
                    .build();

            service.createJob(input, userId);

            ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
            verify(jobPort).save(saved.capture());
            assertThat(saved.getValue().extractionMode()).isEqualTo(ExtractionMode.AUTO);
            verify(schemaPort, never()).saveOrUpdate(any());
        }

        @Test
        @DisplayName("WEBHOOK job gets generated credentials")
        void webhookJobGetsCredentials() {
            stubSaveAndUpdateEcho();
            stubProxyAndSchemaPassthrough();
            when(webhookGenerator.generate()).thenReturn(WebhookCredentials.builder()
                    .token("tok12345abcdef")
                    .secret("supersecret")
                    .enabled(true)
                    .build());

            Job input = baseScraperJob()
                    .triggerType(JobTriggerType.WEBHOOK)
                    .build();

            service.createJob(input, userId);

            ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
            verify(jobPort).save(saved.capture());
            assertThat(saved.getValue().webhookEnabled()).isTrue();
            assertThat(saved.getValue().webhookToken()).isEqualTo("tok12345abcdef");
        }

        @Test
        @DisplayName("non-webhook job has webhook disabled and no generator call")
        void nonWebhookJobDisablesWebhook() {
            stubSaveAndUpdateEcho();
            stubProxyAndSchemaPassthrough();

            service.createJob(baseScraperJob().build(), userId);

            ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
            verify(jobPort).save(saved.capture());
            assertThat(saved.getValue().webhookEnabled()).isFalse();
            verify(webhookGenerator, never()).generate();
        }
    }


    @Nested
    @DisplayName("read operations")
    class Reads {

        @Test
        @DisplayName("getJobById returns enriched job")
        void getByIdReturns() {
            when(jobPort.findById(jobId)).thenReturn(Optional.of(baseScraperJob().build()));
            when(schemaPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(proxyConfigPort.findByJobId(jobId)).thenReturn(Optional.empty());

            Job result = service.getJobById(jobId);

            assertThat(result.id()).isEqualTo(jobId);
        }

        @Test
        @DisplayName("getJobById throws when missing")
        void getByIdMissingThrows() {
            when(jobPort.findById(jobId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getJobById(jobId))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("getJobByIdAndUser throws when not owned by user")
        void getByIdAndUserMissingThrows() {
            when(jobPort.findByIdAndCreatedBy(jobId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getJobByIdAndUser(jobId, userId))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("getStats aggregates counts by status")
        void statsAggregates() {
            when(jobPort.countByStatus(userId)).thenReturn(Map.of(
                    JobStatus.CREATED, 2L,
                    JobStatus.RUNNING, 1L,
                    JobStatus.COMPLETED, 3L));

            JobStats stats = service.getStats(userId);

            assertThat(stats.total()).isEqualTo(6L);
            assertThat(stats.created()).isEqualTo(2L);
            assertThat(stats.running()).isEqualTo(1L);
            assertThat(stats.completed()).isEqualTo(3L);
            assertThat(stats.failed()).isZero();
        }

        @Test
        @DisplayName("search maps the page through enrich")
        void searchMapsPage() {
            JobSearchCriteria criteria = org.mockito.Mockito.mock(JobSearchCriteria.class);
            Page<Job> page = buildPage(new ArrayList<>(List.of(baseScraperJob().build())));
            when(jobPort.search(eq(criteria), anyInt(), anyInt())).thenReturn(page);

            Page<Job> result = service.search(criteria, 0, 20);

            assertThat(result).isNotNull();
            verify(jobPort).search(eq(criteria), eq(0), eq(20));
        }
    }


    @Nested
    @DisplayName("updateJob")
    class UpdateJob {

        @Test
        @DisplayName("preserves existing status and createdBy, reschedules")
        void updatePreservesStatusAndReschedules() {
            Job existing = baseScraperJob().status(JobStatus.RUNNING).build();
            when(jobPort.findById(jobId)).thenReturn(Optional.of(existing));
            when(schemaPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(proxyConfigPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(jobPort.update(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
            when(proxyConfigPort.saveOrUpdate(any(UUID.class), any(ProxyConfig.class)))
                    .thenAnswer(inv -> inv.getArgument(1));

            Job changes = baseScraperJob().name("renamed").createdBy(UUID.randomUUID()).build();

            service.updateJob(jobId, changes, userId);

            ArgumentCaptor<Job> updated = ArgumentCaptor.forClass(Job.class);
            verify(jobPort).update(updated.capture());
            assertThat(updated.getValue().status()).isEqualTo(JobStatus.RUNNING); // kept from existing
            assertThat(updated.getValue().createdBy()).isEqualTo(userId);          // kept from existing
            assertThat(updated.getValue().updatedBy()).isEqualTo(userId);
            verify(jobScheduler).reschedule(any(Job.class));
        }

        @Test
        @DisplayName("switching to CRAWLER deletes the schema")
        void updateToCrawlerDeletesSchema() {
            Job existing = baseScraperJob().build();
            when(jobPort.findById(jobId)).thenReturn(Optional.of(existing));
            when(schemaPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(proxyConfigPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(jobPort.update(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
            when(proxyConfigPort.saveOrUpdate(any(UUID.class), any(ProxyConfig.class)))
                    .thenAnswer(inv -> inv.getArgument(1));

            Job changes = baseScraperJob().jobType(JobType.CRAWLER).build();

            service.updateJob(jobId, changes, userId);

            verify(schemaPort).deleteByJobId(jobId);
        }
    }

    @Nested
    @DisplayName("deleteJob")
    class DeleteJob {

        @Test
        @DisplayName("deletes schema, proxy, job then unschedules")
        void deletesEverything() {
            when(jobPort.findById(jobId)).thenReturn(Optional.of(baseScraperJob().build()));

            service.deleteJob(jobId);

            verify(schemaPort).deleteByJobId(jobId);
            verify(proxyConfigPort).deleteByJobId(jobId);
            verify(jobPort).deleteById(jobId);
            verify(jobScheduler).unschedule(jobId);
        }

        @Test
        @DisplayName("is idempotent when job does not exist")
        void idempotentWhenMissing() {
            when(jobPort.findById(jobId)).thenReturn(Optional.empty());

            service.deleteJob(jobId);

            verify(jobPort, never()).deleteById(any());
            verify(schemaPort, never()).deleteByJobId(any());
            verifyNoInteractions(jobScheduler);
        }
    }


    @Nested
    @DisplayName("launchJob")
    class LaunchJob {

        @Test
        @DisplayName("transitions CREATED -> RUNNING and submits scrape async")
        void launchesFromCreated() {
            Job created = baseScraperJob().status(JobStatus.CREATED).build();
            Job running = baseScraperJob().status(JobStatus.RUNNING).build();
            // transitionToRunning -> getJobById (CREATED); then update; then getJobById again (RUNNING)
            when(jobPort.findById(jobId))
                    .thenReturn(Optional.of(created))
                    .thenReturn(Optional.of(running));
            when(schemaPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(proxyConfigPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(jobPort.update(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
            // run the submitted task immediately
            when(managedExecutor.runAsync(any(Runnable.class))).thenAnswer(inv -> {
                ((Runnable) inv.getArgument(0)).run();
                return CompletableFuture.completedFuture(null);
            });

            service.launchJob(jobId);

            ArgumentCaptor<Job> updated = ArgumentCaptor.forClass(Job.class);
            verify(jobPort).update(updated.capture());
            assertThat(updated.getValue().status()).isEqualTo(JobStatus.RUNNING);
            verify(scrapeEngine).runScrape(any(InternalScrapeRequest.class));
        }

        @Test
        @DisplayName("rejects launch from CANCELLED status")
        void rejectsNonLaunchableStatus() {
            Job cancelled = baseScraperJob().status(JobStatus.CANCELLED).build();
            when(jobPort.findById(jobId)).thenReturn(Optional.of(cancelled));
            when(schemaPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(proxyConfigPort.findByJobId(jobId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.launchJob(jobId))
                    .isInstanceOf(DomainException.class);
            verify(scrapeEngine, never()).runScrape(any());
        }

        @Test
        @DisplayName("scrape engine error does not propagate to caller")
        void scrapeErrorSwallowedAsync() {
            Job created = baseScraperJob().status(JobStatus.CREATED).build();
            Job running = baseScraperJob().status(JobStatus.RUNNING).build();
            when(jobPort.findById(jobId))
                    .thenReturn(Optional.of(created))
                    .thenReturn(Optional.of(running));
            when(schemaPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(proxyConfigPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(jobPort.update(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
            when(managedExecutor.runAsync(any(Runnable.class))).thenAnswer(inv -> {
                ((Runnable) inv.getArgument(0)).run();
                return CompletableFuture.completedFuture(null);
            });
            org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                    .when(scrapeEngine).runScrape(any());

            Job result = service.launchJob(jobId);

            assertThat(result.status()).isEqualTo(JobStatus.RUNNING);
        }
    }


    @Nested
    @DisplayName("pauseJob")
    class PauseJob {

        @Test
        @DisplayName("pauses a RUNNING job")
        void pausesRunning() {
            Job running = baseScraperJob().status(JobStatus.RUNNING).build();
            when(jobPort.findById(jobId)).thenReturn(Optional.of(running));
            when(schemaPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(proxyConfigPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(jobPort.update(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

            Job result = service.pauseJob(jobId);

            ArgumentCaptor<Job> updated = ArgumentCaptor.forClass(Job.class);
            verify(jobPort).update(updated.capture());
            assertThat(updated.getValue().status()).isEqualTo(JobStatus.PAUSED);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("rejects pause when not RUNNING")
        void rejectsPauseWhenNotRunning() {
            Job created = baseScraperJob().status(JobStatus.CREATED).build();
            when(jobPort.findById(jobId)).thenReturn(Optional.of(created));
            when(schemaPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(proxyConfigPort.findByJobId(jobId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.pauseJob(jobId))
                    .isInstanceOf(DomainException.class);
            verify(jobPort, never()).update(any());
        }
    }

    @Nested
    @DisplayName("duplicateJob")
    class DuplicateJob {

        @Test
        @DisplayName("creates a copy with MANUAL trigger and (copy) suffix")
        void duplicatesAsManualCopy() {
            Job existing = baseScraperJob()
                    .triggerType(JobTriggerType.SCHEDULED)
                    .cronExpression("0 12 * * *")
                    .build();
            when(jobPort.findById(jobId)).thenReturn(Optional.of(existing));
            when(schemaPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(proxyConfigPort.findByJobId(jobId)).thenReturn(Optional.empty());
            when(jobPort.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
            when(proxyConfigPort.saveOrUpdate(nullable(UUID.class), any(ProxyConfig.class)))
                    .thenAnswer(inv -> inv.getArgument(1));

            service.duplicateJob(jobId, userId);

            ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
            verify(jobPort).save(saved.capture());
            assertThat(saved.getValue().name()).endsWith("(copy)");
            assertThat(saved.getValue().triggerType()).isEqualTo(JobTriggerType.MANUAL);
            assertThat(saved.getValue().cronExpression()).isNull();
            assertThat(saved.getValue().status()).isEqualTo(JobStatus.CREATED);
        }
    }


    @Nested
    @DisplayName("webhook management")
    class Webhooks {

        @Test
        @DisplayName("rotateWebhookSecret regenerates and persists")
        void rotateRegenerates() {
            Job webhookJob = baseScraperJob()
                    .triggerType(JobTriggerType.WEBHOOK)
                    .webhookToken("existing-token")
                    .build();
            when(jobPort.findById(jobId)).thenReturn(Optional.of(webhookJob));
            when(webhookGenerator.regenerateSecret("existing-token"))
                    .thenReturn(WebhookCredentials.builder()
                            .token("existing-token")
                            .secret("new-secret")
                            .enabled(true)
                            .build());

            WebhookCredentials creds = service.rotateWebhookSecret(jobId);

            assertThat(creds.secret()).isEqualTo("new-secret");
            verify(jobPort).updateWebhookCredentials(eq(jobId), any(WebhookCredentials.class));
        }

        @Test
        @DisplayName("rotateWebhookSecret rejects non-webhook job")
        void rotateRejectsNonWebhook() {
            Job manualJob = baseScraperJob().triggerType(JobTriggerType.MANUAL).build();
            when(jobPort.findById(jobId)).thenReturn(Optional.of(manualJob));

            assertThatThrownBy(() -> service.rotateWebhookSecret(jobId))
                    .isInstanceOf(DomainException.class);
            verify(jobPort, never()).updateWebhookCredentials(any(), any());
        }

        @Test
        @DisplayName("disableWebhook persists disabled credentials")
        void disableWebhook() {
            Job webhookJob = baseScraperJob()
                    .triggerType(JobTriggerType.WEBHOOK)
                    .webhookToken("tok")
                    .build();
            when(jobPort.findById(jobId)).thenReturn(Optional.of(webhookJob));

            service.disableWebhook(jobId);

            ArgumentCaptor<WebhookCredentials> creds = ArgumentCaptor.forClass(WebhookCredentials.class);
            verify(jobPort).updateWebhookCredentials(eq(jobId), creds.capture());
            assertThat(creds.getValue().enabled()).isFalse();
            assertThat(creds.getValue().secret()).isNull();
        }
    }
}