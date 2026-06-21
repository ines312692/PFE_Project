import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {CommonModule, DatePipe} from '@angular/common';
import {Router} from '@angular/router';
import {ScheduleService, UpcomingRun} from '../../../core/services/schedule.service';

type ViewMode = 'month' | 'list';

interface CalendarCell {
  date: Date;
  inMonth: boolean;
  isToday: boolean;
  runs: UpcomingRun[];
}

@Component({
  selector: 'app-job-agenda',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './job-agenda.component.html',
  styleUrl: './job-agenda.component.scss',
})
export class JobAgendaComponent implements OnInit {
  private readonly schedule = inject(ScheduleService);
  private readonly router = inject(Router);

  view = signal<ViewMode>('month');
  cursor = signal<Date>(new Date());        // month being viewed
  loading = signal(false);
  runs = signal<UpcomingRun[]>([]);
  selectedDay = signal<Date | null>(null);

  monthLabel = computed(() =>
    this.cursor().toLocaleDateString(undefined, {month: 'long', year: 'numeric'})
  );

  weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  cells = computed<CalendarCell[]>(() => {
    const c = this.cursor();
    const year = c.getFullYear();
    const month = c.getMonth();

    const first = new Date(year, month, 1);
    const offset = (first.getDay() + 6) % 7;
    const gridStart = new Date(year, month, 1 - offset);

    const today = this.stripTime(new Date());
    const runsByDay = this.bucketByDay(this.runs());

    return Array.from({length: 42}, (_, i) => {
      const d = new Date(gridStart);
      d.setDate(gridStart.getDate() + i);
      const key = this.dayKey(d);
      return {
        date: d,
        inMonth: d.getMonth() === month,
        isToday: this.stripTime(d).getTime() === today.getTime(),
        runs: runsByDay.get(key) ?? [],
      };
    });
  });

  selectedRuns = computed<UpcomingRun[]>(() => {
    const day = this.selectedDay();
    if (!day) return [];
    const key = this.dayKey(day);
    return this.bucketByDay(this.runs()).get(key) ?? [];
  });

  groupedRuns = computed(() => {
    const map = new Map<string, UpcomingRun[]>();
    for (const r of this.runs()) {
      const key = this.dayKey(new Date(r.fireAt));
      const arr = map.get(key) ?? [];
      arr.push(r);
      map.set(key, arr);
    }
    return Array.from(map.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, items]) => ({key, date: new Date(key), items}));
  });

  ngOnInit(): void {
    this.load();
  }

  setView(v: ViewMode): void {
    this.view.set(v);
  }

  prevMonth(): void {
    this.shiftMonth(-1);
  }

  nextMonth(): void {
    this.shiftMonth(+1);
  }

  today(): void {
    this.cursor.set(new Date());
    this.selectedDay.set(null);
    this.load();
  }

  selectDay(cell: CalendarCell): void {
    if (cell.runs.length === 0) return;
    this.selectedDay.set(cell.date);
  }

  openJob(jobId: string): void {
    this.router.navigate(['/jobs', jobId]);
  }

  private shiftMonth(delta: number): void {
    const d = new Date(this.cursor());
    d.setMonth(d.getMonth() + delta);
    this.cursor.set(d);
    this.selectedDay.set(null);
    this.load();
  }

  private load(): void {
    const c = this.cursor();
    const start = new Date(c.getFullYear(), c.getMonth(), 1);
    const end = new Date(c.getFullYear(), c.getMonth() + 1, 1);
    this.loading.set(true);
    this.schedule.upcoming(start, end).subscribe({
      next: res => {
        this.runs.set(res.data ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.runs.set([]);
        this.loading.set(false);
      },
    });
  }

  private bucketByDay(list: UpcomingRun[]): Map<string, UpcomingRun[]> {
    const map = new Map<string, UpcomingRun[]>();
    for (const r of list) {
      const key = this.dayKey(new Date(r.fireAt));
      const arr = map.get(key) ?? [];
      arr.push(r);
      map.set(key, arr);
    }
    for (const arr of map.values()) {
      arr.sort((a, b) => a.fireAt.localeCompare(b.fireAt));
    }
    return map;
  }

  private dayKey(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private stripTime(d: Date): Date {
    return new Date(d.getFullYear(), d.getMonth(), d.getDate());
  }

  goBack(): void {
    void this.router.navigate(['/jobs']);
  }

}
