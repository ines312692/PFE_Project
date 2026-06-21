import {CommonModule} from '@angular/common';
import {
  Component, ElementRef, inject, input, OnDestroy, OnInit, output, signal,
} from '@angular/core';

import {ExportFormat, ExportDestination} from '../../../core/models/etl.model';

export interface ExportSummary {
  rawCount: number;
  cleanCount: number;
  removedCount: number;
  stepCount: number;
}

export interface ExportRequestPayload {
  format: ExportFormat;
  destination: ExportDestination;
  destinationConfig?: Record<string, unknown>;
}

@Component({
  selector: 'app-export-dialog',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './export-dialog.component.html',
  styleUrl: './export-dialog.component.scss',
})
export class ExportDialogComponent implements OnInit, OnDestroy {

  private readonly host = inject(ElementRef<HTMLElement>);

  recordCount = input.required<number>();
  summary = input<ExportSummary | null>(null);
  exporting = input<boolean>(false);
  progress = input<number>(0);
  exportRequested = output<ExportRequestPayload>();
  closed = output<void>();

  readonly formats: ExportFormat[] = [
    'CSV', 'JSON', 'PARQUET', 'EXCEL',
    'MARKDOWN', 'JSON_LD', 'LLMS_TXT',
  ];

  readonly formatLabels: Record<ExportFormat, string> = {
    CSV: 'CSV',
    JSON: 'JSON',
    PARQUET: 'Parquet',
    EXCEL: 'Excel',
    MARKDOWN: 'Markdown',
    JSON_LD: 'JSON-LD',
    LLMS_TXT: 'llms.txt',
  };

  readonly formatHints: Record<ExportFormat, string> = {
    CSV: 'Spreadsheet-friendly',
    JSON: 'API integrations',
    PARQUET: 'Big data / analytics',
    EXCEL: 'Microsoft Excel',
    MARKDOWN: 'RAG / LLM context',
    JSON_LD: 'Knowledge graphs',
    LLMS_TXT: 'LLM-friendly summary',
  };

  isLlmReady(fmt: ExportFormat): boolean {
    return fmt === 'MARKDOWN' || fmt === 'JSON_LD' || fmt === 'LLMS_TXT';
  }

  readonly destinations: ExportDestination[] = ['FILE', 'POSTGRESQL', 'WEBHOOK'];

  exportFormat = signal<ExportFormat>('CSV');
  exportDestination = signal<ExportDestination>('FILE');
  destConfig = signal<Record<string, unknown>>({});
  errorMsg = signal<string | null>(null);

  private previousBodyOverflow = '';

  ngOnInit(): void {
    document.body.appendChild(this.host.nativeElement);
    this.previousBodyOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
  }

  ngOnDestroy(): void {
    document.body.style.overflow = this.previousBodyOverflow;
    this.host.nativeElement.remove();
  }

  selectFormat(fmt: ExportFormat): void {
    this.exportFormat.set(fmt);
  }

  selectDestination(dest: ExportDestination): void {
    this.exportDestination.set(dest);
    this.destConfig.set({});
    this.errorMsg.set(null);
  }

  patchDestConfig(key: string, value: unknown): void {
    this.destConfig.update(cfg => ({...cfg, [key]: value}));
    this.errorMsg.set(null);
  }

  close(): void {
    this.closed.emit();
  }

  submit(): void {
    const dest = this.exportDestination();
    const cfg = this.destConfig();

    const error = this.validate(dest, cfg);
    if (error) {
      this.errorMsg.set(error);
      return;
    }

    this.exportRequested.emit({
      format: this.exportFormat(),
      destination: dest,
      destinationConfig: Object.keys(cfg).length > 0 ? cfg : undefined,
    });
  }

  private validate(dest: ExportDestination, cfg: Record<string, unknown>): string | null {
    if (dest === 'POSTGRESQL') {
      if (!this.isFilled(cfg['jdbcUrl'])) return 'PostgreSQL export requires a JDBC URL.';
      if (!this.isFilled(cfg['username'])) return 'PostgreSQL export requires a username.';
      if (cfg['password'] === undefined || cfg['password'] === '') {
        return 'PostgreSQL export requires a password.';
      }
    }
    if (dest === 'WEBHOOK') {
      if (!this.isFilled(cfg['url'])) return 'Webhook export requires an endpoint URL.';
    }
    return null;
  }

  private isFilled(value: unknown): boolean {
    return value !== undefined && value !== null && String(value).trim() !== '';
  }
}
