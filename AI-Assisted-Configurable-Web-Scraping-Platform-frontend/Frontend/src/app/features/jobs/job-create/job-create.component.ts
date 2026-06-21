import {
  Component,
  computed,
  ElementRef,
  inject,
  input,
  OnDestroy,
  OnInit,
  output,
  signal,
  ViewChild
} from '@angular/core';
import {Router, ActivatedRoute} from '@angular/router';
import {DatePipe, DecimalPipe, LowerCasePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subject, switchMap} from 'rxjs';
import {debounceTime, distinctUntilChanged, filter, takeUntil} from 'rxjs/operators';
import {Overlay, OverlayRef} from '@angular/cdk/overlay';
import {ComponentPortal} from '@angular/cdk/portal';
import {JobService} from '../../../core/services/job.service';
import {TemplateService} from '../../../core/services/template.service';
import {ToastService} from '../../../core/services/toast.service';
import {SiteProbeService} from '../../../core/services/site-probe.service';
import {ProxyService} from '../../../core/services/proxy.service';
import {ScheduleService} from '../../../core/services/schedule.service';

import {SchemaBuilderComponent} from '../schema-builder/schema-builder.component';
import {ProbeReportComponent} from './probe-report/probe-report.component';
import {WebhookConfigComponent} from '../webhook-config/webhook-config.component';

import {
  CreateJobRequest,
  ExtractionSchema,
  ExtractionMode,
  Job,
  UpdateJobRequest,
  JobType,
  DetectedPagination,
  PaginationType,
  BrowserConfig,
  PageActionType,
  PageAction
} from '../../../core/models/job.model';
import {ApiResponse} from '../../../core/models/api-response.model';
import {
  JobForm,
  createDefaultJobForm,
  SCHEMA_STEP_TITLES,
  PAGINATION_SELECTOR_LABELS,
  auchanPriceTemplate,
  createPageAction,
  PAGE_ACTION_META
} from '../../../core/models/job-form.model';
import {
  SiteProbeResponse,
  SIGNAL_CONFIG
} from '../../../core/models/site-probe.model';
import {JobTemplate} from '../../../core/models/job-templates.model';
import {
  createDefaultProxyConfig,
  ProxyCountryStat,
  ROTATION_STRATEGY_LABELS
} from '../../../core/models/proxy.model';
import {environment} from '../../../../environments/environment';

type StepId = 1 | 2 | 3 | 4 | 5 | 6;
type TabId = 'general' | 'advanced';

@Component({
  selector: 'app-job-create',
  standalone: true,
  imports: [LowerCasePipe, FormsModule, SchemaBuilderComponent, DecimalPipe, DatePipe, WebhookConfigComponent],
  templateUrl: './job-create.component.html',
  styleUrl: './job-create.component.scss',
})
export class JobCreateComponent implements OnInit, OnDestroy {

  readOnlyMode = input(false);
  jobType = input<JobType>('SCRAPER');
  showChangeType = input(false);
  displayJobId = input<string | null>(null);
  formValid = signal(false);
  private readonly overlay = inject(Overlay);
  private reportOverlayRef: OverlayRef | null = null;


  jobCreated = output<Job>();
  changeTypeRequest = output<void>();
  modeChange = output<ExtractionMode>();

  @ViewChild('formScroll') private formScroll?: ElementRef<HTMLElement>;

  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly jobSvc = inject(JobService);
  private readonly templateService = inject(TemplateService);
  private readonly toast = inject(ToastService);
  private readonly probeSvc = inject(SiteProbeService);
  private readonly proxyService = inject(ProxyService);
  private readonly scheduleService = inject(ScheduleService);

  private readonly urlSubject = new Subject<string>();
  private readonly cronSubject = new Subject<string>();
  private readonly destroy$ = new Subject<void>();

  private readonly regionDisplay =
    typeof Intl !== 'undefined' && (Intl as any).DisplayNames
      ? new Intl.DisplayNames(['en'], {type: 'region'})
      : null;

