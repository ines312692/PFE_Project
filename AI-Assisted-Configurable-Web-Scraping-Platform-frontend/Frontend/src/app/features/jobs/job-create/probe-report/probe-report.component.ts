import {Component, computed, input, output} from '@angular/core';
import {DecimalPipe} from '@angular/common';
import {
  DIFFICULTY_CONFIG, ORIGIN_VALIDATION_CONFIG,
  PROVIDER_LABELS,
  SiteProbeResponse,
} from '../../../../core/models/site-probe.model';

@Component({
  selector: 'app-probe-report',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './probe-report.component.html',
  styleUrl: './probe-report.component.scss',
})
export class ProbeReportComponent {

  probe = input.required<SiteProbeResponse>();
  closeReport = output<void>();

  readonly difficultyConfig = DIFFICULTY_CONFIG;
  readonly providerLabels = PROVIDER_LABELS;

  hasDifficulty = computed(() => !!this.probe().difficulty);
  hasProtections = computed(() => (this.probe().protections?.length ?? 0) > 0);
  hasSignals = computed(() => this.probe().signals.length > 0);
  readonly originValidationConfig = ORIGIN_VALIDATION_CONFIG;

  hasOriginCandidates = computed(
    () => (this.probe().originCandidates?.length ?? 0) > 0
  );

  usableOriginCount = computed(
    () => this.probe().originCandidates?.filter(c => c.usable).length ?? 0
  );
  scoreCirclePath = computed(() => {
    const score = this.probe().difficulty?.score ?? 0;
    const radius = 52;
    const circumference = 2 * Math.PI * radius;
    const offset = circumference - (score / 100) * circumference;
    return {circumference, offset};
  });

  onClose(): void {
    this.closeReport.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.onClose();
    }
  }
}
