import {CommonModule, DatePipe} from '@angular/common';
import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';

import {ScrapeResultResponse} from '../../../../core/models/scrape-result.model';
import {DataVersion, DataVersionSummary} from '../../../../core/models/data-version.model';
import {DataVersionService} from '../../../../core/services/data-version.service';
import {ScrapeResultService} from '../../../../core/services/scrape-result.service';
import {EtlService} from '../../../../core/services/etl.service';
import {ToastService} from '../../../../core/services/toast.service';
import {WorkflowCanvasComponent} from './workflow-canvas/workflow-canvas.component';
import {
  ExportDialogComponent,
  ExportRequestPayload,
} from '../../../../shared/components/export-dialog/export-dialog.component';
import {DataPreviewModalComponent} from './data-preview-modal/data-preview-modal.component';


@Component({
  selector: 'app-pipeline-history',
  standalone: true,
  imports: [CommonModule, DatePipe, WorkflowCanvasComponent, ExportDialogComponent, DataPreviewModalComponent],
  templateUrl: './pipeline-history.component.html',
  styleUrl: './pipeline-history.component.scss',
})
export class PipelineHistoryComponent implements OnInit {

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly scrapeResultSvc = inject(ScrapeResultService);
  private readonly versionSvc = inject(DataVersionService);
  private readonly etlSvc = inject(EtlService);
  private readonly toast = inject(ToastService);

  rawResult = signal<ScrapeResultResponse | null>(null);
  versions = signal<DataVersionSummary[]>([]);
  selectedVersion = signal<DataVersion | null>(null);
  loading = signal(true);
  loadingVersion = signal(false);
  rollingBack = signal(false);
  previewVersion = signal<DataVersion | null>(null);


  exportTarget = signal<DataVersion | null>(null);
  exporting = signal(false);
  exportProgress = signal(0);

  jobId!: string;

  rawCount = computed(() => this.rawResult()?.records?.length ?? 0);
  hasVersions = computed(() => this.versions().some(v => v.versionNumber > 0));

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      void this.router.navigate(['/jobs']);
      return;
    }
    this.jobId = id;

    this.scrapeResultSvc.getLatestByJobId(id).subscribe({
      next: res => {
        this.rawResult.set(res.data);
        this.versionSvc.list(res.data.id).subscribe({
          next: list => {
            // Newest first in the sidebar
            const sorted = [...list].sort((a, b) => b.versionNumber - a.versionNumber);
            this.versions.set(sorted);
            const latestNonRaw = sorted.find(v => v.versionNumber > 0);
            if (latestNonRaw) this.selectVersion(latestNonRaw);
            this.loading.set(false);
          },
          error: () => {
            this.toast.error('Could not load versions.');
            this.loading.set(false);
          },
        });
      },
      error: () => {
        this.toast.error('Scrape result not found.');
        void this.router.navigate(['/jobs', id]);
      },
    });
  }

  selectVersion(v: DataVersionSummary): void {
    if (v.versionNumber === 0) {
      // Raw version — no workflow to display
      this.selectedVersion.set(null);
      return;
    }
    if (this.selectedVersion()?.versionNumber === v.versionNumber) return;

    this.loadingVersion.set(true);
    const scrapeResultId = this.rawResult()!.id;
    this.versionSvc.get(scrapeResultId, v.versionNumber).subscribe({
      next: full => {
        this.selectedVersion.set(full);
        this.loadingVersion.set(false);
      },
      error: () => {
        this.toast.error(`Could not load v${v.versionNumber}.`);
        this.loadingVersion.set(false);
      },
    });
  }

  rollback(v: DataVersionSummary): void {
    if (this.rollingBack()) return;
    if (!confirm(`Roll back to v${v.versionNumber}? A new version will be created.`)) return;

    this.rollingBack.set(true);
    const scrapeResultId = this.rawResult()!.id;
    this.versionSvc.rollback(scrapeResultId, v.versionNumber).subscribe({
      next: newVersion => {
        this.toast.success(`Rolled back to v${v.versionNumber} (created v${newVersion.versionNumber}).`);
        this.versionSvc.list(scrapeResultId).subscribe(list => {
          this.versions.set([...list].sort((a, b) => b.versionNumber - a.versionNumber));
          this.selectVersion(newVersion);
          this.rollingBack.set(false);
        });
      },
      error: () => {
        this.toast.error('Rollback failed.');
        this.rollingBack.set(false);
      },
    });
  }

  /** Opens the export dialog for the currently viewed version. */
  openExport(v: DataVersion): void {
    this.exportTarget.set(v);
  }

  /** Runs the export for the targeted version, passing its versionNumber to the backend. */
  runExport(req: ExportRequestPayload): void {
    const target = this.exportTarget();
    if (!target) return;
    const scrapeResultId = this.rawResult()!.id;

    this.exporting.set(true);
    this.exportProgress.set(0);
    const interval = setInterval(
      () => this.exportProgress.update(p => Math.min(p + 15, 90)), 200);

    this.etlSvc.exportRecords(scrapeResultId, {
      ...req,
      fromVersion: target.versionNumber,
    }).subscribe({
      next: res => {
        clearInterval(interval);
        this.exportProgress.set(100);
        this.exporting.set(false);
        this.toast.success(res.message);
        if (res.downloadUrl) {
          const a = document.createElement('a');
          a.href = res.downloadUrl;
          a.download = `v${target.versionNumber}.${req.format.toLowerCase()}`;
          a.click();
        }
        setTimeout(() => this.exportTarget.set(null), 1200);
      },
      error: err => {
        clearInterval(interval);
        this.exportProgress.set(0);
        this.exporting.set(false);
        this.toast.error(err?.error?.message ?? 'Export failed.');
      },
    });
  }

  goBack(): void {
    void this.router.navigate(['/jobs', this.jobId, 'data-pipeline']);
  }

  trackByVersion(_: number, v: DataVersionSummary): number {
    return v.versionNumber;
  }


  /** Ouvre le modal de prévisualisation pour la version donnée (records déjà chargés). */
  openPreview(v: DataVersion): void {
    this.previewVersion.set(v);
  }

  closePreview(): void {
    this.previewVersion.set(null);
  }
  /** Construit une version v0 synthétique depuis le scrape result brut et l'ouvre. */
  openRawPreview(): void {
    const raw = this.rawResult();
    if (!raw) return;

    const rawVersion: DataVersion = {
      id: null,
      scrapeResultId: raw.id,
      versionNumber: 0,
      parentVersionId: null,
      records: raw.records ?? [],
      appliedSteps: [],
      stepReports: [],
      recordCount: raw.records?.length ?? 0,
      label: 'Raw data',
      createdBy: null,
      createdAt: '',
    };
    this.previewVersion.set(rawVersion);
  }
}