  readonly signalConfig = SIGNAL_CONFIG;
  readonly paginationLabels = PAGINATION_SELECTOR_LABELS;
  readonly rotationStrategyLabels = ROTATION_STRATEGY_LABELS;
  readonly pageActionMeta = PAGE_ACTION_META;
  readonly pageActionTypes: PageActionType[] = [
    'CLICK', 'FILL', 'TYPE', 'SELECT', 'PRESS_KEY',
    'WAIT_FOR_SELECTOR', 'WAIT_MS', 'SCROLL_TO_BOTTOM', 'HOVER',
    'LLM_CLICK', 'EVAL_JS',
  ];

  form: JobForm = createDefaultJobForm();
  newSeedUrl = '';
  newActionType = signal<PageActionType>('CLICK');

  currentStep = signal<StepId>(1);
  activeTab = signal<TabId>('general');
  loading = signal(false);
  error = signal<string | null>(null);
  created = signal<Job | null>(null);
  jobData = signal<Job | null>(null);

  probeResult = signal<SiteProbeResponse | null>(null);
  probeLoading = signal(false);
  appliedTemplateName = signal<string | null>(null);
  projectId = signal<string | null>(null);

  availableCountries = signal<ProxyCountryStat[]>([]);
  cronPreview = signal<string[]>([]);
  cronPreviewLoading = signal(false);
  cronPreviewError = signal(false);
  showReport = signal(false);

  nlpInstruction = signal('');
  nlpLoading = signal(false);
  nlpError = signal<string | null>(null);
  nlpPreview = signal<PageAction[] | null>(null);
  nlpReasoning = signal<string | null>(null);
  nlpGrounded = signal(false);

  isEditMode = computed(() => !!this.route.snapshot.paramMap.get('id'));
  isCrawler = computed(() => this.jobType() === 'CRAWLER');

  editingJobId = computed<string | null>(
    () => this.route.snapshot.paramMap.get('id') ?? this.displayJobId()
  );

  generalSteps = computed<StepId[]>(() =>
    this.isCrawler() ? [1, 2] : [1, 2, 4]
  );

  advancedSteps = computed<StepId[]>(() => [3, 6, 5]);

  visibleSteps = computed<StepId[]>(() =>
    this.isCrawler() ? [1, 2, 3, 6, 5] : [1, 2, 4, 3, 6, 5]
  );

  generalStepLabels = computed(() =>
    this.isCrawler()
      ? ['General', 'Crawler Config']
      : ['General', 'Scraper Config', 'Schema']
  );

  advancedStepLabels = computed(() => ['Browser & Network', 'Proxy', 'Retry']);

  currentTabStepIndex = computed(() => {
    const steps = this.activeTab() === 'general'
      ? this.generalSteps()
      : this.advancedSteps();
    return steps.indexOf(this.currentStep());
  });

