import { CommonModule } from '@angular/common';
import { Component, computed, input, output, signal } from '@angular/core';
import { ETL_STEP_CATALOGUE, EtlStepDef } from '../../../../../core/models/etl.model';
import { DataVersion } from '../../../../../core/models/data-version.model';

interface WorkflowStep {
  index: number;
  type: string;
  params: Record<string, unknown>;
  def?: EtlStepDef;
  category: string;
  label: string;
  description: string;
  color: string;
  paramsPreview: string[];
}

@Component({
  selector: 'app-workflow-canvas',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './workflow-canvas.component.html',
  styleUrl: './workflow-canvas.component.scss',
})
export class WorkflowCanvasComponent {

  version = input.required<DataVersion>();
  rawCount = input.required<number>();
  selectedIndex = signal(-1);

  stepClick = output<WorkflowStep>();

  steps = computed<WorkflowStep[]>(() => {
    return this.version().appliedSteps.map((s, i) => {
      const type = String(s['type']);
      const params = (s['params'] as Record<string, unknown>) ?? {};
      const def = ETL_STEP_CATALOGUE.find(d => d.type === type);
      return {
        index: i,
        type,
        params,
        def,
        category: def?.category ?? 'Step',
        label: def?.label ?? type,
        description: def?.description ?? '',
        color: def?.color ?? '#888',
        paramsPreview: this.buildPreview(type, params),
      };
    });
  });

  outputCount = computed(() => this.version().recordCount);
  removedCount = computed(() => this.rawCount() - this.outputCount());

  onStepClick(step: WorkflowStep): void {
    this.selectedIndex.set(this.selectedIndex() === step.index ? -1 : step.index);
    this.stepClick.emit(step);
  }

  trackByIdx(_: number, s: WorkflowStep): number {
    return s.index;
  }

  /** Builds 3-line preview text for the card's "preview" block. */
  private buildPreview(type: string, params: Record<string, unknown>): string[] {
    switch (type) {
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
      default:
        return Object.entries(params).slice(0, 3).map(([k, v]) => `${k}: ${JSON.stringify(v)}`);
    }
  }

  private applyCase(s: string, mode: string): string {
    if (mode === 'upper') return s.toUpperCase();
    if (mode === 'lower') return s.toLowerCase();
    return s.split(' ').map(w => w[0].toUpperCase() + w.slice(1)).join(' ');
  }
}
