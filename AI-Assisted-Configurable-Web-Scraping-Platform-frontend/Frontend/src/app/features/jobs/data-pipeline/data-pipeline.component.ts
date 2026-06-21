import {
  Component, signal, computed, inject, OnInit, OnDestroy,
  ViewChild, TemplateRef, EmbeddedViewRef, ViewContainerRef,
} from '@angular/core';
import {CommonModule, DOCUMENT} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {
  CdkDragDrop, CdkDropList, CdkDrag, moveItemInArray,
} from '@angular/cdk/drag-drop';

import {ScrapeResultResponse} from '../../../core/models/scrape-result.model';
import {
  EtlStepDef, EtlStepConfig, EtlResponse,
  ExportFormat, ExportDestination, ETL_STEP_CATALOGUE, QualityReport,
} from '../../../core/models/etl.model';
import {EtlService} from '../../../core/services/etl.service';
import {ScrapeResultService} from '../../../core/services/scrape-result.service';
import {ToastService} from '../../../core/services/toast.service';
import {QualityReportPanelComponent} from './quality-report-panel/quality-report-panel.component';

type PipelineState = 'idle' | 'applying' | 'applied' | 'saving' | 'exporting';
type PreviewTab = 'table' | 'json';

@Component({
  selector: 'app-data-pipeline',
  standalone: true,
  imports: [CommonModule, FormsModule, CdkDropList, CdkDrag, QualityReportPanelComponent],
  templateUrl: './data-pipeline.component.html',
  styleUrl: './data-pipeline.component.scss',
})
export class DataPipelineComponent implements OnInit, OnDestroy {

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly etlSvc = inject(EtlService);
  private readonly scrapeResultSvc = inject(ScrapeResultService);
  private readonly toast = inject(ToastService);
  private readonly vcr = inject(ViewContainerRef);
  private readonly doc = inject(DOCUMENT);

  @ViewChild('exportModalTpl', {read: TemplateRef})
  private exportModalTpl!: TemplateRef<unknown>;
  private modalViewRef: EmbeddedViewRef<unknown> | null = null;

  @ViewChild('previewModalTpl', {read: TemplateRef})
  private previewModalTpl!: TemplateRef<unknown>;
  private previewModalViewRef: EmbeddedViewRef<unknown> | null = null;

  readonly catalogue = ETL_STEP_CATALOGUE;
  readonly categories = ['Clean', 'Normalize', 'Transform', 'AI'] as const;
  readonly formats: ExportFormat[] = ['CSV', 'JSON', 'PARQUET', 'EXCEL', 'MARKDOWN', 'JSON_LD', 'LLMS_TXT'];

  readonly formatLabels: Record<ExportFormat, string> = {
    CSV: 'CSV', JSON: 'JSON', PARQUET: 'Parquet', EXCEL: 'Excel',
    MARKDOWN: 'Markdown', JSON_LD: 'JSON-LD', LLMS_TXT: 'llms.txt',
  };
  readonly formatHints: Record<ExportFormat, string> = {
    CSV: 'Spreadsheet-friendly', JSON: 'API integrations', PARQUET: 'Big data / analytics',
    EXCEL: 'Microsoft Excel', MARKDOWN: 'RAG / LLM context',
    JSON_LD: 'Knowledge graphs', LLMS_TXT: 'LLM-friendly summary',
  };
  readonly destinations: ExportDestination[] = ['FILE', 'POSTGRESQL', 'WEBHOOK'];

  isLlmReady(fmt: ExportFormat): boolean {
    return fmt === 'MARKDOWN' || fmt === 'JSON_LD' || fmt === 'LLMS_TXT';
  }

  result = signal<ScrapeResultResponse | null>(null);
  resultLoading = signal(true);
  pipeline = signal<EtlStepDef[]>([]);
  state = signal<PipelineState>('idle');
  fullResult = signal<EtlResponse | null>(null);

  showPreviewModal = signal(false);
  selectedStage = signal<number>(-1);
  previewTab = signal<PreviewTab>('table');
  private stageRecords = signal<Map<number, Record<string, unknown>[]>>(new Map());
  stageLoading = signal(false);

  showExportModal = signal(false);
  exportFormat = signal<ExportFormat>('CSV');
  exportDestination = signal<ExportDestination>('FILE');
  exportProgress = signal(0);
  destConfig = signal<Record<string, unknown>>({});

  qualityReport = signal<QualityReport | null>(null);
  showQualityModal = signal(false);
  validating = signal(false);

  catalogueByCategory = computed(() =>
    this.categories.map(cat => ({cat, steps: this.catalogue.filter(s => s.category === cat)})));

