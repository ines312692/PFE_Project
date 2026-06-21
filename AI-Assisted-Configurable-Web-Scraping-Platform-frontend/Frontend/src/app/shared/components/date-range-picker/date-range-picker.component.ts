import { Component, signal, computed, output } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface DateRange {
  start: Date;
  end: Date;
}

export interface DatePreset {
  label: string;
  range: () => DateRange;
}

@Component({
  selector:    'app-date-range-picker',
  standalone:  true,
  imports:     [CommonModule],
  templateUrl: './date-range-picker.component.html',
  styleUrl:    './date-range-picker.component.scss',
})
export class DateRangePickerComponent {
  dateRangeChange = output<DateRange | null>();

  isOpen       = signal(false);
  startDate    = signal<Date | null>(null);
  endDate      = signal<Date | null>(null);
  tempStart    = signal<Date | null>(null);
  tempEnd      = signal<Date | null>(null);
  hoverDate    = signal<Date | null>(null);
  activePreset = signal<string | null>(null);

  private today = new Date();

  viewMonth = signal(this.today.getMonth());
  viewYear  = signal(this.today.getFullYear());

  nextViewMonth = computed(() => this.viewMonth() === 11 ? 0 : this.viewMonth() + 1);
  nextViewYear  = computed(() => this.viewMonth() === 11 ? this.viewYear() + 1 : this.viewYear());

  hasSelection = computed(() => !!this.startDate() && !!this.endDate());

  readonly weekdays = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

  readonly presets: DatePreset[] = [
    { label: 'Today',        range: () => this.makeRange(0) },
    { label: 'Yesterday',    range: () => this.makeRange(1, 1) },
    { label: 'Last 7 days',  range: () => this.makeRange(6) },
    { label: 'Last 14 days', range: () => this.makeRange(13) },
    { label: 'Last 30 days', range: () => this.makeRange(29) },
    { label: 'Last 90 days', range: () => this.makeRange(89) },
    { label: 'This month',   range: () => {
      const now = new Date();
      return { start: new Date(now.getFullYear(), now.getMonth(), 1), end: now };
    }},
    { label: 'Last month',   range: () => {
      const now = new Date();
      const start = new Date(now.getFullYear(), now.getMonth() - 1, 1);
      const end   = new Date(now.getFullYear(), now.getMonth(), 0);
      return { start, end };
    }},
    { label: 'This year',    range: () => {
      const now = new Date();
      return { start: new Date(now.getFullYear(), 0, 1), end: now };
    }},
  ];

  leftCalendarDays = computed(() => this.generateDays(this.viewYear(), this.viewMonth()));
  rightCalendarDays = computed(() => this.generateDays(this.nextViewYear(), this.nextViewMonth()));

  private generateDays(year: number, month: number): number[] {
    const firstDay   = new Date(year, month, 1).getDay();
    const offset     = firstDay === 0 ? 6 : firstDay - 1; // Monday-based
    const daysInMonth = new Date(year, month + 1, 0).getDate();

    const days: number[] = [];
    for (let i = 0; i < offset; i++) days.push(0); // empty slots
    for (let d = 1; d <= daysInMonth; d++) days.push(d);
    return days;
  }

  prevMonth(): void {
    if (this.viewMonth() === 0) {
      this.viewMonth.set(11);
      this.viewYear.update(y => y - 1);
    } else {
      this.viewMonth.update(m => m - 1);
    }
  }

  nextMonth(): void {
    if (this.viewMonth() === 11) {
      this.viewMonth.set(0);
      this.viewYear.update(y => y + 1);
    } else {
      this.viewMonth.update(m => m + 1);
    }
  }

  // ── Day selection ──

