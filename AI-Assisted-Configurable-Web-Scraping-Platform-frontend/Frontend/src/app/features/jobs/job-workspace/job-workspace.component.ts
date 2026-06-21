import {
  Component, signal, inject, OnInit, OnDestroy,
  ElementRef, ViewChild, AfterViewInit, NgZone, HostListener,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router, ActivatedRoute} from '@angular/router';

import {JobCreateComponent} from '../job-create/job-create.component';
import {JobRunnerComponent} from '../job-runner/job-runner.component';
import {JobHistoryComponent} from '../job-history/job-history.component';
import {ExtractionMode, Job, JobType} from '../../../core/models/job.model';
import {JobExecution} from '../../../core/models/job-execution.model';
import {JobService} from '../../../core/services/job.service';
import {toJobExecution} from '../../../core/models/scrape-result.model';
import {
  SPLIT_KEY, DEFAULT_SPLIT, MIN_PCT, MAX_PCT,
} from '../../../core/constants/workspace.constants';

type WorkspacePhase = 'creation' | 'saved';

const SAVED_SPLIT1_KEY = 'ws_saved_split1';
const SAVED_SPLIT2_KEY = 'ws_saved_split2';
const DEFAULT_S1 = 33.33;
const DEFAULT_S2 = 66.67;
const MIN_PANEL_PCT = 15;

