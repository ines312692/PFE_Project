import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {CommonModule, DatePipe} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {AuditLogService} from '../../../core/services/audit-log.service';
import {ToastService} from '../../../core/services/toast.service';
import {
  ALL_ACTIONS,
  ALL_OUTCOMES,
  ALL_RESOURCES,
  AuditAction,
  AuditLog,
  AuditOutcome,
  AuditResource,
  AuditSortField,
} from '../../../core/models/audit-log.model';

@Component({
  selector: 'app-audit-logs',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  templateUrl: './audit-logs.component.html',
  styleUrls: ['./audit-logs.component.scss'],
})
export class AuditLogsComponent implements OnInit {

  private readonly auditService = inject(AuditLogService);
  private readonly toast = inject(ToastService);
  logs = this.auditService.logs;
  loading = this.auditService.loading;
  stats = this.auditService.stats;
  totalElements = this.auditService.totalElements;
  totalPages = this.auditService.totalPages;
  currentPage = this.auditService.currentPage;
  readonly PAGE_SIZE = 7;

  searchQuery = signal('');
  filterAction = signal<AuditAction | 'ALL'>('ALL');
  filterResource = signal<AuditResource | 'ALL'>('ALL');
  filterOutcome = signal<AuditOutcome | 'ALL'>('ALL');
  filterFromDate = signal<string>('');
  filterToDate = signal<string>('');
  filterPanelOpen = signal(false);

  sortField = signal<AuditSortField>('occurredAt');
  sortDirection = signal<'asc' | 'desc'>('desc');
  viewingLog = signal<AuditLog | null>(null);
  readonly actionOptions = ALL_ACTIONS;
  readonly resourceOptions = ALL_RESOURCES;
  readonly outcomeOptions = ALL_OUTCOMES;
  private searchDebounce: any;

  activeFilterCount = computed(() => {
    let n = 0;
    if (this.filterAction() !== 'ALL') n++;
    if (this.filterResource() !== 'ALL') n++;
    if (this.filterOutcome() !== 'ALL') n++;
    if (this.filterFromDate()) n++;
    if (this.filterToDate()) n++;
    return n;
  });

  hasActiveFilters = computed(() =>
    this.searchQuery() !== '' || this.activeFilterCount() > 0,
  );

  paginationFrom = computed(() =>
    this.logs().length === 0 ? 0 : this.currentPage() * this.PAGE_SIZE + 1,
  );

  paginationTo = computed(() =>
    this.currentPage() * this.PAGE_SIZE + this.logs().length,
  );

  ngOnInit() {
    this.refresh();
    this.loadStats();
  }

  refresh() {
    this.auditService.search({
      page: this.currentPage(),
      size: this.PAGE_SIZE,
      search: this.searchQuery() || undefined,
      action: this.filterAction() !== 'ALL' ? this.filterAction() as AuditAction : undefined,
      resourceType: this.filterResource() !== 'ALL' ? this.filterResource() as AuditResource : undefined,
      outcome: this.filterOutcome() !== 'ALL' ? this.filterOutcome() as AuditOutcome : undefined,
      occurredFrom: this.toIsoOrUndefined(this.filterFromDate()),
      occurredTo: this.toIsoOrUndefined(this.filterToDate()),
      sortBy: this.sortField(),
      sortDirection: this.sortDirection(),
    }).subscribe({
      error: () => this.toast.error('Failed to load audit logs'),
    });
  }

  loadStats() {
    this.auditService.getStats().subscribe();
  }

