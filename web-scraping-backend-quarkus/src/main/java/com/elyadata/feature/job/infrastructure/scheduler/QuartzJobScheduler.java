package com.elyadata.feature.job.infrastructure.scheduler;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.elyadata.feature.job.domain.model.Job;
import com.elyadata.feature.job.domain.model.JobStatus;
import com.elyadata.feature.job.domain.model.JobTriggerType;
import com.elyadata.feature.job.domain.service.JobScheduler;
import com.elyadata.feature.job.domain.service.port.JobPort;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class QuartzJobScheduler implements JobScheduler {

    private static final String GROUP = "scrape-jobs";
    private static final CronParser PARSER =
            new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

    private final Scheduler quartz;
    private final JobPort jobPort;

    void onStart(@Observes StartupEvent ev) {
        log.info("[SCHED] Bootstrapping scheduled jobs from DB…");
        int ok = 0;
        int skipped = 0;
        for (Job job : jobPort.findAll()) {
            if (!isSchedulable(job)) {
                skipped++;
                continue;
            }
            try {
                schedule(job);
                ok++;
            } catch (Exception e) {
                log.warn("[SCHED] Skip job={} — invalid cron='{}' ({})",
                        job.id(), job.cronExpression(), e.getMessage());
                skipped++;
            }
        }
        log.info("[SCHED] Bootstrap done — scheduled={} skipped={}", ok, skipped);
    }

    @Override
    public void schedule(Job job) {
        if (!isSchedulable(job)) return;

        JobKey key = key(job.id());
        try {
            if (quartz.checkExists(key)) {
                reschedule(job);
                return;
            }

            JobDetail detail = JobBuilder.newJob(ScrapeJobExecutor.class)
                    .withIdentity(key)
                    .usingJobData(ScrapeJobExecutor.JOB_ID_KEY, job.id().toString())
                    .storeDurably(false)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(job.id()))
                    .withSchedule(CronScheduleBuilder
                            .cronSchedule(job.cronExpression())
                            .withMisfireHandlingInstructionDoNothing())
                    .forJob(detail)
                    .build();

            quartz.scheduleJob(detail, trigger);
            log.info("[SCHED] Registered job={} cron='{}'", job.id(), job.cronExpression());
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to schedule job " + job.id(), e);
        }
    }

    @Override
    public void reschedule(Job job) {
        if (!isSchedulable(job)) {
            unschedule(job.id());
            return;
        }
        try {
            TriggerKey tk = triggerKey(job.id());
            if (!quartz.checkExists(tk)) {
                schedule(job);
                return;
            }
            Trigger newTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(tk)
                    .withSchedule(CronScheduleBuilder
                            .cronSchedule(job.cronExpression())
                            .withMisfireHandlingInstructionDoNothing())
                    .forJob(key(job.id()))
                    .build();
            quartz.rescheduleJob(tk, newTrigger);
            log.info("[SCHED] Rescheduled job={} cron='{}'", job.id(), job.cronExpression());
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to reschedule job " + job.id(), e);
        }
    }

    @Override
    public void unschedule(UUID jobId) {
        try {
            boolean removed = quartz.deleteJob(key(jobId));
            if (removed) log.info("[SCHED] Unregistered job={}", jobId);
        } catch (SchedulerException e) {
            log.warn("[SCHED] Failed to unschedule job={} ({})", jobId, e.getMessage());
        }
    }

    @Override
    public List<Instant> nextFireTimes(String cron, int count) {
        if (cron == null || cron.isBlank() || count <= 0) return List.of();
        ExecutionTime et = ExecutionTime.forCron(PARSER.parse(cron.trim()));
        List<Instant> out = new ArrayList<>(count);
        ZonedDateTime cursor = ZonedDateTime.now(ZoneId.systemDefault());
        for (int i = 0; i < count; i++) {
            Optional<ZonedDateTime> next = et.nextExecution(cursor);
            if (next.isEmpty()) break;
            out.add(next.get().toInstant());
            cursor = next.get();
        }
        return out;
    }

    @Override
    public List<UpcomingRun> upcomingRunsBetween(Instant from, Instant to) {
        List<UpcomingRun> out = new ArrayList<>();
        for (Job job : jobPort.findAll()) {
            if (!isSchedulable(job)) continue;
            try {
                ExecutionTime et = ExecutionTime.forCron(PARSER.parse(job.cronExpression().trim()));
                ZonedDateTime cursor = ZonedDateTime.ofInstant(from, ZoneId.systemDefault());
                ZonedDateTime end = ZonedDateTime.ofInstant(to, ZoneId.systemDefault());
                while (true) {
                    Optional<ZonedDateTime> next = et.nextExecution(cursor);
                    if (next.isEmpty() || next.get().isAfter(end)) break;
                    out.add(new UpcomingRun(job.id(), job.name(), next.get().toInstant()));
                    cursor = next.get();
                }
            } catch (Exception ignored) { /* skip invalid cron */ }
        }
        out.sort(Comparator.comparing(UpcomingRun::fireAt));
        return out;
    }

    private static boolean isSchedulable(Job j) {
        return j != null
                && j.triggerType() == JobTriggerType.SCHEDULED
                && j.cronExpression() != null
                && !j.cronExpression().isBlank()
                && j.status() != JobStatus.CANCELLED;
    }

    private static JobKey key(UUID id) {
        return JobKey.jobKey(id.toString(), GROUP);
    }

    private static TriggerKey triggerKey(UUID id) {
        return TriggerKey.triggerKey(id.toString(), GROUP);
    }
}