  ngOnInit(): void {
    this.form.jobType = this.jobType();

    if (this.isCrawler()) {
      this.form.extractionMode = 'AUTO';
      this.form.extractionSchema = {};
    }

    const routeJobId = this.route.snapshot.paramMap.get('id');
    const templateId = this.route.snapshot.queryParamMap.get('templateId');
    const projectIdParam = this.route.snapshot.queryParamMap.get('projectId');

    if (projectIdParam) this.projectId.set(projectIdParam);

    const jobId = routeJobId || this.displayJobId();

    if (jobId) {
      this.loadJob(jobId);
    } else if (templateId) {
      this.templateService.getById(templateId).subscribe({
        next: tpl => this.applyTemplate(tpl),
      });
    }

    this.proxyService.getAvailableCountries().subscribe({
      next: list => this.availableCountries.set(list ?? []),
      error: () => this.availableCountries.set([]),
    });

    this.urlSubject.pipe(
      debounceTime(700),
      distinctUntilChanged(),
      filter(url => this.isValidHttpUrl(url)),
      takeUntil(this.destroy$)
    ).subscribe(url => {
      this.probeResult.set(null);
      this.probeLoading.set(true);

      this.probeSvc.probe(url).subscribe(res => {
        this.probeLoading.set(false);
        this.probeResult.set(res?.data ?? null);
      });
    });

    this.cronSubject.pipe(
      debounceTime(500),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(cron => {
      if (!cron || !cron.trim()) {
        this.cronPreview.set([]);
        this.cronPreviewError.set(false);
        this.cronPreviewLoading.set(false);
        return;
      }
      this.cronPreviewLoading.set(true);
      this.cronPreviewError.set(false);
      this.scheduleService.preview(cron.trim(), 5).subscribe({
        next: res => {
          this.cronPreview.set(res.data?.next ?? []);
          this.cronPreviewLoading.set(false);
          this.cronPreviewError.set(false);
        },
        error: () => {
          this.cronPreview.set([]);
          this.cronPreviewLoading.set(false);
          this.cronPreviewError.set(true);
        }
      });
    });

    if (this.form.cronExpression?.trim()) {
      this.cronSubject.next(this.form.cronExpression);
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.reportOverlayRef?.dispose();
  }

  setTab(tab: TabId): void {
    if (tab === this.activeTab()) return;

    if (tab === 'advanced') {
      if (!this.validateCurrentStep()) return;
      this.activeTab.set('advanced');
      this.currentStep.set(3);
    } else {
      this.activeTab.set('general');
      const lastGeneralStep = this.generalSteps().at(-1)!;
      this.currentStep.set(lastGeneralStep);
    }

    this.error.set(null);
    this.scrollFormTop();
  }

  goToStep(step: StepId): void {
    const targetIndex = this.visibleSteps().indexOf(step);
    const currentIndex = this.visibleSteps().indexOf(this.currentStep());
    if (targetIndex > currentIndex && !this.validateCurrentStep()) return;

    const isGeneral = (this.generalSteps() as number[]).includes(step);
    this.activeTab.set(isGeneral ? 'general' : 'advanced');

    this.currentStep.set(step);
    this.error.set(null);
    this.scrollFormTop();
  }

  nextStep(): void {
    const steps = this.visibleSteps();
    const idx = steps.indexOf(this.currentStep());
    if (idx >= 0 && idx < steps.length - 1) this.goToStep(steps[idx + 1]);
  }

  prevStep(): void {
    const steps = this.visibleSteps();
    const idx = steps.indexOf(this.currentStep());
    if (idx > 0) this.goToStep(steps[idx - 1]);
  }

  cancel(): void {
    void this.router.navigate(['/jobs']);
  }

  requestChangeType(): void {
    this.changeTypeRequest.emit();
  }

  createAnother(): void {
    this.created.set(null);
    this.probeResult.set(null);
    this.appliedTemplateName.set(null);
    this.currentStep.set(1);
    this.activeTab.set('general');
    this.error.set(null);

    this.form = createDefaultJobForm();
    this.form.jobType = this.jobType();

    if (this.isCrawler()) {
      this.form.extractionMode = 'AUTO';
      this.form.extractionSchema = {};
    }

    this.changeTypeRequest.emit();
  }

  submit(): void {
    this.error.set(null);

    if (!this.validateStep1()) return;
    if (!this.isCrawler() && !this.validateSchema()) return;

    this.loading.set(true);
    const id = this.route.snapshot.paramMap.get('id');

    if (id) {
      this.jobSvc.getById(id).pipe(
        switchMap((res: ApiResponse<Job>) =>
          this.jobSvc.update(id, this.buildUpdatePayload(res.data.status))
        )
      ).subscribe({
        next: res => {
          this.loading.set(false);
          this.toast.success('Job updated successfully.');
          this.jobCreated.emit(res.data);
        },
        error: err => this.handleError(err),
      });
    } else {
      this.jobSvc.create(this.buildCreatePayload()).subscribe({
        next: (res: { data: Job | null }) => {
          if (res.data) {
            this.created.set(res.data);
            this.jobCreated.emit(res.data);
            this.toast.success('Job created successfully.');
          }
          this.loading.set(false);
        },
        error: err => this.handleError(err),
      });
    }
  }

  onUrlChange(url: string): void {
    this.probeResult.set(null);
    this.urlSubject.next(url ?? '');
    this.revalidate();
  }

  onCronChange(value: string): void {
    this.form.cronExpression = value;
    this.cronSubject.next(value);
    this.revalidate();
  }

  onSchemaChange(schema: ExtractionSchema): void {
    this.form.extractionSchema = schema;
    this.revalidate();
  }

  revalidate(): void {
    const v = this.computeValidity();
    console.log('[formValid]', v, '| name:', this.form.name, '| url:', this.form.targetUrl);
    this.formValid.set(v);
  }

  private computeValidity(): boolean {
    if (!this.form.name?.trim()) return false;
    if (!this.form.targetUrl?.trim()) return false;
    try { new URL(this.form.targetUrl); } catch { return false; }
    return !(this.form.triggerType === 'SCHEDULED' && !this.form.cronExpression?.trim());

  }

  onModeChange(mode: ExtractionMode): void {
    if (this.isCrawler()) return;
    this.form.extractionMode = mode;
    this.modeChange.emit(mode);
    this.revalidate();
  }

  schemaStepTitle(): string {
    return SCHEMA_STEP_TITLES[this.form.extractionMode] ?? 'Extraction Schema';
  }

  openReport(): void {
    const pr = this.probeResult();
    if (!pr) return;

    if (this.reportOverlayRef) {
      this.reportOverlayRef.dispose();
      this.reportOverlayRef = null;
    }

    this.showReport.set(true);

    const overlayRef = this.overlay.create({
      hasBackdrop: false,
      positionStrategy: this.overlay.position().global(),
      scrollStrategy: this.overlay.scrollStrategies.block(),
      panelClass: 'pr-overlay-panel',
    });

    const portal = new ComponentPortal(ProbeReportComponent);
    const ref = overlayRef.attach(portal);

    ref.setInput('probe', pr);
    ref.instance.closeReport.subscribe(() => this.closeReport());

    this.reportOverlayRef = overlayRef;
  }

  closeReport(): void {
    this.showReport.set(false);
    if (this.reportOverlayRef) {
      this.reportOverlayRef.dispose();
      this.reportOverlayRef = null;
    }
  }

  probeSummary(pr: SiteProbeResponse): string {
    const count = pr.protections?.length ?? 0;

    if (count === 0) {
      return 'No anti-bot protections detected';
    }

    const first = pr.protections![0];
    const name = this.providerLabelInline(first.provider);

    if (count === 1) {
      return name;
    }

    return `${name} + ${count - 1} more`;
  }

  detectedTypeLabel(t: PaginationType): string {
    switch (t) {
      case 'NUMBERED':
        return 'Numbered pages';
      case 'INFINITE_SCROLL':
        return 'Infinite scroll';
      case 'LOAD_MORE':
        return 'Load-more button';
      case 'NEXT_BUTTON':
        return 'Next button';
      case 'URL_PARAM':
        return 'URL parameter';
      case 'CURSOR':
        return 'Cursor';
      case 'AUTO':
        return 'Auto';
      default:
        return 'None';
    }
  }

  applyDetectedPagination(detected: DetectedPagination): void {
    if (!detected || detected.type === 'NONE') return;
    if (this.isCrawler()) return;

    const cfg = this.form.scraperConfig;
    cfg.paginationType = detected.type;
    if (detected.suggestedSelector) {
      cfg.paginationSelector = detected.suggestedSelector;
    }
    if (!cfg.maxPaginationPages || cfg.maxPaginationPages < 1) {
      cfg.maxPaginationPages = 10;
    }
  }

  applyAutoMode(): void {
    if (this.isCrawler()) return;

    if (this.form.scraperConfig.singlePageMode) {
      this.form.scraperConfig.singlePageMode = false;
    }
    this.form.scraperConfig.paginationType = 'AUTO';
    this.form.scraperConfig.paginationSelector = null;
    if (!this.form.scraperConfig.maxPaginationPages || this.form.scraperConfig.maxPaginationPages < 1) {
      this.form.scraperConfig.maxPaginationPages = 10;
    }
  }

  addSeedUrl(): void {
    const url = this.newSeedUrl.trim();
    if (!url) return;

    try {
      new URL(url);
    } catch {
      this.error.set('Invalid seed URL.');
      return;
    }

    this.form.scraperConfig.seedUrls = [...this.form.scraperConfig.seedUrls, url];
    this.newSeedUrl = '';
    this.error.set(null);
  }

  removeSeedUrl(index: number): void {
    this.form.scraperConfig.seedUrls =
      this.form.scraperConfig.seedUrls.filter((_, i) => i !== index);
  }

  toggleJavascriptEnabled(): void {
    this.form.browserConfig.javascriptEnabled =
      !this.form.browserConfig.javascriptEnabled;
  }

  toggleStealthMode(): void {
    this.form.browserConfig.stealthMode = !this.form.browserConfig.stealthMode;
  }

  toggleCircuitBreaker(): void {
    this.form.retryPolicy.circuitBreakerEnabled =
      !this.form.retryPolicy.circuitBreakerEnabled;
  }

  toggleSinglePageMode(): void {
    this.form.scraperConfig.singlePageMode =
      !this.form.scraperConfig.singlePageMode;

    if (this.form.scraperConfig.singlePageMode) {
      this.form.scraperConfig.paginationType = 'NONE';
      this.form.scraperConfig.paginationSelector = null;
      this.form.scraperConfig.maxPaginationPages = 10;
    }
  }

  toggleScrollToBottom(): void {
    this.form.scraperConfig.scrollToBottom =
      !this.form.scraperConfig.scrollToBottom;
  }

  toggleFollowRedirects(): void {
    this.form.crawlerConfig.followRedirects =
      !this.form.crawlerConfig.followRedirects;
  }

  toggleRespectRobotsTxt(): void {
    this.form.crawlerConfig.respectRobotsTxt =
      !this.form.crawlerConfig.respectRobotsTxt;
  }

  toggleProxyEnabled(): void {
    this.form.proxyConfig.enabled = !this.form.proxyConfig.enabled;
  }

  toggleStrictGeo(): void {
    this.form.proxyConfig.strictGeo = !this.form.proxyConfig.strictGeo;
  }

  toggleSessionEnabled(): void {
    const bc = this.form.browserConfig;
    if (!bc.session) {
      bc.session = createDefaultJobForm().browserConfig.session;
    }
    bc.session!.enabled = !bc.session!.enabled;
    if (bc.session!.enabled && !bc.session!.mode) {
      bc.session!.mode = 'FORM';
    }
  }

  flagEmoji(code: string | null | undefined): string {
    if (!code || code.length !== 2) return '🌐';
    const cps = code.toUpperCase()
      .split('')
      .map(c => 0x1F1E6 + c.charCodeAt(0) - 65);
    return String.fromCodePoint(...cps);
  }

  countryLabel(code: string): string {
    if (!code) return '';
    try {
      return this.regionDisplay?.of(code) ?? code;
    } catch {
      return code;
    }
  }

  isCountrySelected(code: string): boolean {
    return (this.form.proxyConfig.countries ?? []).includes(code);
  }

  addCountry(code: string): void {
    if (!code || this.isCountrySelected(code)) return;
    const current = this.form.proxyConfig.countries ?? [];
    this.form.proxyConfig.countries = [...current, code];
  }

  onCountrySelectChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.addCountry(select.value);
    select.value = '';
  }

  removeCountry(code: string): void {
    const current = this.form.proxyConfig.countries ?? [];
    const next = current.filter(c => c !== code);
    this.form.proxyConfig.countries = next.length === 0 ? null : next;
  }

  addPageAction(): void {
    const type = this.newActionType();
    const action = createPageAction(type);
    this.form.scraperConfig.preExtractActions = [
      ...this.form.scraperConfig.preExtractActions,
      action,
    ];
  }

  removePageAction(index: number): void {
    this.form.scraperConfig.preExtractActions =
      this.form.scraperConfig.preExtractActions.filter((_, i) => i !== index);
  }

  movePageAction(index: number, dir: -1 | 1): void {
    const list = [...this.form.scraperConfig.preExtractActions];
    const target = index + dir;
    if (target < 0 || target >= list.length) return;
    [list[index], list[target]] = [list[target], list[index]];
    this.form.scraperConfig.preExtractActions = list;
  }

  onActionTypeChange(index: number, type: PageActionType): void {
    const list = [...this.form.scraperConfig.preExtractActions];
    const fresh = createPageAction(type);
    list[index] = {...fresh, optional: list[index].optional, label: list[index].label};
    this.form.scraperConfig.preExtractActions = list;
  }

  insertAuchanTemplate(): void {
    this.form.scraperConfig.preExtractActions = [
      ...this.form.scraperConfig.preExtractActions,
      ...auchanPriceTemplate(),
    ];
  }

  clearPageActions(): void {
    this.form.scraperConfig.preExtractActions = [];
  }

  trackByIndex(index: number): number {
    return index;
  }

  generateActionsFromNlp(): void {
    const instruction = this.nlpInstruction().trim();
    const targetUrl = this.form.targetUrl.trim();

    if (!instruction) {
      this.nlpError.set('Describe what you want to do first.');
      return;
    }
    if (!targetUrl) {
      this.nlpError.set('Set the Target URL (step 1) before generating steps.');
      return;
    }

    this.nlpError.set(null);
    this.nlpPreview.set(null);
    this.nlpReasoning.set(null);
    this.nlpLoading.set(true);

    this.jobSvc.planActions({
      targetUrl,
      instruction,
      jobId: this.editingJobId(),
    }).subscribe({
      next: res => {
        this.nlpLoading.set(false);
        const data = res.data;
        if (!data?.success || !data.actions?.length) {
          this.nlpError.set(data?.errorMessage ?? 'No steps could be generated.');
          return;
        }
        const mapped: PageAction[] = data.actions.map(a => ({
          type: a.type,
          selector: a.selector ?? null,
          value: a.value ?? null,
          timeoutMs: a.timeoutMs ?? null,
          optional: a.optional ?? false,
          label: a.label ?? null,
        }));
        this.nlpPreview.set(mapped);
        this.nlpReasoning.set(data.reasoning);
        this.nlpGrounded.set(data.groundedOnHtml);
      },
      error: () => {
        this.nlpLoading.set(false);
        this.nlpError.set('The AI planner is unavailable right now.');
      },
    });
  }

  insertGeneratedActions(): void {
    const generated = this.nlpPreview();
    if (!generated?.length) return;

    this.form.scraperConfig.preExtractActions = [
      ...this.form.scraperConfig.preExtractActions,
      ...generated,
    ];
    this.discardGeneratedActions();
  }

  replaceWithGeneratedActions(): void {
    const generated = this.nlpPreview();
    if (!generated?.length) return;

    this.form.scraperConfig.preExtractActions = [...generated];
    this.discardGeneratedActions();
  }

  discardGeneratedActions(): void {
    this.nlpPreview.set(null);
    this.nlpReasoning.set(null);
    this.nlpInstruction.set('');
    this.nlpError.set(null);
  }

  webhookUrlFor(token: string | null | undefined): string {
    if (!token) return '';
    const base = environment.apiUrl.replace(/\/$/, '');
    return `${base}/v1/webhooks/jobs/${token}`;
  }

  private loadJob(id: string): void {
    this.jobSvc.getById(id).subscribe({
      next: (res: ApiResponse<Job>) => {
        const job = res.data;

        this.jobData.set(job);
        this.projectId.set(job.projectId ?? null);

        const isCrawler = job.jobType === 'CRAWLER';

        this.form = {
          name: job.name,
          targetUrl: job.targetUrl,
          jobType: job.jobType,
          priority: job.priority,
          triggerType: job.triggerType,
          extractionMode: isCrawler ? 'AUTO' : job.extractionMode,
          cronExpression: job.cronExpression ?? '',
          browserConfig: {...job.browserConfig},
          retryPolicy: {...job.retryPolicy},
          extractionSchema: isCrawler ? {} : {...(job.extractionSchema ?? {})},
          scraperConfig: job.scraperConfig
            ? {...job.scraperConfig, preExtractActions: job.scraperConfig.preExtractActions ?? []}
            : createDefaultJobForm().scraperConfig,
          crawlerConfig: job.crawlerConfig ?? createDefaultJobForm().crawlerConfig,
          proxyConfig: job.proxyConfig ?? createDefaultProxyConfig(),
        };

        if (this.readOnlyMode()) this.jobCreated.emit(job);
      },
      error: () => this.error.set('Failed to load job.'),
    });
  }

  private applyTemplate(tpl: JobTemplate): void {
    const cfg = tpl.config;

    if (cfg.priority) this.form.priority = cfg.priority;
    if (cfg.triggerType) this.form.triggerType = cfg.triggerType;
    if (cfg.extractionMode && !this.isCrawler()) this.form.extractionMode = cfg.extractionMode;

    if (cfg.browserConfig) this.form.browserConfig = {...this.form.browserConfig, ...cfg.browserConfig};
    if (cfg.retryPolicy) this.form.retryPolicy = {...this.form.retryPolicy, ...cfg.retryPolicy};
    if (cfg.scraperConfig) this.form.scraperConfig = {...this.form.scraperConfig, ...cfg.scraperConfig};
    if (cfg.crawlerConfig) this.form.crawlerConfig = {...this.form.crawlerConfig, ...cfg.crawlerConfig};
    if (cfg.proxyConfig) this.form.proxyConfig = {...this.form.proxyConfig, ...cfg.proxyConfig};

    if (cfg.extractionSchema && !this.isCrawler()) {
      this.form.extractionSchema = {...cfg.extractionSchema};
    }

    if (!this.form.name) this.form.name = tpl.name;

    this.appliedTemplateName.set(tpl.name);
  }

  private validateCurrentStep(): boolean {
    if (this.currentStep() === 1) return this.validateStep1();
    if (this.currentStep() === 4 && !this.isCrawler()) return this.validateSchema();
    return true;
  }

  private validateStep1(): boolean {
    if (!this.form.name.trim()) return this.setError('Job name is required.', 'name');
    if (!this.form.targetUrl.trim()) return this.setError('Target URL is required.', 'targetUrl');

    try {
      new URL(this.form.targetUrl);
    } catch {
      return this.setError('Target URL must be valid (https://).', 'targetUrl');
    }

    if (this.form.triggerType === 'SCHEDULED' && !this.form.cronExpression.trim()) {
      return this.setError('Cron expression is required.', 'cron');
    }

    return true;
  }

  private validateSchema(): boolean {
    const mode = this.form.extractionMode;

    if (mode === 'LLM' && !this.form.extractionSchema.llmPrompt?.trim()) {
      return this.setError('LLM prompt is required.', 'schemaBuilderAnchor');
    }

    if (mode === 'REGEX' && !this.form.extractionSchema.regexPattern?.trim()) {
      return this.setError('Regex pattern is required.', 'schemaBuilderAnchor');
    }

    if (['CSS_SELECTOR', 'XPATH', 'VISUAL'].includes(mode)) {
      const fields = (this.form.extractionSchema.fields ?? [])
        .filter(f => f.name?.trim() && f.selector?.trim());

      if (!fields.length) return this.setError('At least one field is required.', 'schemaBuilderAnchor');
    }

    return true;
  }

  private buildSchema(): ExtractionSchema | null {
    if (this.isCrawler()) return null;
    if (this.form.extractionMode === 'AUTO') return null;

    const schema = {...this.form.extractionSchema};
    if (schema.fields) {
      schema.fields = schema.fields.filter(f => f.name?.trim() && f.selector?.trim());
    }
    return schema;
  }

  private buildCreatePayload(): CreateJobRequest {
    const isScraper = this.jobType() === 'SCRAPER';

    return {
      name: this.form.name.trim(),
      targetUrl: this.form.targetUrl.trim(),
      jobType: this.jobType(),
      priority: this.form.priority,
      triggerType: this.form.triggerType,
      extractionMode: isScraper ? this.form.extractionMode : 'AUTO',
      cronExpression: this.form.cronExpression.trim() || null,
      browserConfig: this.buildBrowserConfigPayload(),
      retryPolicy: {...this.form.retryPolicy},
      extractionSchema: this.buildSchema(),
      scraperConfig: isScraper ? {...this.form.scraperConfig} : null,
      crawlerConfig: !isScraper ? {...this.form.crawlerConfig} : null,
      proxyConfig: {...this.form.proxyConfig},
      projectId: this.projectId(),
    };
  }

  private buildBrowserConfigPayload(): BrowserConfig {
    const bc = {...this.form.browserConfig};
    if (bc.session) {
      const s = {...bc.session};
      if (this.isEditMode()) {
        if (!s.username) s.username = null;
        if (!s.password) s.password = null;
        if (!s.importedStorageState) s.importedStorageState = null;
      }
      bc.session = s;
    }
    return bc;
  }

  private buildUpdatePayload(status: Job['status']): UpdateJobRequest {
    return {...this.buildCreatePayload(), status};
  }

  private setError(msg: string, fieldId?: string): false {
    this.error.set(msg);
    if (fieldId) {
      this.scrollToField(fieldId);
    }
    return false;
  }

  private handleError(err: unknown): void {
    this.loading.set(false);
    const apiErr = err as { error?: { message?: string } };
    this.error.set(apiErr?.error?.message ?? 'An error occurred.');
    this.scrollFormTop();
  }

  private scrollFormTop(): void {
    this.formScroll?.nativeElement.scrollTo({top: 0, behavior: 'smooth'});
  }

  private scrollToField(fieldId: string): void {
    setTimeout(() => {
      const host = this.formScroll?.nativeElement;
      const el = host?.querySelector<HTMLElement>(`#${fieldId}`);
      if (!el) return;
      el.scrollIntoView({behavior: 'smooth', block: 'center'});
      el.focus({preventScroll: true});
    });
  }

  private providerLabelInline(provider: string): string {
    switch (provider) {
      case 'CLOUDFLARE':
        return 'Cloudflare';
      case 'DATADOME':
        return 'DataDome';
      case 'AKAMAI':
        return 'Akamai';
      case 'PERIMETER_X':
        return 'PerimeterX';
      case 'HCAPTCHA':
        return 'hCaptcha';
      case 'RECAPTCHA':
        return 'reCAPTCHA';
      default:
        return provider;
    }
  }

  private isValidHttpUrl(url: string): boolean {
    try {
      const u = new URL(url);
      return u.protocol === 'http:' || u.protocol === 'https:';
    } catch {
      return false;
    }
  }

}
