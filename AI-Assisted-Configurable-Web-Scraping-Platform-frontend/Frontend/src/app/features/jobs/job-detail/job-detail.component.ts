import {Component, computed, HostListener, inject, OnDestroy, OnInit, signal} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {DatePipe, LowerCasePipe} from '@angular/common';
import {JobService} from '../../../core/services/job.service';
import {Job, PageAction, PageActionType} from '../../../core/models/job.model';
import {DiscoveredFile, ScrapeResultResponse, toJobExecution} from '../../../core/models/scrape-result.model';
import {JobExecution, EXECUTION_STATUS_LABELS} from '../../../core/models/job-execution.model';
import {ROTATION_STRATEGY_LABELS} from '../../../core/models/proxy.model';
import {DomSanitizer, SafeResourceUrl} from '@angular/platform-browser';
import {PAGE_ACTION_META} from '../../../core/models/job-form.model';

const LLM_MODEL_LABELS: Record<string, string> = {
  GPT_4O: 'GPT-4o',
  GPT_4O_MINI: 'GPT-4o Mini',
  CLAUDE_3_5_SONNET: 'Claude 3.5 Sonnet',
  CLAUDE_3_HAIKU: 'Claude 3 Haiku',
  GEMINI_1_5_PRO: 'Gemini 1.5 Pro',
  GEMINI_1_5_FLASH: 'Gemini 1.5 Flash',
};

type TabId = 'overview' | 'extraction' | 'network' | 'history' | 'results';

@Component({
  selector: 'app-job-detail',
  standalone: true,
  imports: [DatePipe, LowerCasePipe],
  templateUrl: './job-detail.component.html',
  styleUrl: './job-detail.component.scss',
})
export class JobDetailComponent implements OnInit, OnDestroy{

  private readonly jobService = inject(JobService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly sanitizer = inject(DomSanitizer);

  job = signal<Job | null>(null);
  loading = signal(false);
  scrapeResult = signal<ScrapeResultResponse | null>(null);
  resultsLoading = signal(false);
  viewMode = signal<'table' | 'json'>('table');
  activeTab = signal<TabId>('overview');
  executions = signal<JobExecution[]>([]);
  selectedExecId = signal<string | null>(null);
  execViewMode = signal<'table' | 'json'>('table');

  canLaunch = computed(() => ['CREATED', 'PAUSED', 'FAILED', 'CANCELLED', 'COMPLETED'].includes(this.job()?.status ?? ''));
  canPause = computed(() => this.job()?.status === 'RUNNING');
  isCompleted = computed(() => this.job()?.status === 'COMPLETED');

  isScraper = computed(() => this.job()?.jobType === 'SCRAPER');
  isCrawler = computed(() => this.job()?.jobType === 'CRAWLER');
  previewPage = signal<{ url: string; html: string; length: number; safeUrl: SafeResourceUrl } | null>(null);

  hasFields = computed(() => {
    const fields = this.job()?.extractionSchema?.fields;
    return Array.isArray(fields) && fields.length > 0;
  });
  isLlm = computed(() => this.job()?.extractionMode === 'LLM');
  isRegex = computed(() => this.job()?.extractionMode === 'REGEX');
  llmModelLabel = computed(() => {
    const model = this.job()?.extractionSchema?.llmModel ?? '';
    return LLM_MODEL_LABELS[model] ?? model;
  });

  hasSeedUrls = computed(() => {
    const urls = this.job()?.scraperConfig?.seedUrls;
    return Array.isArray(urls) && urls.length > 0;
  });

  tableColumns = computed(() => {
    const records = this.scrapeResult()?.records;
    return records?.length ? Object.keys(records[0]) : [];
  });

  resultsJson = computed(() =>
    JSON.stringify(this.scrapeResult()?.records ?? [], null, 2)
  );

  discoveredFiles = computed<DiscoveredFile[]>(
    () => this.scrapeResult()?.discoveredFiles ?? []
  );

  hasDiscoveredFiles = computed(() => this.discoveredFiles().length > 0);

  selectedExec = computed<JobExecution | null>(() =>
    this.executions().find(e => e.id === this.selectedExecId()) ?? null
  );

  execColumns = computed<string[]>(() => {
    const records = this.selectedExec()?.recordsData ?? [];
    return records.length ? Object.keys(records[0]) : [];
  });

  execJson = computed(() =>
    JSON.stringify(this.selectedExec()?.recordsData ?? [], null, 2)
  );

  tabs = computed<{ id: TabId; label: string; icon: string }[]>(() => {
    const base: { id: TabId; label: string; icon: string }[] = [
      {id: 'overview', label: 'Overview', icon: 'doc'},
      {id: 'extraction', label: 'Schema', icon: 'code'},
      {id: 'network', label: 'Network & Retry', icon: 'shield'},
      {id: 'history', label: `History (${this.executions().length})`, icon: 'history'},
    ];
    if (this.isCompleted()) {
      const count = this.scrapeResult()?.records?.length ?? 0;
      base.push({id: 'results', label: `Results (${count})`, icon: 'table'});
    }
    return base;
  });

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.loading.set(true);
    this.jobService.getById(id).subscribe({
      next: res => {
        this.job.set(res.data);
        this.loading.set(false);
        if (res.data.status === 'COMPLETED') this.loadResults(id);
        this.loadHistory(id);
      },
      error: () => void this.router.navigate(['/jobs']),
    });
  }

