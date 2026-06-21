import {
  Component, inject, OnInit, OnDestroy, signal, computed, effect, HostListener, ElementRef
} from '@angular/core';
import {Router} from '@angular/router';
import {CommonModule, SlicePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {Subject, merge, of, debounceTime, distinctUntilChanged, switchMap, takeUntil} from 'rxjs';
import {JobService} from '../../../core/services/job.service';
import {ToastService} from '../../../core/services/toast.service';
import {Job, JobSearchParams} from '../../../core/models/job.model';
import {
  DateRangePickerComponent, DateRange
} from '../../../shared/components/date-range-picker/date-range-picker.component';
import {ConnectedPosition, OverlayModule} from '@angular/cdk/overlay';
import {UserService} from '../../../core/services/user.service';
import {AuthService} from '../../../core/auth/auth.service';

@Component({
  selector: 'app-job-list',
  standalone: true,
  imports: [CommonModule, SlicePipe, FormsModule, DateRangePickerComponent,OverlayModule],
  templateUrl: './job-list.component.html',
  styleUrl: './job-list.component.scss',
})
export class JobListComponent implements OnInit, OnDestroy {

  private readonly jobService = inject(JobService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly elRef = inject(ElementRef);
  private readonly destroy$ = new Subject<void>();
  private readonly searchTrigger$ = new Subject<JobSearchParams>();
  private readonly forceRefresh$ = new Subject<void>();
  private readonly userService = inject(UserService);
  avatarMap = signal<Map<string, string>>(new Map());

  jobs = this.jobService.jobs;
  loading = this.jobService.loading;
  totalElements = this.jobService.totalElements;
  totalPages = this.jobService.totalPages;
  currentPage = this.jobService.currentPage;
  stats = this.jobService.stats;

  viewMode = signal<'list' | 'card'>('list');
  filterPanelOpen = signal(false);
  sortField = signal<'createdAt' | 'updatedAt'>('createdAt');
  dateSortOrder = signal<'asc' | 'desc'>('desc');
  searchQuery = signal('');
  filterStatus = signal<string>('ALL');
  filterPriority = signal<string>('ALL');
  filterTrigger = signal<string>('ALL');
  filterJobType = signal<string>('ALL');
  filterDateRange = signal<DateRange | null>(null);
  pageRequest = signal(0);
  exportingId = signal<string | null>(null);
  private readonly auth = inject(AuthService);

  private readonly LIST_PAGE_SIZE = 7;
  private readonly CARD_PAGE_SIZE = 10;

  readonly pageSize = computed(() =>
    this.viewMode() === 'card' ? this.CARD_PAGE_SIZE : this.LIST_PAGE_SIZE
  );

  readonly statuses = ['ALL', 'CREATED', 'QUEUED', 'RUNNING', 'PAUSED', 'WAITING_FOR_HUMAN', 'COMPLETED', 'FAILED', 'CANCELLED'];
  readonly priorities = ['ALL', 'LOW', 'NORMAL', 'HIGH', 'CRITICAL'];
  readonly triggers = ['ALL', 'MANUAL', 'SCHEDULED', 'WEBHOOK'];
  readonly jobTypes = ['ALL', 'SCRAPER', 'CRAWLER'];

  activeFilterCount = computed(() => {
    let n = 0;
    if (this.filterStatus() !== 'ALL') n++;
    if (this.filterPriority() !== 'ALL') n++;
    if (this.filterTrigger() !== 'ALL') n++;
    if (this.filterJobType() !== 'ALL') n++;
    if (this.filterDateRange()) n++;
    return n;
  });

  hasActiveFilters = computed(() => this.searchQuery() !== '' || this.activeFilterCount() > 0);

  selectedIds = signal<Set<string>>(new Set());
  confirmDeleteId = signal<string | null>(null);
  confirmBulkDelete = signal(false);
  selectedCount = computed(() => this.selectedIds().size);
  openMenuId = signal<string | null>(null);


  isAllSelected = computed(() => {
    const j = this.jobs();
    return j.length > 0 && j.every(x => this.selectedIds().has(x.id));
  });

  isIndeterminate = computed(() => {
    const j = this.jobs();
    const c = j.filter(x => this.selectedIds().has(x.id)).length;
    return c > 0 && c < j.length;
  });

  runningWidgetOpen = signal(false);
  runningJobs = computed(() => this.jobs().filter(j => j.status === 'RUNNING'));

  paginatedJobs = computed(() => this.jobs());
  paginationFrom = computed(() => this.totalElements() === 0 ? 0 : this.currentPage() * this.pageSize() + 1);
  paginationTo = computed(() => Math.min((this.currentPage() + 1) * this.pageSize(), this.totalElements()));
  filteredJobs = computed(() => this.jobs());

  menuPositions: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
    { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -6 },
  ];

  constructor() {
    effect(() => {
      this.searchQuery();
      this.filterStatus();
      this.filterPriority();
      this.filterTrigger();
      this.filterJobType();
      this.filterDateRange();
      this.sortField();
      this.dateSortOrder();
      this.viewMode();
      this.pageRequest.set(0);
    }, {allowSignalWrites: true});

    effect(() => {
      this.searchTrigger$.next(this.buildParams());
    }, {allowSignalWrites: true});

    merge(
      this.searchTrigger$.pipe(
        debounceTime(300),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
      ),
      this.forceRefresh$.pipe(
        switchMap(() => of(this.buildParams()))
      )
    ).pipe(
      switchMap(params => this.jobService.search(params)),
      takeUntil(this.destroy$),
    ).subscribe();
  }

  ngOnInit(): void {
    this.jobService.getStats().subscribe();
    this.loadAvatars();
  }

  private loadAvatars(): void {
    if (!this.auth.hasRole('ADMIN')) return;

    this.userService.getAllUsers().subscribe({
      next: res => {
        const map = new Map<string, string>();
        for (const u of res.data ?? []) {
          if (u.avatarUrl) map.set(u.id, u.avatarUrl);
        }
        this.avatarMap.set(map);
      },
      error: () => { /* avatars optionnels */ }
    });
  }

  avatarFor(job: Job): string | null {
    return this.avatarMap().get(job.createdBy) ?? null;
  }

  onAvatarError(e: Event): void {
    (e.target as HTMLImageElement).style.display = 'none';
  }
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private buildParams(): JobSearchParams {
    return {
      page: this.pageRequest(),
      size: this.pageSize(),
      search: this.searchQuery() || undefined,
      status: this.filterStatus() as any,
      priority: this.filterPriority() as any,
      triggerType: this.filterTrigger() as any,
      jobType: this.filterJobType() as any,
      createdFrom: this.filterDateRange()?.start
        ? new Date(new Date(this.filterDateRange()!.start).setHours(0, 0, 0, 0)).toISOString() : undefined,
      createdTo: this.filterDateRange()?.end
        ? new Date(new Date(this.filterDateRange()!.end).setHours(23, 59, 59, 999)).toISOString() : undefined,
      sortBy: this.sortField(),
      sortDirection: this.dateSortOrder(),
    };
  }

  private refresh() {
    this.forceRefresh$.next();
    this.jobService.getStats().subscribe();
  }

  toggleMenu(id: string, e: Event): void {
    e.stopPropagation();
    this.openMenuId.update(cur => cur === id ? null : id);
  }

  closeMenu(): void {
    this.openMenuId.set(null);
  }

  toggleDateSort() {
    this.dateSortOrder.update(o => o === 'desc' ? 'asc' : 'desc');
  }

  setSortField(f: 'createdAt' | 'updatedAt') {
    this.sortField.set(f);
    this.dateSortOrder.set('desc');
  }

  toggleFilterPanel() {
    this.filterPanelOpen.update(v => !v);
  }

  onDateRangeChange(r: DateRange | null) {
    this.filterDateRange.set(r);
  }

  clearFilters() {
    this.searchQuery.set('');
    this.filterStatus.set('ALL');
    this.filterPriority.set('ALL');
    this.filterTrigger.set('ALL');
    this.filterJobType.set('ALL');
    this.filterDateRange.set(null);
    this.filterPanelOpen.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const panel = this.elRef.nativeElement.querySelector('.filter-dropdown');
    if (panel && !panel.contains(event.target)) this.filterPanelOpen.set(false);
  }

  countByStatus(s: string): number {
    const st = this.stats();
    if (!st) return 0;
    switch (s) {
      case 'CREATED':
        return st.created;
      case 'QUEUED':
        return st.queued;
      case 'RUNNING':
        return st.running;
      case 'PAUSED':
        return st.paused;
      case 'WAITING_FOR_HUMAN':
        return st.waitingForHuman;
      case 'COMPLETED':
        return st.completed;
      case 'FAILED':
        return st.failed;
      case 'CANCELLED':
        return st.cancelled;
      default:
        return 0;
    }
  }

  prevPage() {
    if (this.currentPage() > 0) this.pageRequest.set(this.currentPage() - 1);
  }

  nextPage() {
    if (this.currentPage() < this.totalPages() - 1) this.pageRequest.set(this.currentPage() + 1);
  }

  isSelected(id: string) {
    return this.selectedIds().has(id);
  }

  toggleSelect(id: string, e: Event) {
    e.stopPropagation();
    this.selectedIds.update(s => {
      const n = new Set(s);
      n.has(id) ? n.delete(id) : n.add(id);
      return n;
    });
  }

  toggleSelectAll() {
    const j = this.jobs();
    if (this.isAllSelected()) {
      this.selectedIds.update(s => {
        const n = new Set(s);
        j.forEach(x => n.delete(x.id));
        return n;
      });
    } else {
      this.selectedIds.update(s => {
        const n = new Set(s);
        j.forEach(x => n.add(x.id));
        return n;
      });
    }
  }

  clearSelection() {
    this.selectedIds.set(new Set());
  }

  bulkDelete() {
    this.confirmBulkDelete.set(true);
  }

  cancelBulkDelete() {
    this.confirmBulkDelete.set(false);
  }

  confirmBulkDeleteAction() {
    const ids = [...this.selectedIds()];
    const count = ids.length;
    let done = 0;
    ids.forEach(id => this.jobService.delete(id).subscribe({
      next: () => {
        if (++done === count) {
          this.clearSelection();
          this.confirmBulkDelete.set(false);
          this.toast.success(`${count} job${count > 1 ? 's' : ''} deleted.`);
          this.refresh();
        }
      },
      error: () => {
        this.toast.error('Failed to delete one or more jobs.');
        this.confirmBulkDelete.set(false);
      }
    }));
  }

  goToCreate() {
    this.router.navigate(['/jobs/create']);
  }

  goToDetail(id: string) {
    const job = this.jobs().find(j => j.id === id);
    this.router.navigate(
      job && ['RUNNING', 'QUEUED'].includes(job.status)
        ? ['/jobs', id, 'run']
        : ['/jobs', id]
    );
  }

  goToEdit(id: string, e: Event) {
    e.stopPropagation();
    this.router.navigate(['/jobs', id, 'edit']);
  }

  confirmDelete(id: string, e: Event) {
    e.stopPropagation();
    this.confirmDeleteId.set(id);
  }

  cancelDelete() {
    this.confirmDeleteId.set(null);
  }

  deleteJob() {
    const id = this.confirmDeleteId();
    if (!id) return;
    this.jobService.delete(id).subscribe({
      next: () => {
        this.confirmDeleteId.set(null);
        this.toast.success('Job deleted.');
        this.refresh();
      },
      error: () => this.toast.error('Failed to delete.')
    });
  }

  launchJob(id: string, e: Event) {
    e.stopPropagation();
    this.router.navigate(['/jobs', id, 'run']);
  }

  pauseJob(id: string, e: Event) {
    e.stopPropagation();
    this.jobService.pause(id).subscribe({
      next: () => {
        this.toast.warning('Job paused.');
        this.refresh();
      },
      error: () => this.toast.error('Failed to pause.')
    });
  }

  duplicateJob(id: string, e: Event) {
    e.stopPropagation();
    this.jobService.duplicate(id).subscribe({
      next: () => {
        this.toast.success('Job duplicated.');
        this.refresh();
      },
      error: () => this.toast.error('Failed to duplicate.')
    });
  }
  goToAgenda() {
    this.router.navigate(['/jobs/agenda']);
  }

  canLaunch(job: Job) {
    return ['CREATED', 'PAUSED', 'COMPLETED', 'FAILED','QUEUED'].includes(job.status);
  }

  canPause(job: Job) {
    return job.status === 'RUNNING';
  }

  canDelete(job: Job) {
    return !['RUNNING', 'QUEUED'].includes(job.status);
  }

  canEdit(job: Job) {
    return !['RUNNING', 'WAITING_FOR_HUMAN'].includes(job.status);
  }

  canDuplicate(job: Job) {
    return !['RUNNING', 'WAITING_FOR_HUMAN'].includes(job.status);
  }

  goToCaptcha(id: string, e: Event) {
    e.stopPropagation();
    this.router.navigate(['/jobs', id, 'captcha-intervention']);
  }

  onSortFieldClick(field: 'createdAt' | 'updatedAt'): void {
    if (this.sortField() === field) {
      this.dateSortOrder.update(o => o === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortField.set(field);
      this.dateSortOrder.set('desc');
    }
  }

  canExport(job: Job) {
    return job.status === 'COMPLETED';
  }

  exportCsv(id: string, e: Event) {
    e.stopPropagation();
    if (this.exportingId()) return;
    this.exportingId.set(id);

    this.jobService.getResults(id).subscribe({
      next: res => {
        const records = res.data?.records ?? [];
        if (!records.length) {
          this.toast.warning('No records to export.');
          this.exportingId.set(null);
          return;
        }
        this.downloadCsv(records, `job-${id}-results.csv`);
        this.exportingId.set(null);
      },
      error: () => {
        this.toast.error('Failed to export results.');
        this.exportingId.set(null);
      }
    });
  }

  private downloadCsv(records: Record<string, unknown>[], filename: string): void {
    if (!records.length) return;
    const cols = Object.keys(records[0]);
    const header = cols.join(',');
    const body = records
      .map(r =>
        cols.map(c => {
          const val = r[c];
          const str = val != null ? String(val) : '';
          return `"${str.replace(/"/g, '""')}"`;
        }).join(',')
      )
      .join('\n');
    const blob = new Blob([`${header}\n${body}`], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
