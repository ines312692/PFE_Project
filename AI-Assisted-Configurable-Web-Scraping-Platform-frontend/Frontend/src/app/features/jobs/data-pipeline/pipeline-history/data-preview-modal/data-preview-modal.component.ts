import {CommonModule, DOCUMENT} from '@angular/common';
import {
  Component, computed, ElementRef, inject,
  input, OnDestroy, OnInit, output, signal,
} from '@angular/core';
import {DataVersion} from '../../../../../core/models/data-version.model';

@Component({
  selector: 'app-data-preview-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './data-preview-modal.component.html',
  styleUrl: './data-preview-modal.component.scss',
})
export class DataPreviewModalComponent implements OnInit, OnDestroy {

  private readonly el = inject(ElementRef<HTMLElement>);
  private readonly doc = inject(DOCUMENT);

  version = input<DataVersion | null>(null);
  loading = input<boolean>(false);
  closed = output<void>();

  tab = signal<'table' | 'json'>('table');
  copied = signal(false);

  readonly records = computed<Record<string, unknown>[]>(
    () => this.version()?.records ?? []
  );

  readonly columns = computed<string[]>(() => {
    const keys = new Set<string>();
    for (const row of this.records()) {
      Object.keys(row).forEach(k => keys.add(k));
    }
    return [...keys];
  });

  readonly jsonText = computed(() => JSON.stringify(this.records(), null, 2));

  /** Téléporte le modal hors de <main> → enfant direct de <body>,
   *  pour qu'il recouvre navbar, sidebar et footer. */
  ngOnInit(): void {
    this.doc.body.appendChild(this.el.nativeElement);
  }

  ngOnDestroy(): void {
    this.el.nativeElement.remove();
  }

  cell(row: Record<string, unknown>, col: string): string {
    const v = row[col];
    if (v === null || v === undefined) return '';
    if (typeof v === 'object') return JSON.stringify(v);
    return String(v);
  }

  isEmpty(row: Record<string, unknown>, col: string): boolean {
    const v = row[col];
    return v === null || v === undefined || v === '';
  }

  copyJson(): void {
    navigator.clipboard?.writeText(this.jsonText()).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    });
  }

  trackCol(_: number, c: string): string {
    return c;
  }

  trackRow(i: number): number {
    return i;
  }
}