  rotationStrategyLabel = computed(() => {
    const strategy = this.job()?.proxyConfig?.rotationStrategy;
    if (!strategy) return '—';
    return ROTATION_STRATEGY_LABELS[strategy]?.label ?? strategy;
  });

  private loadResults(id: string): void {
    this.resultsLoading.set(true);
    this.jobService.getResults(id).subscribe({
      next: res => {
        this.scrapeResult.set(res.data);
        this.resultsLoading.set(false);
      },
      error: () => this.resultsLoading.set(false),
    });
  }

  private loadHistory(id: string): void {
    this.jobService.getHistory(id).subscribe({
      next: (res: { data: ScrapeResultResponse[] }) => {
        const raw = res.data ?? [];
        const total = raw.length;
        this.executions.set(raw.map((r, i) => toJobExecution(r, i, total)));
      },
      error: () => this.executions.set([]),
    });
  }

  private downloadCsv(records: Record<string, unknown>[], filename: string): void {
    if (!records.length) return;
    const cols = Object.keys(records[0]);
    const header = cols.join(',');
    const body = records
      .map(r =>
        cols.map(c => {
          const val = r[c];
          const str = val != null ? String(val) : '';
          return `"${str.replace(/"/g, '""')}"`;
        }).join(',')
      )
      .join('\n');
    const blob = new Blob([`${header}\n${body}`], {type: 'text/csv'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  setTab(id: TabId) {
    this.activeTab.set(id);
  }

  selectExec(id: string) {
    this.selectedExecId.set(this.selectedExecId() === id ? null : id);
  }

  statusLabel(status: string): string {
    return EXECUTION_STATUS_LABELS[status] ?? status;
  }

  setExecViewMode(mode: 'table' | 'json', event: Event): void {
    event.stopPropagation();
    this.execViewMode.set(mode);
  }

  exportExecCsv(exec: JobExecution, event: Event): void {
    event.stopPropagation();
    this.downloadCsv(exec.recordsData, `job-${this.job()?.id}-exec-${exec.num}.csv`);
  }

  exportCsv(): void {
    this.downloadCsv(
      this.scrapeResult()?.records ?? [],
      `job-${this.job()?.id}-results.csv`
    );
  }

  goBack() {
    void this.router.navigate(['/jobs']);
  }

  goToEdit() {
    void this.router.navigate(['/jobs', this.job()!.id, 'edit']);
  }

  launch() {
    void this.router.navigate(['/jobs', this.job()!.id, 'run']);
  }

  pause() {
    this.jobService.pause(this.job()!.id).subscribe(res => this.job.set(res.data));
  }

  goToPipeline(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) void this.router.navigate(['/jobs', id, 'pipeline']);
  }


  htmlViewMode = signal<'preview' | 'source'>('preview');
  private blobUrls: string[] = [];

  isHtmlMode = computed(() => {
    const recs = this.scrapeResult()?.records;
    if (!recs?.length) return false;
    const first = recs[0] as Record<string, unknown>;
    return typeof first['html'] === 'string';
  });

  htmlPages = computed<{ url: string; html: string; length: number; safeUrl: SafeResourceUrl }[]>(() => {
    if (!this.isHtmlMode()) return [];
    this.revokeBlobUrls();

    return (this.scrapeResult()!.records as Record<string, unknown>[]).map(r => {
      const url = String(r['url'] ?? '');
      const rawHtml = String(r['html'] ?? '');
      const length = Number(r['html_length'] ?? rawHtml.length);

      const htmlWithBase = this.injectBaseHref(rawHtml, url);

      const blob = new Blob([htmlWithBase], { type: 'text/html;charset=utf-8' });
      const blobUrl = URL.createObjectURL(blob);
      this.blobUrls.push(blobUrl);

      return {
        url,
        html: rawHtml,
        length,
        safeUrl: this.sanitizer.bypassSecurityTrustResourceUrl(blobUrl),
      };
    });
  });

  private injectBaseHref(html: string, pageUrl: string): string {
    if (!pageUrl) return html;

    let origin: string;
    try {
      origin = new URL(pageUrl).origin + '/';
    } catch {
      return html;
    }

    const baseTag = `<base href="${origin}">`;

    // Si un <base> existe déjà, on ne touche à rien
    if (/<base\s/i.test(html)) return html;

    // Injecte juste après <head>
    if (/<head[^>]*>/i.test(html)) {
      return html.replace(/<head[^>]*>/i, match => `${match}${baseTag}`);
    }

    // Sinon avant <html> ou tout au début
    if (/<html[^>]*>/i.test(html)) {
      return html.replace(/<html[^>]*>/i, match => `${match}<head>${baseTag}</head>`);
    }

    return `<head>${baseTag}</head>${html}`;
  }

  setHtmlViewMode(mode: 'preview' | 'source'): void {
    this.htmlViewMode.set(mode);
  }

  downloadHtml(page: { url: string; html: string }, index: number): void {
    const blob = new Blob([page.html], {type: 'text/html;charset=utf-8'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    const safeName = page.url.replace(/[^a-z0-9]+/gi, '_').slice(0, 60) || `page-${index + 1}`;
    a.download = `${safeName}.html`;
    a.click();
    URL.revokeObjectURL(url);
  }

  formatBytes(n: number): string {
    if (n < 1024) return `${n} B`;
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
    return `${(n / 1024 / 1024).toFixed(2)} MB`;
  }

  private revokeBlobUrls(): void {
    this.blobUrls.forEach(u => URL.revokeObjectURL(u));
    this.blobUrls = [];
  }

  ngOnDestroy(): void {
    this.revokeBlobUrls();
    document.body.style.overflow = '';
    if (this.modalEl && this.modalEl.parentElement === document.body) {
      this.modalEl.remove();
    }
  }

  private modalEl: HTMLElement | null = null;

  openPreview(page: { url: string; html: string; length: number; safeUrl: SafeResourceUrl }): void {
    this.previewPage.set(page);
    document.body.style.overflow = 'hidden';
    setTimeout(() => {
      const el = document.querySelector('.preview-modal') as HTMLElement | null;
      if (el && el.parentElement !== document.body) {
        this.modalEl = el;
        document.body.appendChild(el);
      }
    });
  }
  preActions = computed<PageAction[]>(() => this.job()?.scraperConfig?.preExtractActions ?? []);
  hasPreActions = computed(() => this.preActions().length > 0);

  actionLabel(type: PageActionType): string {
    return PAGE_ACTION_META[type]?.label ?? type;
  }

  closePreview(): void {
    this.previewPage.set(null);
    document.body.style.overflow = '';
    this.modalEl = null;
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.previewPage()) this.closePreview();
  }
}
