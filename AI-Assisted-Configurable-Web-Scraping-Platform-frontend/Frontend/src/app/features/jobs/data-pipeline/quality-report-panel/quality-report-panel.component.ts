import {CommonModule} from '@angular/common';
import {
  Component, ElementRef, OnDestroy, OnInit, computed, inject, input, output,
} from '@angular/core';
import {FieldQuality, QualityReport} from '../../../../core/models/etl.model';

interface FieldRow extends FieldQuality {
  name: string;
}

@Component({
  selector: 'app-quality-report-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './quality-report-panel.component.html',
  styleUrl: './quality-report-panel.component.scss',
})
export class QualityReportPanelComponent implements OnInit, OnDestroy {

  private readonly host = inject(ElementRef<HTMLElement>);

  report = input.required<QualityReport>();
  closed = output<void>();

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

  close(): void {
    this.closed.emit();
  }

  pct(rate: number): number {
    return Math.round(rate * 100);
  }

  fields = computed<FieldRow[]>(() =>
    Object.entries(this.report().byField).map(([name, f]) => ({name, ...f})),
  );

  overallClass = computed<'good' | 'warn' | 'bad'>(() => {
    const r = this.report().validityRate;
    return r >= 0.95 ? 'good' : r >= 0.8 ? 'warn' : 'bad';
  });

  completenessClass(rate: number): 'ok' | 'partial' | 'empty' {
    if (rate >= 1) return 'ok';
    if (rate <= 0) return 'empty';
    return 'partial';
  }

  completenessBar(rate: number): string {
    const c = this.completenessClass(rate);
    return c === 'ok' ? 'qr__bar-fill--blue'
      : c === 'partial' ? 'qr__bar-fill--amber'
        : 'qr__bar-fill--pink';
  }

  validityBar(rate: number): string {
    return rate >= 0.8 ? 'qr__bar-fill--green' : 'qr__bar-fill--pink';
  }
}