  selectDay(day: number, year: number, month: number): void {
    if (this.isFutureDay(day, year, month)) return;

    const date = new Date(year, month, day);
    this.activePreset.set(null);

    if (!this.tempStart() || (this.tempStart() && this.tempEnd())) {
      // Start fresh selection
      this.tempStart.set(date);
      this.tempEnd.set(null);
      this.hoverDate.set(null);
    } else {
      // Complete selection
      if (date < this.tempStart()!) {
        this.tempEnd.set(this.tempStart());
        this.tempStart.set(date);
      } else {
        this.tempEnd.set(date);
      }
    }
  }

  onDayHover(day: number, year: number, month: number): void {
    if (this.tempStart() && !this.tempEnd()) {
      this.hoverDate.set(new Date(year, month, day));
    }
  }



  isToday(day: number, year: number, month: number): boolean {
    const t = this.today;
    return day === t.getDate() && month === t.getMonth() && year === t.getFullYear();
  }

  isDaySelected(day: number, year: number, month: number): boolean {
    const date = new Date(year, month, day);
    return this.isSameDay(date, this.tempStart()) || this.isSameDay(date, this.tempEnd());
  }

  isDayInRange(day: number, year: number, month: number): boolean {
    const date  = new Date(year, month, day);
    const start = this.tempStart();
    const end   = this.tempEnd() || this.hoverDate();
    if (!start || !end) return false;

    const lo = start < end ? start : end;
    const hi = start < end ? end : start;
    return date > lo && date < hi;
  }

  isRangeStart(day: number, year: number, month: number): boolean {
    return this.isSameDay(new Date(year, month, day), this.tempStart());
  }

  isRangeEnd(day: number, year: number, month: number): boolean {
    const end = this.tempEnd() || this.hoverDate();
    return !!end && this.isSameDay(new Date(year, month, day), end);
  }

  isFutureDay(day: number, year: number, month: number): boolean {
    return new Date(year, month, day) > this.today;
  }

  applyPreset(preset: DatePreset): void {
    const { start, end } = preset.range();
    this.tempStart.set(start);
    this.tempEnd.set(end);
    this.activePreset.set(preset.label);

    this.viewMonth.set(start.getMonth());
    this.viewYear.set(start.getFullYear());
  }


  toggle(): void {
    if (this.isOpen()) {
      this.close();
    } else {
      this.open();
    }
  }

  open(): void {
    this.tempStart.set(this.startDate());
    this.tempEnd.set(this.endDate());
    const ref = this.startDate() || this.today;
    this.viewMonth.set(ref.getMonth());
    this.viewYear.set(ref.getFullYear());

    this.isOpen.set(true);
  }

  close(): void {
    this.isOpen.set(false);
    this.hoverDate.set(null);
  }

  apply(): void {
    if (this.tempStart() && this.tempEnd()) {
      this.startDate.set(this.tempStart());
      this.endDate.set(this.tempEnd());
      this.dateRangeChange.emit({ start: this.tempStart()!, end: this.tempEnd()! });
    }
    this.close();
  }

  clearSelection(event: Event): void {
    event.stopPropagation();
    this.startDate.set(null);
    this.endDate.set(null);
    this.tempStart.set(null);
    this.tempEnd.set(null);
    this.activePreset.set(null);
    this.dateRangeChange.emit(null);
  }


  monthName(month: number): string {
    return ['January', 'February', 'March', 'April', 'May', 'June',
            'July', 'August', 'September', 'October', 'November', 'December'][month];
  }

  formatDate(date: Date): string {
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    return `${months[date.getMonth()]} ${date.getDate()}, ${date.getFullYear()}`;
  }

  private isSameDay(a: Date | null, b: Date | null): boolean {
    if (!a || !b) return false;
    return a.getDate() === b.getDate() && a.getMonth() === b.getMonth() && a.getFullYear() === b.getFullYear();
  }

  private makeRange(daysBack: number, daysBackEnd: number = 0): DateRange {
    const now   = new Date();
    const start = new Date(now);
    start.setDate(now.getDate() - daysBack);
    const end = new Date(now);
    end.setDate(now.getDate() - daysBackEnd);
    return { start, end };
  }
}
