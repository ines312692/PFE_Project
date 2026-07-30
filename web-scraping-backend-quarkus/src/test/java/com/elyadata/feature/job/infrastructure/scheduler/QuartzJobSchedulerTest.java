package com.elyadata.feature.job.infrastructure.scheduler;

import com.elyadata.feature.job.domain.model.Job;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.model.JobTriggerType;
import com.elyadata.feature.job.domain.service.JobScheduler.UpcomingRun;
import com.elyadata.feature.job.domain.service.port.JobPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Scheduler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuartzJobScheduler")
class QuartzJobSchedulerTest {

    @Mock
    Scheduler quartz;

    @Mock
    JobPort jobPort;

    QuartzJobScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new QuartzJobScheduler(quartz, jobPort);
    }

    private Job scheduledJob(String cron, JobStatus status) {
        return Job.builder()
                .id(UUID.randomUUID())
                .name("Test Job")
                .status(status)
                .triggerType(JobTriggerType.SCHEDULED)
                .cronExpression(cron)
                .build();
    }

    private Job manualJob() {
        return Job.builder()
                .id(UUID.randomUUID())
                .name("Manual Job")
                .status(JobStatus.CREATED)
                .triggerType(JobTriggerType.MANUAL)
                .cronExpression(null)
                .build();
    }

    @Nested
    @DisplayName("nextFireTimes()")
    class NextFireTimes {

        @Test
        @DisplayName("returns empty list when cron is null")
        void nullCron_returnsEmpty() {
            assertThat(scheduler.nextFireTimes(null, 5)).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when cron is blank")
        void blankCron_returnsEmpty() {
            assertThat(scheduler.nextFireTimes("   ", 5)).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when count is zero")
        void zeroCount_returnsEmpty() {
            assertThat(scheduler.nextFireTimes("* * * * *", 0)).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when count is negative")
        void negativeCount_returnsEmpty() {
            assertThat(scheduler.nextFireTimes("* * * * *", -1)).isEmpty();
        }

        @Test
        @DisplayName("returns 1 fire time for count=1 with every-minute cron")
        void count1_returnsOneFire() {
            List<Instant> result = scheduler.nextFireTimes("* * * * *", 1);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("returns exactly 5 fire times for count=5 with every-minute cron")
        void count5_returns5Fires() {
            List<Instant> result = scheduler.nextFireTimes("* * * * *", 5);
            assertThat(result).hasSize(5);
        }

        @Test
        @DisplayName("fire times are strictly increasing")
        void fireTimes_areIncreasing() {
            List<Instant> result = scheduler.nextFireTimes("* * * * *", 4);
            for (int i = 1; i < result.size(); i++) {
                assertThat(result.get(i)).isAfter(result.get(i - 1));
            }
        }

        @Test
        @DisplayName("fire times are in the future")
        void fireTimes_areInFuture() {
            Instant now = Instant.now();
            List<Instant> result = scheduler.nextFireTimes("* * * * *", 3);
            result.forEach(t -> assertThat(t).isAfterOrEqualTo(now));
        }

        @Test
        @DisplayName("daily cron at 9am returns times about 24h apart")
        void dailyCron_timesAreDailyApart() {
            List<Instant> result = scheduler.nextFireTimes("0 9 * * *", 2);
            assertThat(result).hasSize(2);
            long gapSeconds = ChronoUnit.SECONDS.between(result.get(0), result.get(1));
            assertThat(gapSeconds).isBetween(23 * 3600L, 25 * 3600L);
        }
    }

    @Nested
    @DisplayName("upcomingRunsBetween()")
    class UpcomingRunsBetween {

        @Test
        @DisplayName("returns empty list when job list is empty")
        void emptyJobList_returnsEmpty() {
            when(jobPort.findAll()).thenReturn(List.of());

            Instant from = Instant.now();
            Instant to = from.plus(1, ChronoUnit.HOURS);

            assertThat(scheduler.upcomingRunsBetween(from, to)).isEmpty();
        }

        @Test
        @DisplayName("filters out MANUAL trigger jobs")
        void manualJobs_areFiltered() {
            when(jobPort.findAll()).thenReturn(List.of(manualJob()));

            Instant from = Instant.now();
            Instant to = from.plus(1, ChronoUnit.HOURS);

            assertThat(scheduler.upcomingRunsBetween(from, to)).isEmpty();
        }

        @Test
        @DisplayName("filters out CANCELLED scheduled jobs")
        void cancelledJobs_areFiltered() {
            when(jobPort.findAll()).thenReturn(
                    List.of(scheduledJob("* * * * *", JobStatus.CANCELLED)));

            Instant from = Instant.now();
            Instant to = from.plus(1, ChronoUnit.HOURS);

            assertThat(scheduler.upcomingRunsBetween(from, to)).isEmpty();
        }

        @Test
        @DisplayName("returns upcoming runs for valid scheduled job within window")
        void scheduledJob_returnsRunsInWindow() {
            Job job = scheduledJob("* * * * *", JobStatus.CREATED);
            when(jobPort.findAll()).thenReturn(List.of(job));

            Instant from = Instant.now();
            Instant to = from.plus(3, ChronoUnit.MINUTES);

            List<UpcomingRun> result = scheduler.upcomingRunsBetween(from, to);

            assertThat(result).isNotEmpty();
            result.forEach(run -> {
                assertThat(run.jobId()).isEqualTo(job.id());
                assertThat(run.fireAt()).isBetween(from, to);
            });
        }

        @Test
        @DisplayName("returns runs sorted by fireAt ascending")
        void multipleJobs_resultsSortedByFireAt() {
            Job jobA = scheduledJob("* * * * *", JobStatus.CREATED);
            Job jobB = scheduledJob("* * * * *", JobStatus.RUNNING);
            when(jobPort.findAll()).thenReturn(List.of(jobA, jobB));

            Instant from = Instant.now();
            Instant to = from.plus(5, ChronoUnit.MINUTES);

            List<UpcomingRun> result = scheduler.upcomingRunsBetween(from, to);

            for (int i = 1; i < result.size(); i++) {
                assertThat(result.get(i).fireAt())
                        .isAfterOrEqualTo(result.get(i - 1).fireAt());
            }
        }
    }

    @Nested
    @DisplayName("schedule() with non-schedulable jobs")
    class ScheduleNonSchedulable {

        @Test
        @DisplayName("MANUAL trigger job is silently skipped — no quartz interaction")
        void manualJob_noQuartzCall() throws Exception {
            scheduler.schedule(manualJob());
            verify(quartz, never()).checkExists(org.mockito.ArgumentMatchers.any(org.quartz.JobKey.class));
        }

        @Test
        @DisplayName("CANCELLED scheduled job is silently skipped")
        void cancelledJob_noQuartzCall() throws Exception {
            scheduler.schedule(scheduledJob("* * * * *", JobStatus.CANCELLED));
            verify(quartz, never()).checkExists(org.mockito.ArgumentMatchers.any(org.quartz.JobKey.class));
        }

        @Test
        @DisplayName("job with blank cron is silently skipped")
        void blankCronJob_noQuartzCall() throws Exception {
            scheduler.schedule(scheduledJob("   ", JobStatus.CREATED));
            verify(quartz, never()).checkExists(org.mockito.ArgumentMatchers.any(org.quartz.JobKey.class));
        }

        @Test
        @DisplayName("null job is silently skipped")
        void nullJob_noQuartzCall() throws Exception {
            scheduler.schedule(null);
            verify(quartz, never()).checkExists(org.mockito.ArgumentMatchers.any(org.quartz.JobKey.class));
        }
    }
}
