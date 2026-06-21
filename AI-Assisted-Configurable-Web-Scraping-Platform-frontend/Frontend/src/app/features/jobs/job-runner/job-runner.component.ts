import {
  Component,
  computed, effect,
  inject,
  input,
  OnDestroy,
  OnInit,
  output,
  signal,
} from '@angular/core';
import {CommonModule, DOCUMENT} from '@angular/common';

import {interval, Subscription, timer} from 'rxjs';
import {switchMap} from 'rxjs/operators';

import {JobService} from '../../../core/services/job.service';
import {ApiResponse} from '../../../core/models/api-response.model';
import {ExtractionMode, Job} from '../../../core/models/job.model';
import {DiscoveredFile} from '../../../core/models/scrape-result.model';

export type PipelineState = 'idle' | 'extract' | 'scrape' | 'clean' | 'done' | 'error';
export type JobType = 'CRAWLER' | 'SCRAPER';
type ViewMode = 'table' | 'json';

interface JobWithProgress extends Job {
  pagesCrawled?: number;
  recordsExtracted?: number;
}

const STEP_ORDER: PipelineState[] = ['extract', 'scrape', 'clean', 'done'];

@Component({
  selector: 'app-job-runner',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './job-runner.component.html',
  styleUrls: ['./job-runner.component.scss'],
})
export class JobRunnerComponent implements OnInit, OnDestroy {
  showSaveActions = input(false);
  jobId = input<string | null>(null);
  autoLaunch = input(false);
  mode = input<ExtractionMode>('AUTO');
  jobType = input<JobType>('SCRAPER');
  canSave = input(true);

  saveJob = output<void>();
  saveAndRunJob = output<void>();
  jobCompleted = output<void>();

  private jobSvc = inject(JobService);
  private doc = inject(DOCUMENT);

  pipelineState = signal<PipelineState>('idle');
  launching = signal(false);
  viewMode = signal<ViewMode>('table');

  results = signal<Record<string, unknown>[]>([]);
  errorLogs = signal<string[]>([]);
  scrapeLogs = signal<string[]>([]);
  logsExpanded = signal(false);
  discoveredFiles = signal<DiscoveredFile[]>([]);
  resumePolling = input(false);

  stats = signal({
    pagesCrawled: 0,
    recordsExtracted: 0,
    recordsCleaned: 0,
    errors: 0,
    duration: 0,
  });

  private pollSubscription: Subscription | null = null;
  private timerSubscription: Subscription | null = null;
  private durationInterval: Subscription | null = null;
  private autoLaunched = false;

  isCrawler = computed(() => this.jobType() === 'CRAWLER');

  step1 = computed(() =>
    this.isCrawler()
      ? {label: 'Crawl', desc: 'Crawling pages'}
      : {label: 'Scrape', desc: 'Fetching page'}
  );

  step2 = computed(() =>
    this.isCrawler()
      ? {label: 'Extract', desc: 'Extract from pages'}
      : {label: 'Extract', desc: 'Extract structured data'}
  );

  canLaunch = computed(() =>
    ['idle', 'done', 'error'].includes(this.pipelineState())
  );

  canStop = computed(() =>
    ['extract', 'scrape', 'clean'].includes(this.pipelineState())
  );

  isRunning = computed(() =>
    ['extract', 'scrape', 'clean'].includes(this.pipelineState())
  );

  progressPct = computed(() => {
    const map: Partial<Record<PipelineState, number>> = {
      idle: 0, extract: 20, scrape: 55, clean: 80, done: 100, error: 0,
    };
    return map[this.pipelineState()] ?? 0;
  });

  tableColumns = computed(() => {
    const r = this.results();
    return r.length ? Object.keys(r[0]) : [];
  });

  resultsJson = computed(() => JSON.stringify(this.results(), null, 2));

  runningLabel = computed(() => {
    const crawler = this.isCrawler();
    const map: Partial<Record<PipelineState, string>> = {
      extract: crawler ? 'Crawling pages…' : 'Scraping page…',
      scrape: 'Extracting records…',
      clean: 'Cleaning & normalizing data…',
    };
    return map[this.pipelineState()] ?? '';
  });

