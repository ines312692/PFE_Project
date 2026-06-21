import {
  Component,
  OnInit,
  AfterViewInit,
  ElementRef,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {HttpClient} from '@angular/common/http';
import {DomSanitizer, SafeHtml} from '@angular/platform-browser';

import {ProxyService} from '../../../core/services/proxy.service';
import {
  AntiBotProvider,
  ProtectionStatus,
  ProxyDashboard,
  ProxyProtocol,
  ProxySource,
} from '../../../core/models/proxy.model';
import {
  ISO2_TO_ISO3,
  ISO2_TO_NAME,
  SOURCE_COLORS,
  SOURCE_LABELS,
} from '../../../core/constants/proxy-dashboard.constants';

interface DonutSegment {
  label: string;
  count: number;
  color: string;
  pct: number;
  offset: number;
}

interface LatencyBar {
  tone: 'fast' | 'medium' | 'slow' | 'unknown';
  label: string;
  count: number;
  pct: number;
}

interface ProtocolBar {
  label: ProxyProtocol;
  count: number;
}

interface CountryRow {
  code: string;
  count: number;
  pct: number;
}

interface MatrixCell {
  status: ProtectionStatus;
  count: number;
  pct: number;
}

interface MatrixRow {
  provider: AntiBotProvider;
  total: number;
  cells: MatrixCell[];
}

@Component({
  selector: 'app-proxy-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './proxy-dashboard.component.html',
  styleUrls: ['./proxy-dashboard.component.scss'],
})
export class ProxyDashboardComponent implements OnInit, AfterViewInit {
  private readonly proxyService = inject(ProxyService);
  private readonly router = inject(Router);
  private readonly http = inject(HttpClient);
  private readonly sanitizer = inject(DomSanitizer);

  readonly data = signal<ProxyDashboard | null>(null);
  readonly loading = signal<boolean>(false);
  readonly refreshing = signal<boolean>(false);
  readonly hoveredCountry = signal<CountryRow | null>(null);
  readonly svgContent = signal<SafeHtml>('');

  @ViewChild('mapHost') mapHost?: ElementRef<HTMLDivElement>;

  private svgRoot: SVGElement | null = null;
  private highlightedPaths: SVGPathElement[] = [];
  private mapInitialized = false;

  readonly matrixStatuses: ProtectionStatus[] = [
    'CLEAN',
    'CHALLENGED',
    'BLOCKED',
    'UNREACHABLE',
    'UNTESTED',
  ];

  private readonly ISO2_TO_ISO3 = ISO2_TO_ISO3;
  private readonly ISO2_TO_NAME = ISO2_TO_NAME;
  private readonly SOURCE_COLORS = SOURCE_COLORS;
  private readonly SOURCE_LABELS = SOURCE_LABELS;

  ngOnInit(): void {
    this.load(true);
  }

  ngAfterViewInit(): void {
    this.loadWorldMap();
  }

  refresh(): void {
    if (this.refreshing()) return;
    this.load(false);
  }

  private load(initial: boolean): void {
    if (initial) this.loading.set(true);
    else this.refreshing.set(true);

    this.proxyService.getDashboard().subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
        this.refreshing.set(false);
        if (this.mapInitialized) {
          this.applyCountryColors();
        }
      },
      error: (err) => {
        console.error('[ProxyDashboard] failed to load', err);
        this.loading.set(false);
        this.refreshing.set(false);
      },
    });
  }

  goToList(): void {
    this.router.navigate(['/admin/proxies/list']);
  }

  private loadWorldMap(): void {
    this.http.get('assets/world.svg', {responseType: 'text'}).subscribe({
      next: (raw) => {
        this.svgContent.set(this.sanitizer.bypassSecurityTrustHtml(raw));
        setTimeout(() => this.setupMap(), 0);
      },
      error: (err) => {
        console.error('[ProxyDashboard] world.svg failed to load', err);
      },
    });
  }

  private setupMap(): void {
    const host = this.mapHost?.nativeElement;
    if (!host) return;

    const svg = host.querySelector('svg') as SVGElement | null;
    if (!svg) return;

    svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
    svg.setAttribute('width', '100%');
    svg.setAttribute('height', '100%');
    svg.style.width = '100%';
    svg.style.height = '100%';
    svg.style.display = 'block';

    this.svgRoot = svg;
    this.mapInitialized = true;
    this.applyCountryColors();
  }

  private getCountryPaths(code: string): SVGPathElement[] {
    if (!this.svgRoot || !code) return [];
    const iso2 = code.toUpperCase();
    const iso3 = this.ISO2_TO_ISO3[iso2];
    const name = this.ISO2_TO_NAME[iso2];

    const codeVariants = new Set<string>();
    [iso2, iso2.toLowerCase()].forEach((v) => codeVariants.add(v));
    if (iso3) [iso3, iso3.toLowerCase()].forEach((v) => codeVariants.add(v));

    const found = new Set<SVGPathElement>();

    for (const v of codeVariants) {
      this.svgRoot
        .querySelectorAll<SVGPathElement>(
          `path[id="${v}"], path[class~="${v}"], path[data-id="${v}"], path[name="${v}"]`,
        )
        .forEach((p) => found.add(p));
    }

    if (name) {
      const escaped = this.cssEscape(name);
      this.svgRoot
        .querySelectorAll<SVGPathElement>(
          `path[class="${escaped}"], path[name="${escaped}"], path[data-name="${escaped}"]`,
        )
        .forEach((p) => found.add(p));

      this.svgRoot
        .querySelectorAll<SVGPathElement>('path[class]')
        .forEach((p) => {
          const cls = p.getAttribute('class') || '';
          if (cls.split(/\s+/).includes(name) || cls === name) {
            found.add(p);
          }
        });
    }

    return Array.from(found);
  }

  private cssEscape(value: string): string {
    return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  }


  private applyCountryColors(): void {
    if (!this.svgRoot) return;

    this.svgRoot.querySelectorAll<SVGPathElement>('path').forEach((p) => {
      p.classList.remove('has-data');
      p.style.removeProperty('--intensity');
    });

    const d = this.data();
    if (!d?.topCountries) return;

    const counts = Object.values(d.topCountries);
    if (counts.length === 0) return;
    const max = Math.max(...counts, 1);

    for (const [code, count] of Object.entries(d.topCountries)) {
      const paths = this.getCountryPaths(code);
      if (paths.length === 0) continue;
      const visual = Math.max(0.15, Math.pow(count / max, 0.7));
      paths.forEach((p) => {
        p.classList.add('has-data');
        p.style.setProperty('--intensity', visual.toString());
      });
    }
  }

  private highlightCountryByCode(code: string): void {
    this.unhighlightPaths();
    const paths = this.getCountryPaths(code);
    paths.forEach((p) => p.classList.add('is-hovered'));
    this.highlightedPaths = paths;
  }

  private unhighlightPaths(): void {
    this.highlightedPaths.forEach((p) => p.classList.remove('is-hovered'));
    this.highlightedPaths = [];
  }

  setHoveredCountry(c: CountryRow | null): void {
    this.hoveredCountry.set(c);
    if (!this.svgRoot) return;
    if (c) this.highlightCountryByCode(c.code);
    else this.unhighlightPaths();
  }


  readonly successPct = computed<number | null>(() => {
    const v = this.data()?.avgSuccessRate;
    return v == null ? null : Math.round(v * 100);
  });

  readonly coveragePct = computed<number>(() => {
    const d = this.data();
    if (!d || d.total === 0) return 0;
    return Math.round((d.antiBotTestedCount / d.total) * 100);
  });

  readonly healthGaugeClass = computed<string>(() => {
    const p = this.successPct();
    if (p == null) return 'gauge__ring--unknown';
    if (p >= 75) return 'gauge__ring--success';
    if (p >= 40) return 'gauge__ring--warn';
    return 'gauge__ring--danger';
  });

  readonly poolHealthTier = computed<'healthy' | 'degraded' | 'critical'>(() => {
    const d = this.data();
    if (!d || d.total === 0) return 'critical';
    const ratio = d.active / d.total;
    if (ratio >= 0.7) return 'healthy';
    if (ratio >= 0.4) return 'degraded';
    return 'critical';
  });

  readonly poolHealthLabel = computed<string>(() => {
    const t = this.poolHealthTier();
    return t === 'healthy' ? 'Healthy' : t === 'degraded' ? 'Degraded' : 'Critical';
  });

  readonly statusDonut = computed<DonutSegment[]>(() => {
    const d = this.data();
    if (!d || d.total === 0) return [];

    const items = [
      {label: 'Active', count: d.active, color: 'var(--elyadata-green)'},
      {label: 'Inactive', count: d.inactive, color: 'var(--color-text-faint)'},
      {label: 'Blacklisted', count: d.blacklisted, color: 'var(--elyadata-pink)'},
    ].filter((i) => i.count > 0);

    let offset = 0;
    return items.map((i) => {
      const pct = (i.count / d.total) * 100;
      const seg: DonutSegment = {...i, pct, offset};
      offset += pct;
      return seg;
    });
  });

  readonly sourceTotal = computed<number>(() => {
    const d = this.data();
    if (!d?.countBySource) return 0;
    return Object.values(d.countBySource).reduce((a, b) => a + (b ?? 0), 0);
  });

  readonly sourceDonut = computed<DonutSegment[]>(() => {
    const d = this.data();
    if (!d?.countBySource) return [];
    const total = this.sourceTotal() || 1;

    const entries = (Object.entries(d.countBySource) as [ProxySource, number][])
      .filter(([, v]) => v > 0)
      .sort((a, b) => b[1] - a[1]);

    let offset = 0;
    return entries.map(([key, count]) => {
      const pct = (count / total) * 100;
      const seg: DonutSegment = {
        label: this.SOURCE_LABELS[key] ?? key,
        count,
        color: this.SOURCE_COLORS[key] ?? 'var(--color-text-muted)',
        pct,
        offset,
      };
      offset += pct;
      return seg;
    });
  });

  readonly protocolBars = computed<ProtocolBar[]>(() => {
    const d = this.data();
    if (!d?.countByProtocol) return [];
    return (Object.entries(d.countByProtocol) as [ProxyProtocol, number][])
      .filter(([, v]) => v > 0)
      .map(([label, count]) => ({label, count}));
  });

  readonly latencyBars = computed<LatencyBar[]>(() => {
    const d = this.data();
    if (!d?.latencyBuckets) return [];
    const b = d.latencyBuckets;
    const total = b.fast + b.medium + b.slow + b.unknown || 1;

    return [
      {tone: 'fast' as const, label: 'Fast', count: b.fast, pct: (b.fast / total) * 100},
      {tone: 'medium' as const, label: 'Medium', count: b.medium, pct: (b.medium / total) * 100},
      {tone: 'slow' as const, label: 'Slow', count: b.slow, pct: (b.slow / total) * 100},
      {tone: 'unknown' as const, label: 'Untested', count: b.unknown, pct: (b.unknown / total) * 100},
    ];
  });

  readonly latencyTotal = computed<number>(() => {
    const b = this.data()?.latencyBuckets;
    if (!b) return 0;
    return b.fast + b.medium + b.slow + b.unknown;
  });

  readonly maxLatencyCount = computed<number>(() => {
    const bars = this.latencyBars();
    return bars.reduce((m, b) => Math.max(m, b.count), 1);
  });

  readonly topCountriesArray = computed<CountryRow[]>(() => {
    const d = this.data();
    if (!d?.topCountries) return [];

    const counts = Object.values(d.topCountries);
    if (counts.length === 0) return [];
    const max = Math.max(...counts, 1);

    return Object.entries(d.topCountries)
      .map(([code, count]) => ({code, count, pct: (count / max) * 100}))
      .sort((a, b) => b.count - a.count)
      .slice(0, 8);
  });

  readonly matrixRows = computed<MatrixRow[]>(() => {
    const d = this.data();
    if (!d?.antiBotMatrix) return [];

    return (Object.entries(d.antiBotMatrix) as [
      AntiBotProvider,
      Partial<Record<ProtectionStatus, number>>,
    ][])
      .map(([provider, byStatus]) => {
        const total = Object.values(byStatus).reduce((a, b) => a + (b ?? 0), 0);
        const cells: MatrixCell[] = this.matrixStatuses.map((status) => {
          const count = byStatus[status] ?? 0;
          return {status, count, pct: total > 0 ? (count / total) * 100 : 0};
        });
        return {provider, total, cells};
      })
      .filter((r) => r.total > 0);
  });

  barH(value: number, max: number): number {
    if (!max || !value) return 4;
    return Math.max(8, (value / max) * 100);
  }

  cellTone(s: ProtectionStatus): 'success' | 'warning' | 'danger' | 'muted' {
    switch (s) {
      case 'CLEAN':
        return 'success';
      case 'CHALLENGED':
        return 'warning';
      case 'BLOCKED':
      case 'UNREACHABLE':
        return 'danger';
      default:
        return 'muted';
    }
  }

  formatProvider(p: AntiBotProvider): string {
    return p.replace(/_/g, ' ');
  }

  flagEmoji(code: string | null | undefined): string {
    if (!code || code.length !== 2) return '🌐';
    const A = 0x1f1e6;
    const upper = code.toUpperCase();
    return String.fromCodePoint(
      A + upper.charCodeAt(0) - 65,
      A + upper.charCodeAt(1) - 65,
    );
  }

  countryName(code: string): string {
    try {
      return new Intl.DisplayNames(['en'], {type: 'region'}).of(code.toUpperCase()) ?? code;
    } catch {
      return code;
    }
  }

  relativeTime(iso: string | null | undefined): string {
    if (!iso) return '—';
    const t = new Date(iso).getTime();
    if (isNaN(t)) return '—';

    const diff = Date.now() - t;
    const s = Math.floor(diff / 1000);
    if (s < 30) return 'just now';
    if (s < 60) return `${s}s ago`;
    const m = Math.floor(s / 60);
    if (m < 60) return `${m}m ago`;
    const h = Math.floor(m / 60);
    if (h < 24) return `${h}h ago`;
    const days = Math.floor(h / 24);
    if (days < 30) return `${days}d ago`;
    const months = Math.floor(days / 30);
    if (months < 12) return `${months}mo ago`;
    return `${Math.floor(months / 12)}y ago`;
  }
}
