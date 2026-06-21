import {Component, computed, effect, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ProxyService} from '../../../core/services/proxy.service';
import {ToastService} from '../../../core/services/toast.service';
import {ConnectedPosition, OverlayModule} from '@angular/cdk/overlay';
import {
  Proxy, ProxyStats, ProxyStatus, ProxyProtocol, ProxySource,
  ProxyAnonymity, CreateProxyRequest,
  AntiBotProvider, ProtectionStatus, AntiBotStatus,
  ProxyCountryStat,
  PROTECTION_STATUS_LABELS,
  aggregateAntiBotStatus,
} from '../../../core/models/proxy.model';

type StatusFilter = 'ALL' | ProxyStatus;
type ProtocolFilter = 'ALL' | ProxyProtocol;
type SourceFilter = 'ALL' | ProxySource;
type CountryFilter = 'ALL' | string;
type AntiBotProviderFilter = 'ANY' | AntiBotProvider;
type AntiBotStatusFilter = 'ALL' | ProtectionStatus;

@Component({
  selector: 'app-proxy-list',
  standalone: true,
  imports: [CommonModule, FormsModule, OverlayModule],
  templateUrl: './proxy-list.component.html',
  styleUrls: ['./proxy-list.component.scss'],
})
export class ProxyListComponent implements OnInit {


  private proxyService = inject(ProxyService);
  private toast = inject(ToastService);

  proxies = signal<Proxy[]>([]);
  totalElements = signal(0);
  stats = signal<ProxyStats | null>(null);
  availableProviders = signal<AntiBotProvider[]>([]);
  availableCountries = signal<ProxyCountryStat[]>([]);

  loading = signal(false);
  refreshing = signal(false);
  testingProxyId = signal<string | null>(null);
  submitting = signal(false);

  currentIndex = signal(0);
  pageSize = signal(7);

  searchQuery = signal('');
  filterStatus = signal<StatusFilter>('ALL');
  filterProtocol = signal<ProtocolFilter>('ALL');
  filterSource = signal<SourceFilter>('ALL');
  filterCountry = signal<CountryFilter>('ALL');
  filterAntiBotProvider = signal<AntiBotProviderFilter>('ANY');
  filterAntiBotStatus = signal<AntiBotStatusFilter>('ALL');
  filterPanelOpen = signal(false);

  sortBy = signal<'latency' | 'lastChecked' | 'successRate' | 'createdAt'>('createdAt');
  sortOrder = signal<'asc' | 'desc'>('desc');

  showAddModal = signal(false);
  confirmDeleteId = signal<string | null>(null);
  openMenuId = signal<string | null>(null);
  showAntiBotPicker = signal<Proxy | null>(null);
  showAntiBotDetail = signal<Proxy | null>(null);
  formError = signal<string | null>(null);

  addForm = signal<CreateProxyRequest>({
    host: '', port: 8080, protocol: 'HTTP',
    username: null, password: null, country: null, anonymity: 'UNKNOWN',
  });

  readonly statuses: StatusFilter[] = ['ALL', 'ACTIVE', 'INACTIVE', 'BLACKLISTED'];
  readonly protocols: ProtocolFilter[] = ['ALL', 'HTTP', 'HTTPS', 'SOCKS4', 'SOCKS5'];
  readonly sources: SourceFilter[] = ['ALL', 'MANUAL', 'FREE_PROXY_LIST', 'PROXYSCRAPE', 'GEONODE'];
  readonly antiBotStatusOptions: ProtectionStatus[] =
    ['CLEAN', 'CHALLENGED', 'BLOCKED', 'UNREACHABLE', 'UNTESTED'];
  readonly anonymityOptions: ProxyAnonymity[] = ['ELITE', 'ANONYMOUS', 'TRANSPARENT', 'UNKNOWN'];
  readonly statusLabels = PROTECTION_STATUS_LABELS;

  totalPages = computed(() =>
    Math.max(1, Math.ceil(this.totalElements() / this.pageSize()))
  );

  paginationFrom = computed(() =>
    this.totalElements() === 0 ? 0 : this.currentIndex() * this.pageSize() + 1
  );

  paginationTo = computed(() =>
    Math.min((this.currentIndex() + 1) * this.pageSize(), this.totalElements())
  );