  visibleLogs = computed(() =>
    this.scrapeLogs().filter(
      l => l.includes('[WARNING]') || l.includes('[ERROR]')
    )
  );
  constructor() {
    effect(() => {
      if (this.resumePolling() && this.jobId() && this.pipelineState() === 'idle') {
        this.pipelineState.set('extract');
        this.startDurationTimer();
        this.pollStatus(this.jobId()!);
      }
    });
  }

  ngOnInit(): void {
    if (this.resumePolling() && this.jobId()) {
      this.pipelineState.set('extract');
      this.startDurationTimer();
      this.pollStatus(this.jobId()!);
      return;
    }
    this.tryAutoLaunch();
  }

  ngOnDestroy(): void {
    this.stopAll();
  }

  private tryAutoLaunch(): void {
    if (this.autoLaunch() && this.jobId() && !this.autoLaunched) {
      this.autoLaunched = true;
      this.launch();
    }
  }

  isPastStep(step: PipelineState): boolean {
    const current = STEP_ORDER.indexOf(this.pipelineState());
    const target = STEP_ORDER.indexOf(step);
    return current > target && current !== -1;
  }

  toggleLogs(): void {
    this.logsExpanded.update(v => !v);
  }

  cleanLog(log: string): string {
    const levelMatch = log.match(/^\[(?:WARNING|ERROR)]/);
    const level = levelMatch ? levelMatch[0] + ' ' : '';
    const dashIndex = log.indexOf(' — ');
    const message = dashIndex !== -1 ? log.substring(dashIndex + 3) : log;

    const cleaned = message
      .replace(/\b[0-9a-f-]{36}\b/gi, '')
      .replace(/\b(?:job_id|job|url)=\S+/g, '')
      .replace(/\s+/g, ' ')
      .trim();

    return (level + cleaned).trim() || log;
  }

  launch(): void {
    const id = this.jobId();
    if (!id) return;

    this.launching.set(true);
    this.pipelineState.set('extract');

    this.stats.set({
      pagesCrawled: 0, recordsExtracted: 0, recordsCleaned: 0,
      errors: 0, duration: 0,
    });

    this.results.set([]);
    this.errorLogs.set([]);
    this.scrapeLogs.set([]);
    this.logsExpanded.set(false);
    this.discoveredFiles.set([]);

    this.startDurationTimer();

    this.jobSvc.launch(id).subscribe({
      next: () => {
        this.launching.set(false);
        this.pollStatus(id);
      },
      error: (err: any) => {
        this.launching.set(false);
        this.pipelineState.set('error');
        this.errorLogs.set([
          this.extractErrorMessage(err, 'Failed to launch job. The server may be unreachable.'),
        ]);
        this.stopAll();
      },
    });
  }

  stop(): void {
    const id = this.jobId();
    if (!id) return;

    this.jobSvc.pause(id).subscribe({
      next: () => {
        this.pipelineState.set('idle');
        this.stopAll();
      },
      error: () => {
        this.pipelineState.set('idle');
        this.stopAll();
      },
    });
  }

  private setLogsAndAutoExpand(logs: string[]): void {
    this.scrapeLogs.set(logs);

    if (
      this.pipelineState() === 'error' &&
      logs.some(l => l.includes('[WARNING]') || l.includes('[ERROR]'))
    ) {
      this.logsExpanded.set(true);
    }
  }

