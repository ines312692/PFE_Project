import {Component, input, computed, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {JobExecution, EXECUTION_STATUS_LABELS} from '../../../core/models/job-execution.model';

@Component({
  selector: 'app-job-history',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './job-history.component.html',
  styleUrls: ['./job-history.component.scss'],
})
export class JobHistoryComponent {

  executions = input<JobExecution[]>([]);
  selectedExecId = signal<string | null>(null);

  selectedExec = computed(() => {
    const id = this.selectedExecId();
    return id ? this.executions().find(e => e.id === id) ?? null : null;
  });

  selectExec(id: string): void {
    this.selectedExecId.set(this.selectedExecId() === id ? null : id);
  }

  statusLabel(status: string): string {
    return EXECUTION_STATUS_LABELS[status] ?? status;
  }
}