@Component({
  selector: 'app-job-workspace',
  standalone: true,
  imports: [CommonModule, JobCreateComponent, JobRunnerComponent, JobHistoryComponent],
  templateUrl: './job-workspace.component.html',
  styleUrl: './job-workspace.component.scss',
})
export class JobWorkspaceComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('workspaceEl') workspaceRef!: ElementRef<HTMLDivElement>;
  @ViewChild('jobCreateForm') jobCreateRef?: JobCreateComponent;

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private zone = inject(NgZone);
  private jobService = inject(JobService);
  workspacePhase = signal<WorkspacePhase>('creation');
  autoLaunchRunner = signal(false);
  private pendingAutoRun = false;
  showTypePicker = signal(true);
  selectedType = signal<JobType>('SCRAPER');
  hoveredType = signal<JobType | null>(null);
  pickerLeaving = signal(false);

  activeJobId = signal<string | null>(null);
  activeMode = signal<ExtractionMode>('AUTO');
  isNarrow = signal(window.innerWidth < 900);
  executions = signal<JobExecution[]>([]);
  historyLoading = signal(false);
  historyError = signal(false);
  splitPct = signal(DEFAULT_SPLIT);
  isDragging = signal(false);
  split1Pct = signal(DEFAULT_S1);
  split2Pct = signal(DEFAULT_S2);
  isDraggingSaved1 = signal(false);
  isDraggingSaved2 = signal(false);
  resumePolling = signal(false);
  createdJob = signal<Job | null>(null);
  phaseTransitioning = signal(false);

  get configPanelStyle(): Record<string, string> {
    if (this.isNarrow()) return {};
    return {flex: `0 0 ${this.split1Pct()}%`, maxWidth: `${this.split1Pct()}%`};
  }

  get runnerPanelStyle(): Record<string, string> {
    if (this.isNarrow()) return {};
    const w = this.split2Pct() - this.split1Pct();
    return {flex: `0 0 ${w}%`, maxWidth: `${w}%`};
  }

  get histPanelStyle(): Record<string, string> {
    if (this.isNarrow()) return {};
    const w = 100 - this.split2Pct();
    return {flex: `0 0 ${w}%`, maxWidth: `${w}%`};
  }

  get s1Pct(): number {
    return Math.round(this.split1Pct());
  }

  get s2Pct(): number {
    return Math.round(this.split2Pct() - this.split1Pct());
  }

  get s3Pct(): number {
    return Math.round(100 - this.split2Pct());
  }

  ngOnInit(): void {
    const jobId = this.route.snapshot.paramMap.get('id');
    const templateId = this.route.snapshot.queryParamMap.get('templateId');
    const jobType = this.route.snapshot.queryParamMap.get('jobType') as JobType | null;
    const savedCreation = parseFloat(localStorage.getItem(SPLIT_KEY) ?? '');
    if (!isNaN(savedCreation)) this.splitPct.set(this.clampCreation(savedCreation));
    const s1 = parseFloat(localStorage.getItem(SAVED_SPLIT1_KEY) ?? '');
    const s2 = parseFloat(localStorage.getItem(SAVED_SPLIT2_KEY) ?? '');
    if (!isNaN(s1) && !isNaN(s2) && s2 > s1) {
      this.split1Pct.set(s1);
      this.split2Pct.set(s2);
    }

    if (jobId) {
      this.activeJobId.set(jobId);
      this.showTypePicker.set(false);
      this.loadJobMode(jobId);

      const runMode = this.route.snapshot.data?.['runMode'] === true;

      if (runMode) {
        this.loadHistory(jobId);
        this.workspacePhase.set('saved');

        this.jobService.getById(jobId).subscribe({
          next: res => {
            const status = res.data?.status;
            this.activeMode.set(res.data?.extractionMode ?? 'AUTO');
            this.selectedType.set(res.data?.jobType ?? 'SCRAPER');

            if (['RUNNING', 'QUEUED'].includes(status ?? '')) {
              this.resumePolling.set(true);
            } else {
              this.autoLaunchRunner.set(true);
              setTimeout(() => this.autoLaunchRunner.set(false), 500);
            }
          }
        });
        return;
      }
      return;
    }

    if (templateId && jobType) {
      this.selectedType.set(jobType);
      this.showTypePicker.set(false);
    }
  }


  ngAfterViewInit(): void {
    this.mmFn = (e: MouseEvent) => this.moveCreation(e.clientX);
    this.muFn = () => this.endCreationDrag();
    this.tmFn = (e: TouchEvent) => {
      e.preventDefault();
      this.moveCreation(e.touches[0].clientX);
    };
    this.teFn = () => this.endCreationDrag();
  }

  ngOnDestroy(): void {
    this.endCreationDrag();
    this.endS1Drag();
    this.endS2Drag();
  }

  selectJobType(type: JobType): void {
    this.selectedType.set(type);
    this.pickerLeaving.set(true);
    setTimeout(() => {
      this.showTypePicker.set(false);
      this.pickerLeaving.set(false);
    }, 450);
  }

  reopenTypePicker(): void {
    this.showTypePicker.set(true);
  }

  dismissPicker(): void {
    this.pickerLeaving.set(true);
    setTimeout(() => {
      this.showTypePicker.set(false);
      this.pickerLeaving.set(false);
    }, 450);
  }

  handleSave(): void {
    this.pendingAutoRun = false;
    this.jobCreateRef?.submit();
  }

  handleSaveAndRun(): void {
    this.pendingAutoRun = true;
    this.jobCreateRef?.submit();
  }

  onJobCreated(job: Job): void {
    this.activeJobId.set(job.id);
    this.activeMode.set(job.extractionMode ?? 'AUTO');
    this.selectedType.set(job.jobType ?? this.selectedType());
    this.createdJob.set(job);
    this.loadHistory(job.id);

    if (this.pendingAutoRun) {
      this.pendingAutoRun = false;
      this.autoLaunchRunner.set(true);
      setTimeout(() => this.autoLaunchRunner.set(false), 500);
    }

    if (job.triggerType === 'WEBHOOK') {
      return;
    }

    this.phaseTransitioning.set(true);
    setTimeout(() => {
      this.workspacePhase.set('saved');
      this.phaseTransitioning.set(false);
    }, 280);
  }

  onModeChange(mode: ExtractionMode): void {
    this.activeMode.set(mode);
  }

  onJobLaunched(): void {
    const id = this.activeJobId();
    if (id) this.loadHistory(id);
  }

  goBack(): void {
    this.router.navigate(['/jobs']);
  }

  private loadHistory(jobId: string): void {
    this.historyLoading.set(true);
    this.historyError.set(false);
    this.jobService.getHistory(jobId).subscribe({
      next: res => {
        const raw = res.data ?? [];
        const total = raw.length;
        this.executions.set(raw.map((r, i) => toJobExecution(r, i, total)));
        this.historyLoading.set(false);
      },
      error: () => {
        this.historyError.set(true);
        this.historyLoading.set(false);
      },
    });
  }

  private loadJobMode(jobId: string): void {
    this.jobService.getById(jobId).subscribe({
      next: res => {
        this.activeMode.set(res.data?.extractionMode ?? 'AUTO');
        this.selectedType.set(res.data?.jobType ?? 'SCRAPER');
      },
      error: () => {
      },
    });
  }

  private draggingCreation = false;
  private cStartX = 0;
  private cStartPct = 0;
  private cWorkspaceW = 0;
  private mmFn!: (e: MouseEvent) => void;
  private muFn!: (e: MouseEvent) => void;
  private tmFn!: (e: TouchEvent) => void;
  private teFn!: (e: TouchEvent) => void;

  onMouseDown(e: MouseEvent): void {
    e.preventDefault();
    this.startCreationDrag(e.clientX);
    document.addEventListener('mousemove', this.mmFn);
    document.addEventListener('mouseup', this.muFn);
  }

  onTouchStart(e: TouchEvent): void {
    this.startCreationDrag(e.touches[0].clientX);
    document.addEventListener('touchmove', this.tmFn, {passive: false});
    document.addEventListener('touchend', this.teFn);
  }

  private startCreationDrag(clientX: number): void {
    this.draggingCreation = true;
    this.cStartX = clientX;
    this.cStartPct = this.splitPct();
    this.cWorkspaceW = this.workspaceRef.nativeElement.offsetWidth;
    this.isDragging.set(true);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }

  private moveCreation(clientX: number): void {
    if (!this.draggingCreation) return;
    this.zone.run(() => {
      const delta = ((clientX - this.cStartX) / this.cWorkspaceW) * 100;
      this.splitPct.set(this.clampCreation(this.cStartPct + delta));
    });
  }

  private endCreationDrag(): void {
    if (!this.draggingCreation) return;
    this.draggingCreation = false;
    this.isDragging.set(false);
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
    document.removeEventListener('mousemove', this.mmFn);
    document.removeEventListener('mouseup', this.muFn);
    document.removeEventListener('touchmove', this.tmFn);
    document.removeEventListener('touchend', this.teFn);
    localStorage.setItem(SPLIT_KEY, String(this.splitPct()));
  }

  resetSplit(): void {
    this.splitPct.set(DEFAULT_SPLIT);
    localStorage.setItem(SPLIT_KEY, String(DEFAULT_SPLIT));
  }

  expandLeft(): void {
    this.splitPct.set(MAX_PCT);
    localStorage.setItem(SPLIT_KEY, String(MAX_PCT));
  }

  expandRight(): void {
    this.splitPct.set(MIN_PCT);
    localStorage.setItem(SPLIT_KEY, String(MIN_PCT));
  }

  private draggingS1 = false;
  private s1StartX = 0;
  private s1StartPct = 0;
  private s1W = 0;
  private sm1Fn!: (e: MouseEvent) => void;
  private su1Fn!: () => void;

  onSaved1MouseDown(e: MouseEvent): void {
    e.preventDefault();
    this.draggingS1 = true;
    this.s1StartX = e.clientX;
    this.s1StartPct = this.split1Pct();
    this.s1W = this.workspaceRef.nativeElement.offsetWidth;
    this.isDraggingSaved1.set(true);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    this.sm1Fn = (ev: MouseEvent) => {
      if (!this.draggingS1) return;
      this.zone.run(() => {
        const delta = ((ev.clientX - this.s1StartX) / this.s1W) * 100;
        const v = Math.max(MIN_PANEL_PCT, Math.min(this.split2Pct() - MIN_PANEL_PCT, this.s1StartPct + delta));
        this.split1Pct.set(v);
      });
    };
    this.su1Fn = () => this.endS1Drag();
    document.addEventListener('mousemove', this.sm1Fn);
    document.addEventListener('mouseup', this.su1Fn);
  }

  private endS1Drag(): void {
    if (!this.draggingS1) return;
    this.draggingS1 = false;
    this.isDraggingSaved1.set(false);
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
    document.removeEventListener('mousemove', this.sm1Fn);
    document.removeEventListener('mouseup', this.su1Fn);
    localStorage.setItem(SAVED_SPLIT1_KEY, String(this.split1Pct()));
  }

  private draggingS2 = false;
  private s2StartX = 0;
  private s2StartPct = 0;
  private s2W = 0;
  private sm2Fn!: (e: MouseEvent) => void;
  private su2Fn!: () => void;

  onSaved2MouseDown(e: MouseEvent): void {
    e.preventDefault();
    this.draggingS2 = true;
    this.s2StartX = e.clientX;
    this.s2StartPct = this.split2Pct();
    this.s2W = this.workspaceRef.nativeElement.offsetWidth;
    this.isDraggingSaved2.set(true);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    this.sm2Fn = (ev: MouseEvent) => {
      if (!this.draggingS2) return;
      this.zone.run(() => {
        const delta = ((ev.clientX - this.s2StartX) / this.s2W) * 100;
        const v = Math.max(this.split1Pct() + MIN_PANEL_PCT, Math.min(100 - MIN_PANEL_PCT, this.s2StartPct + delta));
        this.split2Pct.set(v);
      });
    };
    this.su2Fn = () => this.endS2Drag();
    document.addEventListener('mousemove', this.sm2Fn);
    document.addEventListener('mouseup', this.su2Fn);
  }

  private endS2Drag(): void {
    if (!this.draggingS2) return;
    this.draggingS2 = false;
    this.isDraggingSaved2.set(false);
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
    document.removeEventListener('mousemove', this.sm2Fn);
    document.removeEventListener('mouseup', this.su2Fn);
    localStorage.setItem(SAVED_SPLIT2_KEY, String(this.split2Pct()));
  }

  resetSavedSplit(): void {
    this.split1Pct.set(DEFAULT_S1);
    this.split2Pct.set(DEFAULT_S2);
    localStorage.setItem(SAVED_SPLIT1_KEY, String(DEFAULT_S1));
    localStorage.setItem(SAVED_SPLIT2_KEY, String(DEFAULT_S2));
  }

  @HostListener('document:keydown.escape')
  onEsc(): void {
    if (this.workspacePhase() === 'creation') {
      this.resetSplit();
    } else {
      this.resetSavedSplit();
    }
  }

  @HostListener('window:resize')
  onResize(): void {
    this.isNarrow.set(window.innerWidth < 900);
  }

  get leftStyle(): Record<string, string> {
    if (this.isNarrow()) return {};
    return {flex: `0 0 ${this.splitPct()}%`, maxWidth: `${this.splitPct()}%`};
  }

  get rightStyle(): Record<string, string> {
    if (this.isNarrow()) return {};
    const r = 100 - this.splitPct();
    return {flex: `0 0 ${r}%`, maxWidth: `${r}%`};
  }

  get leftPct(): number {
    return Math.round(this.splitPct());
  }

  get rightPct(): number {
    return Math.round(100 - this.splitPct());
  }

  get isCreateFormValid(): boolean {
    return this.jobCreateRef?.formValid() ?? false;
  }

  private clampCreation(n: number): number {
    return Math.max(MIN_PCT, Math.min(MAX_PCT, n));
  }
}