  rawRecords = computed<Record<string, unknown>[]>(() => this.result()?.records ?? []);
  rawCount = computed(() => this.rawRecords().length);
  rawColumns = computed(() => {
    const r = this.rawRecords();
    return r.length ? Object.keys(r[0]) : [];
  });

  hasResult = computed(() => this.fullResult() !== null);
  canApply = computed(() => this.pipeline().length > 0 && this.state() === 'idle');
  drawerRecords = computed<Record<string, unknown>[] | null>(() => {
    const stage = this.selectedStage();
    if (stage < 0) return this.rawRecords();
    return this.stageRecords().get(stage) ?? null;
  });

  drawerColumns = computed<string[]>(() => {
    const recs = this.drawerRecords();
    if (!recs || !recs.length) return [];
    const keys = new Set<string>();
    recs.forEach(r => Object.keys(r).forEach(k => keys.add(k)));
    return [...keys];
  });

  drawerJson = computed(() => JSON.stringify(this.drawerRecords() ?? [], null, 2));

  stageLabel = computed(() => {
    const s = this.selectedStage();
    if (s < 0) return 'Raw input';
    const step = this.pipeline()[s];
    return step ? `After · ${step.label}` : `Stage ${s + 1}`;
  });

  prevStageRecords = computed<Record<string, unknown>[] | null>(() => {
    const stage = this.selectedStage();
    if (stage < 0) return null;
    if (stage === 0) return this.rawRecords();
    return this.stageRecords().get(stage - 1) ?? null;
  });

  private valuesEqual(a: unknown, b: unknown): boolean {
    const norm = (v: unknown) =>
      v === null || v === undefined ? '' :
        typeof v === 'object' ? JSON.stringify(v) : String(v);
    return norm(a) === norm(b);
  }

  changedCells = computed<Set<string>>(() => {
    const set = new Set<string>();
    const prev = this.prevStageRecords();
    const curr = this.drawerRecords();
    if (!prev || !curr) return set;
    const cols = this.drawerColumns();
    curr.forEach((row, i) => {
      const prevRow = prev[i];
      if (!prevRow) return;                // ligne sans équivalent → pas de diff
      cols.forEach(col => {
        if (!this.valuesEqual(row[col], prevRow[col])) set.add(`${i}:${col}`);
      });
    });
    return set;
  });

  changedCellCount = computed(() => this.changedCells().size);
  ngOnInit(): void {
    const jobId = this.route.snapshot.paramMap.get('id');
    if (!jobId) {
      void this.router.navigate(['/jobs']);
      return;
    }

    this.scrapeResultSvc.getLatestByJobId(jobId).subscribe({
      next: res => {
        this.result.set(res.data);
        this.resultLoading.set(false);
      },
      error: () => {
        this.resultLoading.set(false);
        this.toast.error('Could not load scrape results.');
        void this.router.navigate(['/jobs', jobId]);
      },
    });
  }

  ngOnDestroy(): void {
    this.destroyModal();
    this.destroyPreviewModal();
  }

  goBack(): void {
    void this.router.navigate(['/jobs', this.route.snapshot.paramMap.get('id')]);
  }