  activeFilterCount = computed(() => {
    let n = 0;
    if (this.filterStatus() !== 'ALL') n++;
    if (this.filterProtocol() !== 'ALL') n++;
    if (this.filterSource() !== 'ALL') n++;
    if (this.filterCountry() !== 'ALL') n++;
    if (this.filterAntiBotProvider() !== 'ANY') n++;
    if (this.filterAntiBotStatus() !== 'ALL') n++;
    return n;
  });

  hasActiveFilters = computed(() => this.activeFilterCount() > 0 || !!this.searchQuery());

  private debounceTimer?: any;
  private firstLoad = true;

  constructor() {
    effect(() => {
      this.searchQuery();
      this.filterStatus();
      this.filterProtocol();
      this.filterSource();
      this.filterCountry();
      this.filterAntiBotProvider();
      this.filterAntiBotStatus();
      this.sortBy();
      this.sortOrder();
      this.currentIndex();
      this.pageSize();
      this.scheduleLoad();
    });
  }

  ngOnInit() {
    this.loadStats();
    this.loadProviders();
    this.loadCountries();
  }

  private scheduleLoad() {
    if (this.debounceTimer) clearTimeout(this.debounceTimer);
    const delay = this.firstLoad ? 0 : 300;
    this.firstLoad = false;
    this.debounceTimer = setTimeout(() => this.loadProxies(), delay);
  }

  loadProxies() {
    this.loading.set(true);
    this.proxyService.findAll({
      index: this.currentIndex(),
      size: this.pageSize(),
      search: this.searchQuery() || undefined,
      status: this.filterStatus() === 'ALL' ? undefined : this.filterStatus() as ProxyStatus,
      protocol: this.filterProtocol() === 'ALL' ? undefined : this.filterProtocol() as ProxyProtocol,
      source: this.filterSource() === 'ALL' ? undefined : this.filterSource() as ProxySource,
      country: this.filterCountry() === 'ALL' ? undefined : this.filterCountry(),
      antiBotProvider: this.filterAntiBotProvider() === 'ANY'
        ? undefined
        : this.filterAntiBotProvider() as AntiBotProvider,
      antiBotStatus: this.filterAntiBotStatus() === 'ALL'
        ? undefined
        : this.filterAntiBotStatus() as ProtectionStatus,
      sortBy: this.sortBy(),
      sortOrder: this.sortOrder(),
    }).subscribe({
      next: page => {
        this.proxies.set(page.content ?? []);
        this.totalElements.set(page?.totalElements ?? 0);
        this.loading.set(false);
        const open = this.showAntiBotDetail();
        if (open) {
          const fresh = this.proxies().find(p => p.id === open.id);
          if (fresh) this.showAntiBotDetail.set(fresh);
        }
      },
      error: (err) => {
        console.error('[Proxy] Failed to load:', err);
        this.toast.error('Failed to load proxies');
        this.loading.set(false);
      },
    });
  }

  loadStats() {
    this.proxyService.getStats().subscribe({
      next: s => this.stats.set(s),
    });
  }

  loadProviders() {
    this.proxyService.listAntiBotProviders().subscribe({
      next: ps => this.availableProviders.set(ps ?? []),
      error: () => this.availableProviders.set(['CLOUDFLARE']),
    });
  }

  loadCountries() {
    this.proxyService.getAvailableCountries().subscribe({
      next: list => this.availableCountries.set(list ?? []),
      error: () => this.availableCountries.set([]),
    });
  }

  antiBotSummary(p: Proxy): {
    status: ProtectionStatus;
    testedCount: number;
    label: { label: string; tone: string; icon: string };
  } {
    const agg = aggregateAntiBotStatus(p);
    return {
      status: agg.status,
      testedCount: agg.testedCount,
      label: this.statusLabels[agg.status],
    };
  }

  providerStatus(p: Proxy, provider: AntiBotProvider): AntiBotStatus | null {
    return p?.antiBotStatuses?.[provider] ?? null;
  }

  openAntiBotPicker(p: Proxy, ev: Event) {
    ev.stopPropagation();
    this.showAntiBotPicker.set(p);
  }

  closeAntiBotPicker() {
    this.showAntiBotPicker.set(null);
  }

  openAntiBotDetail(p: Proxy, ev: Event) {
    ev.stopPropagation();
    this.showAntiBotDetail.set(p);
  }

  closeAntiBotDetail() {
    this.showAntiBotDetail.set(null);
  }