  onSearchChange(q: string) {
    this.searchQuery.set(q);
    clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => {
      this.currentPage.set(0);
      this.refresh();
    }, 300);
  }

  onFilterChange() {
    this.currentPage.set(0);
    this.refresh();
  }

  clearFilters() {
    this.searchQuery.set('');
    this.filterAction.set('ALL');
    this.filterResource.set('ALL');
    this.filterOutcome.set('ALL');
    this.filterFromDate.set('');
    this.filterToDate.set('');
    this.filterPanelOpen.set(false);
    this.currentPage.set(0);
    this.refresh();
  }

  toggleFilterPanel() {
    this.filterPanelOpen.update(o => !o);
  }

  toggleDateSort() {
    if (this.sortField() === 'occurredAt') {
      this.sortDirection.update(d => d === 'desc' ? 'asc' : 'desc');
    } else {
      this.sortField.set('occurredAt');
      this.sortDirection.set('desc');
    }
    this.currentPage.set(0);
    this.refresh();
  }

  prevPage() {
    if (this.currentPage() > 0) {
      this.currentPage.set(this.currentPage() - 1);
      this.refresh();
    }
  }

  nextPage() {
    if (this.currentPage() < this.totalPages() - 1) {
      this.currentPage.set(this.currentPage() + 1);
      this.refresh();
    }
  }

  openDetail(log: AuditLog, event?: Event) {
    event?.stopPropagation();
    this.viewingLog.set(log);
  }

  closeDetail() {
    this.viewingLog.set(null);
  }

  initialOf(s: string | null | undefined): string {
    return (s ?? '?').charAt(0).toUpperCase();
  }

  formatJson(s: string | null | undefined): string {
    if (!s) return '';
    try {
      return JSON.stringify(JSON.parse(s), null, 2);
    } catch {
      return s;
    }
  }

  copyToClipboard(text: string | null | undefined) {
    if (!text) return;
    navigator.clipboard.writeText(text).then(
      () => this.toast.success('Copied to clipboard'),
      () => this.toast.error('Failed to copy'),
    );
  }

  truncate(s: string | null | undefined, max = 60): string {
    if (!s) return '';
    return s.length <= max ? s : s.slice(0, max) + '…';
  }

  private toIsoOrUndefined(localDate: string): string | undefined {
    if (!localDate) return undefined;
    const d = new Date(localDate);
    return isNaN(d.getTime()) ? undefined : d.toISOString();
  }

  confirmDeleteId = signal<string | null>(null);
  deleting = signal(false);

  confirmDelete(id: string, event: Event) {
    event.stopPropagation();
    this.confirmDeleteId.set(id);
  }

  cancelDelete() {
    this.confirmDeleteId.set(null);
  }

  deleteLog() {
    const id = this.confirmDeleteId();
    if (!id) return;
    this.deleting.set(true);
    this.auditService.delete(id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.confirmDeleteId.set(null);
        this.toast.success('Audit log deleted');
        this.refresh();
        this.loadStats();
      },
      error: () => {
        this.deleting.set(false);
        this.toast.error('Failed to delete audit log');
      },
    });
  }

  trackById = (_: number, log: AuditLog) => log.id;

  selectedIds = signal<Set<string>>(new Set());
  bulkDeleting = signal(false);
  confirmBulkDelete = signal(false);

  allSelected = computed(() => {
    const rows = this.logs();
    return rows.length > 0 && rows.every(l => this.selectedIds().has(l.id));
  });

  someSelected = computed(() => {
    const sel = this.selectedIds();
    return this.logs().some(l => sel.has(l.id)) && !this.allSelected();
  });

  selectedCount = computed(() => this.selectedIds().size);

  isSelected(id: string): boolean {
    return this.selectedIds().has(id);
  }

  toggleSelect(id: string, event?: Event) {
    event?.stopPropagation();
    this.selectedIds.update(set => {
      const next = new Set(set);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  toggleSelectAll() {
    this.selectedIds.update(set => {
      const next = new Set(set);
      const rows = this.logs();
      if (rows.every(l => next.has(l.id))) {
        rows.forEach(l => next.delete(l.id));
      } else {
        rows.forEach(l => next.add(l.id));
      }
      return next;
    });
  }

  clearSelection() {
    this.selectedIds.set(new Set());
  }

  openBulkDelete() {
    if (this.selectedCount() > 0) this.confirmBulkDelete.set(true);
  }

  cancelBulkDelete() {
    this.confirmBulkDelete.set(false);
  }

  deleteBulkLogs() {
    const ids = Array.from(this.selectedIds());
    if (ids.length === 0) return;
    this.bulkDeleting.set(true);
    this.auditService.deleteBulk(ids).subscribe({
      next: () => {
        this.bulkDeleting.set(false);
        this.confirmBulkDelete.set(false);
        this.clearSelection();
        this.toast.success(`${ids.length} audit log(s) deleted`);
        this.refresh();
        this.loadStats();
      },
      error: () => {
        this.bulkDeleting.set(false);
        this.toast.error('Failed to delete selected logs');
      },
    });
  }
}
