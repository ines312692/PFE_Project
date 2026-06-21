import {
  AfterViewInit, Component, ElementRef, OnDestroy, OnInit,
  ViewChild, computed, effect, inject, signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { JobService } from '../../core/services/job.service';
import { Subscription, interval } from 'rxjs';
import { COLORS } from '../../shared/constants/colors';

type ActionKind = 'completed' | 'failed' | 'launched' | 'paused' | 'created' | 'queued' | 'cancelled';

interface ActivityItem {
  id: string;
  name: string;
  action: ActionKind;
  when: string;
  type: 'SCRAPER' | 'CRAWLER';
  url: string;
}

interface WeekDay {
  label: string;
  jobs: number;
  failed: number;
  isToday: boolean;
}

interface ChartGroup {
  label: string;
  pink: number;
  blue: number;
  section: 'priority' | 'trigger' | 'extraction';
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
})
export class DashboardComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('svgRef') private svgRef!: ElementRef<SVGSVGElement>;
  @ViewChild('barsRef') private barsRef!: ElementRef<HTMLDivElement>;

  private router = inject(Router);
  private jobSvc = inject(JobService);
  private tick$: Subscription | null = null;
  private ro?: ResizeObserver;

  constructor() {
    effect(() => {
      this.chartGroups();
      this.chartMax();
      setTimeout(() => this.drawLines(), 0);
    });
  }

  jobs = this.jobSvc.jobs;
  loading = signal(true);
  now = signal(new Date());

  // ── Counts ──────────────────────────────────────────────
  totalJobs = computed(() => this.jobs().length);
  runningJobs = computed(() => this.jobs().filter(j => j.status === 'RUNNING').length);
  completedJobs = computed(() => this.jobs().filter(j => j.status === 'COMPLETED').length);
  failedJobs = computed(() => this.jobs().filter(j => j.status === 'FAILED').length);
  queuedJobs = computed(() => this.jobs().filter(j => j.status === 'QUEUED').length);
  pausedJobs = computed(() => this.jobs().filter(j => j.status === 'PAUSED').length);
  createdJobs = computed(() => this.jobs().filter(j => j.status === 'CREATED').length);

  scraperCount = computed(() => this.jobs().filter(j => j.jobType === 'SCRAPER').length);
  crawlerCount = computed(() => this.jobs().filter(j => j.jobType === 'CRAWLER').length);

  scheduledCount = computed(() => this.jobs().filter(j => j.triggerType === 'SCHEDULED').length);
  manualCount = computed(() => this.jobs().filter(j => j.triggerType === 'MANUAL').length);
  webhookCount = computed(() => this.jobs().filter(j => j.triggerType === 'WEBHOOK').length);

  normalCount = computed(() => this.jobs().filter(j => j.priority === 'NORMAL').length);
  highCount = computed(() => this.jobs().filter(j => j.priority === 'HIGH').length);
  criticalCount = computed(() => this.jobs().filter(j => j.priority === 'CRITICAL').length);
  lowCount = computed(() => this.jobs().filter(j => j.priority === 'LOW').length);

  autoCount = computed(() => this.jobs().filter(j => j.extractionMode === 'AUTO').length);
  cssCount = computed(() => this.jobs().filter(j => j.extractionMode === 'CSS_SELECTOR').length);

  // ── Today / Active stats (NEW) ──────────────────────────
  activeNow = computed(() => this.runningJobs() + this.queuedJobs());

  jobsToday = computed(() => {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return this.jobs().filter(j => new Date(j.createdAt) >= today).length;
  });

  // ── Breakdown chart ─────────────────────────────────────
  readonly chartGroups = computed<ChartGroup[]>(() => [
    { label: 'NORMAL', pink: this.normalCount(), blue: this.highCount(), section: 'priority' },
    { label: 'CRITICAL', pink: this.criticalCount(), blue: this.lowCount(), section: 'priority' },
    { label: 'Manual', pink: this.manualCount(), blue: 0, section: 'trigger' },
    { label: 'Sched.', pink: this.scheduledCount(), blue: 0, section: 'trigger' },
    { label: 'Webhook', pink: this.webhookCount(), blue: 0, section: 'trigger' },
    { label: 'AUTO', pink: this.autoCount(), blue: 0, section: 'extraction' },
    { label: 'CSS', pink: this.cssCount(), blue: 0, section: 'extraction' },
  ]);

  readonly chartMax = computed(() => {
    const vals = this.chartGroups().flatMap(g => [g.pink, g.blue]);
    return Math.ceil(Math.max(5, ...vals) / 5) * 5;
  });

  readonly yTicks = computed(() => {
    const max = this.chartMax();
    const step = max / 4;
    return [max, max - step, max - step * 2, max - step * 3, 0].map(Math.round);
  });

  pct(val: number): number {
    return Math.max(val / this.chartMax() * 100, val > 0 ? 2 : 0);
  }

  private drawLines(): void {
    const svg = this.svgRef?.nativeElement;
    const barsEl = this.barsRef?.nativeElement;
    if (!svg || !barsEl) return;

    const svgRect = svg.getBoundingClientRect();
    if (svgRect.width === 0 || svgRect.height === 0) return;

    const groups = barsEl.querySelectorAll<HTMLElement>('.bc-group');
    const max = this.chartMax();
    const h = svgRect.height;
    const data = this.chartGroups();

    const pinkPts: [number, number][] = [];
    const bluePts: [number, number][] = [];

    groups.forEach((g, i) => {
      const gr = g.getBoundingClientRect();
      const cx = gr.left + gr.width / 2 - svgRect.left;
      const d = data[i];
      if (!d) return;
      pinkPts.push([cx, h * (1 - d.pink / max)]);
      bluePts.push([cx, h * (1 - d.blue / max)]);
    });

    const smooth = (pts: [number, number][]): string => {
      if (pts.length < 2) return '';
      let path = `M ${pts[0][0].toFixed(1)} ${pts[0][1].toFixed(1)}`;
      for (let i = 1; i < pts.length; i++) {
        const [x0, y0] = pts[i - 1];
        const [x1, y1] = pts[i];
        const cpx = ((x0 + x1) / 2).toFixed(1);
        path += ` C ${cpx} ${y0.toFixed(1)} ${cpx} ${y1.toFixed(1)} ${x1.toFixed(1)} ${y1.toFixed(1)}`;
      }
      return path;
    };

    const ns = 'http://www.w3.org/2000/svg';
    svg.innerHTML = '';

    const addPath = (pts: [number, number][], stroke: string, dash = '', opacity = '1') => {
      const p = document.createElementNS(ns, 'path');
      p.setAttribute('d', smooth(pts));
      p.setAttribute('fill', 'none');
      p.setAttribute('stroke', stroke);
      p.setAttribute('stroke-width', '1.5');
      p.setAttribute('stroke-linecap', 'round');
      p.setAttribute('stroke-linejoin', 'round');
      p.setAttribute('opacity', opacity);
      if (dash) p.setAttribute('stroke-dasharray', dash);
      svg.appendChild(p);
    };

    const addDot = (x: number, y: number, r: number, fill: string, opacity = '1') => {
      const c = document.createElementNS(ns, 'circle');
      c.setAttribute('cx', x.toFixed(1));
      c.setAttribute('cy', y.toFixed(1));
      c.setAttribute('r', String(r));
      c.setAttribute('fill', fill);
      c.setAttribute('opacity', opacity);
      svg.appendChild(c);
    };

    addPath(pinkPts, COLORS.chart.pink);
    addPath(bluePts, COLORS.chart.blue, '4 3', '0.65');

    pinkPts.forEach(([x, y]) => addDot(x, y, 3, COLORS.chart.pink));
    bluePts.forEach(([x, y]) => addDot(x, y, 2.5, COLORS.chart.blue, '0.7'));
  }

  // ── Health / Success ────────────────────────────────────
  successRate = computed(() => {
    const d = this.completedJobs() + this.failedJobs();
    return d > 0 ? Math.round((this.completedJobs() / d) * 100) : 0;
  });

  activeRatio = computed(() => {
    const t = this.totalJobs() || 1;
    return Math.round(((this.runningJobs() + this.queuedJobs()) / t) * 100);
  });

  platformHealth = computed(() => {
    if (this.totalJobs() === 0) return 0;
    const sr = this.successRate() * 0.55;
    const ar = Math.min(25, this.activeRatio() * 0.5);
    const pen = Math.min(20, this.failedJobs() * 2);
    return Math.min(100, Math.max(0, Math.round(sr + ar + (20 - pen))));
  });

  healthLabel = computed(() => {
    const h = this.platformHealth();
    if (h >= 85) return 'Healthy';
    if (h >= 60) return 'Degraded';
    if (h >= 30) return 'Warning';
    return 'Critical';
  });

  healthTier = computed(() => {
    const h = this.platformHealth();
    if (h >= 85) return 'healthy';
    if (h >= 60) return 'degraded';
    if (h >= 30) return 'warning';
    return 'critical';
  });

  // ── Gauge stroke class (no inline colors) ───────────────
  successGaugeClass = computed(() => {
    const r = this.successRate();
    if (r >= 80) return 'gauge__ring--success';
    if (r >= 50) return 'gauge__ring--warn';
    return 'gauge__ring--danger';
  });

  healthGaugeClass = computed(() => {
    const t = this.healthTier();
    if (t === 'healthy') return 'gauge__ring--success';
    if (t === 'critical') return 'gauge__ring--danger';
    return 'gauge__ring--warn';
  });

  // ── Weekly bars ─────────────────────────────────────────
  weeklyData = computed<WeekDay[]>(() => {
    const LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const out: WeekDay[] = [];
    const base = new Date();
    base.setHours(0, 0, 0, 0);
    for (let i = 6; i >= 0; i--) {
      const d = new Date(base);
      d.setDate(base.getDate() - i);
      const ds = d.toDateString();
      const day = this.jobs().filter(j => new Date(j.createdAt).toDateString() === ds);
      out.push({
        label: LABELS[d.getDay()],
        jobs: day.length,
        failed: day.filter(j => j.status === 'FAILED').length,
        isToday: i === 0,
      });
    }
    return out;
  });

  maxWeeklyJobs = computed(() => Math.max(1, ...this.weeklyData().map(d => d.jobs)));
  weekTotal = computed(() => this.weeklyData().reduce((s, d) => s + d.jobs, 0));
  weekFailed = computed(() => this.weeklyData().reduce((s, d) => s + d.failed, 0));

  statusSlices = computed(() => {
    const t = this.totalJobs() || 1;
    return [
      { label: 'Completed', count: this.completedJobs(), color: COLORS.status.completed, tier: 'completed' },
      { label: 'Running',   count: this.runningJobs(),   color: COLORS.status.running,   tier: 'running' },
      { label: 'Failed',    count: this.failedJobs(),    color: COLORS.status.failed,    tier: 'failed' },
      { label: 'Queued',    count: this.queuedJobs(),    color: COLORS.status.queued,    tier: 'queued' },
      { label: 'Paused',    count: this.pausedJobs(),    color: COLORS.status.paused,    tier: 'paused' },
      { label: 'Created',   count: this.createdJobs(),   color: COLORS.status.created,   tier: 'created' },
    ]
      .filter(s => s.count > 0)
      .map(s => ({ ...s, pct: (s.count / t) * 100 }));
  });

  donutSegments = computed(() => {
    let offset = 0;
    return this.statusSlices().map(s => {
      const seg = { ...s, offset };
      offset += s.pct;
      return seg;
    });
  });

  recentActivity = computed<ActivityItem[]>(() =>
    [...this.jobs()]
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .slice(0, 8)
      .map(j => ({
        id: j.id,
        name: j.name,
        action: this.toAction(j.status),
        when: this.ago(new Date(j.updatedAt)),
        type: j.jobType,
        url: j.targetUrl,
      }))
  );

  // ── Lifecycle ───────────────────────────────────────────
  ngOnInit(): void {
    this.jobSvc.getAll().subscribe({
      next: () => this.loading.set(false),
      error: () => this.loading.set(false),
    });
    this.tick$ = interval(30_000).subscribe(() => this.now.set(new Date()));
  }

  ngAfterViewInit(): void {
    this.drawLines();
    this.ro = new ResizeObserver(() => this.drawLines());
    this.ro.observe(this.barsRef.nativeElement);
  }

  ngOnDestroy(): void {
    this.tick$?.unsubscribe();
    this.ro?.disconnect();
  }

  // ── Helpers ─────────────────────────────────────────────
  barH(val: number, max: number): number {
    return max > 0 ? Math.max(3, Math.round((val / max) * 100)) : 3;
  }

  private toAction(status: string): ActionKind {
    const m: Record<string, ActionKind> = {
      COMPLETED: 'completed', FAILED: 'failed',
      RUNNING: 'launched', PAUSED: 'paused',
      CREATED: 'created', QUEUED: 'queued',
      CANCELLED: 'cancelled',
    };
    return m[status] ?? 'created';
  }

  private ago(d: Date): string {
    const s = Math.floor((Date.now() - d.getTime()) / 1000);
    if (s < 60) return `${s}s`;
    if (s < 3600) return `${Math.floor(s / 60)}m`;
    if (s < 86400) return `${Math.floor(s / 3600)}h`;
    return `${Math.floor(s / 86400)}d`;
  }

  goToJobs(): void { this.router.navigate(['/jobs']); }
  goToCreate(): void { this.router.navigate(['/jobs/create']); }
  goToJob(id: string): void { this.router.navigate(['/jobs', id]); }
}