  runAntiBotCheck(p: Proxy, provider: AntiBotProvider) {
    this.testingProxyId.set(p.id);
    this.proxyService.triggerAntiBotCheck(p.id, provider).subscribe({
      next: r => {
        this.testingProxyId.set(null);
        const lbl = this.statusLabels[r.status]?.label ?? r.status;
        if (r.blocked) {
          this.toast.error(`${p.host}:${p.port} blocked by ${this.providerLabel(provider)}`);
        } else if (r.detected) {
          this.toast.success(`${p.host}:${p.port} — ${this.providerLabel(provider)}: ${lbl}`);
        } else {
          this.toast.info(`${p.host}:${p.port} — ${this.providerLabel(provider)}: ${lbl}`);
        }
        this.loadProxies();
        this.loadStats();
      },
      error: () => {
        this.testingProxyId.set(null);
        this.toast.error('Anti-bot check failed');
      },
    });
  }

  runFullAntiBotCheck(p: Proxy, ev: Event) {
    ev.stopPropagation();
    this.testingProxyId.set(p.id);
    this.proxyService.triggerFullAntiBotCheck(p.id).subscribe({
      next: r => {
        this.testingProxyId.set(null);
        if (r.success) this.toast.success(`${p.host}:${p.port}  full anti-bot check OK`);
        else this.toast.error(`${p.host}:${p.port}  at least one provider blocked`);
        this.loadProxies();
        this.loadStats();
      },
      error: () => {
        this.testingProxyId.set(null);
        this.toast.error('Full check failed');
      },
    });
  }

  providerLabel(provider: AntiBotProvider): string {
    return provider.replace(/_/g, ' ');
  }

  flagUrl(country: string | null | undefined): string | null {
    if (!country || country.length !== 2) return null;
    return `https://flagcdn.com/24x18/${country.toLowerCase()}.png`;
  }

  refreshPool() {
    this.refreshing.set(true);
    this.proxyService.refresh().subscribe({
      next: r => {
        this.toast.success(`Pool refreshed: ${r.added} new proxies added`);
        this.refreshing.set(false);
        this.currentIndex.set(0);
        this.loadProxies();
        this.loadStats();
        this.loadCountries();
      },
      error: () => {
        this.toast.error('Failed to refresh pool');
        this.refreshing.set(false);
      },
    });
  }

  openAddModal() {
    this.addForm.set({
      host: '', port: 8080, protocol: 'HTTP',
      username: null, password: null, country: null, anonymity: 'UNKNOWN',
    });
    this.formError.set(null);
    this.showAddModal.set(true);
  }

  closeAddModal() {
    this.showAddModal.set(false);
    this.formError.set(null);
  }

  updateAddForm<K extends keyof CreateProxyRequest>(key: K, value: CreateProxyRequest[K]) {
    this.addForm.update(f => ({...f, [key]: value}));
  }

  submitAdd() {
    const f = this.addForm();
    if (!f.host?.trim()) {
      this.formError.set('Host is required');
      return;
    }
    if (!f.port || f.port < 1 || f.port > 65535) {
      this.formError.set('Port must be between 1 and 65535');
      return;
    }

    this.submitting.set(true);
    this.formError.set(null);
    this.proxyService.create({
      ...f,
      host: f.host.trim(),
      country: f.country?.trim().toUpperCase() || null,
      username: f.username?.trim() || null,
      password: f.password?.trim() || null,
    }).subscribe({
      next: () => {
        this.toast.success(`Proxy ${f.host}:${f.port} added successfully`);
        this.submitting.set(false);
        this.closeAddModal();
        this.currentIndex.set(0);
        this.loadProxies();
        this.loadStats();
        this.loadCountries();
      },
      error: err => {
        this.formError.set(err?.error?.error?.message ?? 'Failed to add proxy');
        this.submitting.set(false);
      },
    });
  }

  testHealth(p: Proxy, event: Event) {
    event.stopPropagation();
    this.testingProxyId.set(p.id);
    this.proxyService.triggerHealthCheck(p.id).subscribe({
      next: r => {
        this.testingProxyId.set(null);
        if (r.success) this.toast.success(`${p.host}:${p.port} is alive (${r.latencyMs}ms)`);
        else this.toast.error(`${p.host}:${p.port} is unreachable`);
        this.loadProxies();
        this.loadStats();
      },
      error: () => {
        this.testingProxyId.set(null);
        this.toast.error('Health check failed');
      },
    });
  }