  openHistory(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) void this.router.navigate(['/jobs', id, 'data-pipeline', 'history']);
  }

  dropIntoPipeline(event: CdkDragDrop<EtlStepDef[]>): void {
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
      this.pipeline.set([...event.container.data]);
      this.resetRuns();
      return;
    }
    const def = event.previousContainer.data[event.previousIndex];
    if (!this.isInPipeline(def.type)) {
      const updated = [...this.pipeline()];
      updated.splice(event.currentIndex, 0, def);
      this.pipeline.set(updated);
      this.resetRuns();
    }
  }

  addStep(def: EtlStepDef): void {
    if (this.isInPipeline(def.type)) {
      this.removeStep(def.type);
      return;
    }
    this.pipeline.update(p => [...p, def]);
    this.resetRuns();
  }

  removeStep(type: string): void {
    this.pipeline.update(p => p.filter(s => s.type !== type));
    this.resetRuns();
  }

  clearPipeline(): void {
    this.pipeline.set([]);
    this.resetRuns();
  }

  isInPipeline(type: string): boolean {
    return this.pipeline().some(s => s.type === type);
  }

  private resetRuns(): void {
    this.fullResult.set(null);
    this.stageRecords.set(new Map());
    this.selectedStage.set(-1);
    if (this.state() === 'applied') this.state.set('idle');
  }

  stepReport(i: number) {
    return this.fullResult()?.stepReports?.[i] ?? null;
  }

  nodeStatus(i: number): 'idle' | 'done' | 'error' {
    const rep = this.stepReport(i);
    if (!rep) return 'idle';
    return rep.success ? 'done' : 'error';
  }

  nodeIn(i: number): number | null {
    if (i === 0) return this.rawCount();
    return this.stepReport(i - 1)?.outputCount ?? null;
  }


  paramsPreview(step: EtlStepDef): string[] {
    const params = (step.defaultParams ?? {}) as Record<string, unknown>;

    switch (step.type) {
      case 'STRIP_HTML':
        return ['1  <p>Hello</p>', '2  →', '3  Hello'];
      case 'TRIM_WHITESPACE':
        return ['1  "  text  "', '2  →', '3  "text"'];
      case 'NORMALIZE_DATE':
        return ['1  "05/14/2026"', '2  →', '3  "2026-05-14"'];
      case 'NORMALIZE_PRICE':
        return ['1  "€1.299,50"', '2  →', '3  "1299.50"'];
      case 'CAPITALIZE': {
        const mode = String(params['mode'] ?? 'title');
        return ['1  "hello world"', '2  →', `3  "${this.applyCase('hello world', mode)}"`];
      }
      case 'DEDUPLICATE': {
        const keys = Array.isArray(params['keys']) ? (params['keys'] as string[]) : [];
        return [
          keys.length ? `keys: ${keys.join(', ')}` : 'keys: full row',
          'detecting duplicates...',
          '→ removed',
        ];
      }
      case 'MISSING_VALUE_HANDLER': {
        const fb = String(params['fallback'] ?? 'N/A');
        const strat = String(params['strategy'] ?? 'fill');
        return [`strategy: ${strat}`, `fallback: "${fb}"`, '→ applied'];
      }
      case 'NORMALIZE_ENUM':
        return ['1  "Active User"', '2  →', '3  "active_user"'];
      case 'VALIDATE_FIELDS':
        return ['fields: required', 'check types...', '→ report'];
      default: {
        const entries = Object.entries(params).slice(0, 3);
        if (!entries.length) return ['No params', '—', '→ applied'];
        return entries.map(([k, v]) => `${k}: ${JSON.stringify(v)}`);
      }
    }
  }

  private applyCase(s: string, mode: string): string {
    if (mode === 'upper') return s.toUpperCase();
    if (mode === 'lower') return s.toLowerCase();
    return s.split(' ').map(w => w[0].toUpperCase() + w.slice(1)).join(' ');
  }

  apply(): void {
    const r = this.result();
    if (!this.canApply() || !r) return;
    this.state.set('applying');
    this.etlSvc.applyEtl(r.id, {steps: this.toSteps()}).subscribe({
      next: res => {
        this.fullResult.set(res);
        this.state.set('applied');
        const last = this.pipeline().length - 1;
        this.stageRecords.update(m => new Map(m).set(last, res.records));
        this.selectedStage.set(last);
        this.toast.success(`Pipeline applied  ${res.cleanCount} kept, ${res.removedCount} removed`);
      },
      error: () => {
        this.state.set('idle');
        this.toast.error('ETL pipeline failed.');
      },
    });
  }


  selectStage(i: number): void {
    this.selectedStage.set(i);
    if (i < 0) return;
    this.ensureStageLoaded(i, true);
    if (i > 0) this.ensureStageLoaded(i - 1, false);
  }

  private ensureStageLoaded(i: number, showLoader: boolean): void {
    if (i < 0 || this.stageRecords().has(i)) return;
    const r = this.result();
    if (!r) return;
    if (showLoader) this.stageLoading.set(true);
    this.etlSvc.applyEtl(r.id, {steps: this.toSteps(i + 1)}).subscribe({
      next: res => {
        this.stageRecords.update(m => new Map(m).set(i, res.records));
        if (showLoader) this.stageLoading.set(false);
      },
      error: () => {
        if (showLoader) this.stageLoading.set(false);
        this.toast.error('Could not load step preview.');
      },
    });
  }

  private toSteps(count?: number): EtlStepConfig[] {
    const list = count != null ? this.pipeline().slice(0, count) : this.pipeline();
    return list.map(s => ({type: s.type, params: s.defaultParams} as EtlStepConfig));
  }

  validate(): void {
    const r = this.result();
    if (!r || this.validating()) return;
    const steps: EtlStepConfig[] = [...this.toSteps(), {type: 'VALIDATE_FIELDS', params: {}}];
    this.validating.set(true);
    this.etlSvc.applyEtl(r.id, {steps}).subscribe({
      next: res => {
        this.validating.set(false);
        if (res.qualityReport) {
          this.qualityReport.set(res.qualityReport);
          this.showQualityModal.set(true);
        } else {
          this.toast.error('Server returned no quality report.');
        }
      },
      error: () => {
        this.validating.set(false);
        this.toast.error('Validation failed.');
      },
    });
  }

  saveChanges(): void {
    const r = this.result();
    if (!this.hasResult() || !r) return;
    this.state.set('saving');
    this.etlSvc.applyAndSave(r.id, {steps: this.toSteps()}).subscribe({
      next: () => {
        this.state.set('applied');
        this.toast.success('Cleaned records saved.');
      },
      error: () => {
        this.state.set('applied');
        this.toast.error('Failed to save.');
      },
    });
  }

  openPreviewModal(stage?: number): void {
    if (stage !== undefined) {
      this.selectStage(stage);
    } else if (this.selectedStage() < -1) {
      // sanity: default to raw
      this.selectStage(-1);
    }

    if (this.previewModalViewRef) return;
    this.showPreviewModal.set(true);
    setTimeout(() => {
      this.previewModalViewRef = this.vcr.createEmbeddedView(this.previewModalTpl);
      this.previewModalViewRef.detectChanges();
      this.previewModalViewRef.rootNodes.forEach(node => this.doc.body.appendChild(node));
    });
  }

  closePreviewModal(): void {
    this.showPreviewModal.set(false);
    this.destroyPreviewModal();
  }

  private destroyPreviewModal(): void {
    if (!this.previewModalViewRef) return;
    this.previewModalViewRef.rootNodes.forEach(node => node.parentNode?.removeChild(node));
    this.previewModalViewRef.destroy();
    this.previewModalViewRef = null;
  }

  openExportModal(): void {
    if (this.modalViewRef) return;
    this.showExportModal.set(true);
    setTimeout(() => {
      this.modalViewRef = this.vcr.createEmbeddedView(this.exportModalTpl);
      this.modalViewRef.detectChanges();
      this.modalViewRef.rootNodes.forEach(node => this.doc.body.appendChild(node));
    });
  }

  closeExportModal(): void {
    this.showExportModal.set(false);
    this.exportProgress.set(0);
    this.destConfig.set({});
    this.destroyModal();
  }

  patchDestConfig(key: string, value: unknown): void {
    this.destConfig.update(cfg => ({...cfg, [key]: value}));
  }

  private destroyModal(): void {
    if (!this.modalViewRef) return;
    this.modalViewRef.rootNodes.forEach(node => node.parentNode?.removeChild(node));
    this.modalViewRef.destroy();
    this.modalViewRef = null;
  }

  export(): void {
    const r = this.result();
    if (!r) return;
    const dest = this.exportDestination();
    const cfg = this.destConfig();

    if (dest === 'POSTGRESQL') {
      if (!cfg['jdbcUrl'] || String(cfg['jdbcUrl']).trim() === '') {
        this.toast.error('PostgreSQL export requires a JDBC URL.');
        return;
      }
      if (!cfg['username'] || String(cfg['username']).trim() === '') {
        this.toast.error('PostgreSQL export requires a username.');
        return;
      }
      if (cfg['password'] === undefined || cfg['password'] === '') {
        this.toast.error('PostgreSQL export requires a password.');
        return;
      }
    }
    if (dest === 'WEBHOOK') {
      if (!cfg['url'] || String(cfg['url']).trim() === '') {
        this.toast.error('Webhook export requires an endpoint URL.');
        return;
      }
    }

    this.state.set('exporting');
    this.exportProgress.set(0);
    const interval = setInterval(() => {
      this.exportProgress.update(p => Math.min(p + 15, 90));
    }, 200);

    this.etlSvc.exportRecords(r.id, {
      format: this.exportFormat(),
      destination: dest,
      destinationConfig: Object.keys(cfg).length > 0 ? cfg : undefined,
    }).subscribe({
      next: res => {
        clearInterval(interval);
        this.exportProgress.set(100);
        this.state.set('applied');
        this.toast.success(res.message);
        if (res.downloadUrl) {
          const a = this.doc.createElement('a');
          a.href = res.downloadUrl;
          a.download = `export.${this.exportFormat().toLowerCase()}`;
          a.click();
        }
        setTimeout(() => this.closeExportModal(), 1200);
      },
      error: err => {
        clearInterval(interval);
        this.exportProgress.set(0);
        this.state.set('applied');
        const msg = err?.error?.message ?? err?.error?.error ?? 'Export failed.';
        this.toast.error(msg);
      },
    });
  }

  trackByType(_: number, s: EtlStepDef): string {
    return s.type;
  }

  trackByCol(_: number, c: string): string {
    return c;
  }

  trackByIdx(i: number): number {
    return i;
  }
}