  private pollStatus(id: string): void {
    this.pollSubscription?.unsubscribe();

    this.pollSubscription = interval(2000)
      .pipe(switchMap(() => this.jobSvc.getById(id)))
      .subscribe({
        next: (res: ApiResponse<Job>) => {
          const job = res.data as JobWithProgress;

          if (job.status === 'RUNNING') {
            const crawled = job.pagesCrawled ?? 0;
            const extracted = job.recordsExtracted ?? 0;

            this.stats.update(s => ({...s, pagesCrawled: crawled, recordsExtracted: extracted}));
            if (extracted > 0) this.pipelineState.set('scrape');
          }

          if (job.status === 'COMPLETED') {
            this.stopPolling();
            this.pipelineState.set('clean');

            this.timerSubscription = timer(900).subscribe(() => {
              this.jobSvc.getResults(id).subscribe({
                next: res => {
                  const result = res.data;

                  this.results.set(result.records ?? []);
                  this.setLogsAndAutoExpand(result.logs ?? []);
                  this.errorLogs.set(result.errors ?? []);
                  this.discoveredFiles.set(result.discoveredFiles ?? []);

                  this.stats.update(s => ({
                    ...s,
                    pagesCrawled: result.pagesCrawled,
                    recordsExtracted: result.records?.length ?? 0,
                    recordsCleaned: result.records?.length ?? 0,
                    errors: result.errors?.length ?? 0,
                    duration: Math.round(result.durationMs / 1000),
                  }));

                  this.pipelineState.set('done');
                  this.jobCompleted.emit();
                  this.durationInterval?.unsubscribe();
                },
                error: (err: any) => {
                  this.pipelineState.set('error');
                  this.errorLogs.set([
                    this.extractErrorMessage(err, 'Job completed but failed to fetch results.'),
                  ]);
                  this.durationInterval?.unsubscribe();
                },
              });
            });
          }

          if (job.status === 'FAILED' || job.status === 'CANCELLED') {
            this.stopAll();
            this.pipelineState.set('error');
            this.discoveredFiles.set([]);
            this.errorLogs.set([`Job ${job.status.toLowerCase()}: ${job.name}`]);

            this.jobSvc.getResults(id).subscribe({
              next: res => {
                const errors = res.data?.errors ?? [];
                const logs = res.data?.logs ?? [];

                if (errors.length) this.errorLogs.set(errors);
                this.setLogsAndAutoExpand(logs);

                if (res.data) {
                  this.stats.update(s => ({
                    ...s,
                    pagesCrawled: res.data.pagesCrawled ?? s.pagesCrawled,
                    errors: errors.length,
                    duration: res.data.durationMs
                      ? Math.round(res.data.durationMs / 1000)
                      : s.duration,
                  }));
                }
              },
              error: () => {
              },
            });
          }
        },
        error: (err: any) => {
          this.stopPolling();
          this.pipelineState.set('error');
          this.errorLogs.set([
            this.extractErrorMessage(err, 'Lost connection to server while monitoring job.'),
          ]);
        },
      });
  }

  private extractErrorMessage(err: any, fallback: string): string {
    if (typeof err === 'string') return err;
    if (err?.error?.message) return err.error.message;
    if (err?.error?.error) return err.error.error;
    if (err?.message) return err.message;
    if (err?.statusText) return `${err.status} — ${err.statusText}`;
    return fallback;
  }

  private startDurationTimer(): void {
    this.durationInterval?.unsubscribe();
    this.durationInterval = interval(1000).subscribe(() => {
      this.stats.update(s => ({...s, duration: s.duration + 1}));
    });
  }

  private stopPolling(): void {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = null;
    this.timerSubscription?.unsubscribe();
    this.timerSubscription = null;
  }

  private stopAll(): void {
    this.stopPolling();
    this.durationInterval?.unsubscribe();
    this.durationInterval = null;
  }

  exportCsv(): void {
    const cols = this.tableColumns();
    const rows = this.results();
    if (!rows.length) return;

    const header = cols.join(',');
    const body = rows.map(r =>
      cols.map(c => {
        const val = r[c];
        const str = val != null ? String(val) : '';
        return `"${str.replace(/"/g, '""')}"`;
      }).join(',')
    ).join('\n');

    const blob = new Blob([`${header}\n${body}`], {type: 'text/csv'});
    const url = URL.createObjectURL(blob);
    const a = this.doc.createElement('a');
    a.href = url;
    a.download = `job-${this.jobId()}-results.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