  toggleStatus(p: Proxy, event: Event) {
    event.stopPropagation();
    const action$ = p.status === 'ACTIVE'
      ? this.proxyService.blacklist(p.id)
      : this.proxyService.reactivate(p.id);
    action$.subscribe({
      next: () => {
        this.toast.success(`Proxy ${p.status === 'ACTIVE' ? 'blacklisted' : 'reactivated'}`);
        this.loadProxies();
        this.loadStats();
      },
      error: () => this.toast.error('Action failed'),
    });
  }

  confirmDelete(id: string, event: Event) {
    event.stopPropagation();
    this.confirmDeleteId.set(id);
  }

  cancelDelete() {
    this.confirmDeleteId.set(null);
  }

  menuPositions: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
    { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -6 },
  ];

  toggleMenu(id: string, e: Event): void {
    e.stopPropagation();
    this.openMenuId.update(cur => cur === id ? null : id);
  }

  closeMenu(): void {
    this.openMenuId.set(null);
  }
  deleteProxy() {
    const id = this.confirmDeleteId();
    if (!id) return;
    this.proxyService.delete(id).subscribe({
      next: () => {
        this.toast.success('Proxy deleted');
        this.confirmDeleteId.set(null);
        if (this.proxies().length === 1 && this.currentIndex() > 0) {
          this.currentIndex.update(i => i - 1);
        } else {
          this.loadProxies();
        }
        this.loadStats();
        this.loadCountries();
      },
      error: () => {
        this.toast.error('Failed to delete proxy');
        this.confirmDeleteId.set(null);
      },
    });
  }

  toggleFilterPanel() {
    this.filterPanelOpen.update(v => !v);
  }

  clearFilters() {
    this.searchQuery.set('');
    this.filterStatus.set('ALL');
    this.filterProtocol.set('ALL');
    this.filterSource.set('ALL');
    this.filterCountry.set('ALL');
    this.filterAntiBotProvider.set('ANY');
    this.filterAntiBotStatus.set('ALL');
    this.currentIndex.set(0);
  }

  setSortBy(by: 'latency' | 'lastChecked' | 'successRate' | 'createdAt') {
    if (this.sortBy() === by) {
      this.sortOrder.update(o => o === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortBy.set(by);
      this.sortOrder.set('desc');
    }
    this.currentIndex.set(0);
  }

  prevPage() {
    if (this.currentIndex() > 0) this.currentIndex.update(p => p - 1);
  }

  nextPage() {
    if (this.currentIndex() < this.totalPages() - 1) this.currentIndex.update(p => p + 1);
  }


  flagEmoji(country: string | null): string {
    if (!country || country.length !== 2) return '🌐';
    const A = 0x1f1e6;
    return String.fromCodePoint(
      A + country.toUpperCase().charCodeAt(0) - 65,
      A + country.toUpperCase().charCodeAt(1) - 65
    );
  }

  successRatePct(p: Proxy): number {
    return Math.round((p.successRate ?? 0) * 100);
  }

  successTone(p: Proxy): 'high' | 'medium' | 'low' {
    const pct = this.successRatePct(p);
    if (pct >= 80) return 'high';
    if (pct >= 50) return 'medium';
    return 'low';
  }

  latencyTone(ms: number | null): 'fast' | 'medium' | 'slow' | 'unknown' {
    if (ms == null) return 'unknown';
    if (ms < 500) return 'fast';
    if (ms < 1500) return 'medium';
    return 'slow';
  }

  latencyLabel(ms: number | null): string {
    if (ms == null) return 'Unknown';
    if (ms < 500) return 'Fast';
    if (ms < 1500) return 'Medium';
    return 'Slow';
  }

  relativeTime(iso: string | null): string {
    if (!iso) return 'never';
    const diff = Date.now() - new Date(iso).getTime();
    const s = Math.floor(diff / 1000);
    if (s < 60) return `${s}s ago`;
    const m = Math.floor(s / 60);
    if (m < 60) return `${m}m ago`;
    const h = Math.floor(m / 60);
    if (h < 24) return `${h}h ago`;
    return `${Math.floor(h / 24)}d ago`;
  }

  protected readonly Math = Math;
}
