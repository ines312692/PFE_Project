import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  QueryList,
  ViewChildren,
  inject,
  signal,
} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ThemeService} from '../../core/services/theme.service';
import {AuthService} from '../../core/auth/auth.service';
import {NavbarComponent, FooterComponent} from './components';

interface Particle {
  id: number;
  x: number;
  y: number;
  size: number;
  duration: number;
  delay: number;
  color: 'blue' | 'pink' | 'purple';
}

interface FaqItem {
  q: string;
  a: string;
  open: boolean;
}

interface StatItem {
  value: string;
  suffix: string;
  label: string;
}

type FeatureIcon =
  'schema'
  | 'shield'
  | 'heal'
  | 'chart'
  | 'pipeline'
  | 'plug'
  | 'wizard'
  | 'export'
  | 'gdpr'
  | 'captcha'
  | 'vision';

interface FeatureItem {
  title: string;
  desc: string;
  tone: 'blue' | 'pink' | 'mauve' | 'green';
  icon: FeatureIcon;
}

interface ComparisonRow {
  label: string;
  us: 'yes' | 'no' | 'partial' | string;
  apify: 'yes' | 'no' | 'partial' | string;
  octoparse: 'yes' | 'no' | 'partial' | string;
  brightdata: 'yes' | 'no' | 'partial' | string;
  scrapy: 'yes' | 'no' | 'partial' | string;
}

interface PricingPlan {
  name: string;
  priceMonthly: number | null;
  tagline: string;
  ctaLabel: string;
  featured: boolean;
  features: string[];
}

type DemoMode = 'scrape' | 'crawl' | 'search' | 'extract' | 'vision';
type DemoFormat = 'markdown' | 'html' | 'json' | 'csv';
type Billing = 'monthly' | 'yearly';

const PARTICLE_COLORS = ['blue', 'pink', 'purple'] as const;
const DEMO_DELAY_MS = 2000;
const SCROLL_TOP_THRESHOLD = 400;
const COOKIE_KEY = 'elyascrape_cookie_consent';
const ANNOUNCE_KEY = 'elyascrape_announce_dismissed';
const ANNOUNCE_ID = 'self-healing-2026';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, FormsModule, NavbarComponent, FooterComponent],
  templateUrl: './landing.component.html',
  styleUrls: ['./landing.component.scss'],
})
export class LandingComponent implements OnInit, OnDestroy, AfterViewInit {

  private readonly auth = inject(AuthService);
  readonly theme = inject(ThemeService);
  readonly particles: Particle[] = Array.from({length: 22}, (_, i) => this.createParticle(i));
  readonly demoMode = signal<DemoMode>('scrape');
  readonly demoUrl = signal('example.com/products');
  readonly demoFormat = signal<DemoFormat>('json');
  readonly demoRunning = signal(false);
  readonly demoResult = signal<string | null>(null);

  readonly demoModes: { key: DemoMode; label: string }[] = [
    {key: 'scrape', label: 'Scrape'},
    {key: 'crawl', label: 'Crawl'},
    {key: 'search', label: 'Search'},
    {key: 'extract', label: 'Extract'},
    {key: 'vision', label: 'Visual LLM'},
  ];
  readonly demoFormats: DemoFormat[] = ['markdown', 'html', 'json', 'csv'];

  readonly stats: StatItem[] = [
    {value: '2.4', suffix: 'B+', label: 'Data points extracted'},
    {value: '99.2', suffix: '%', label: 'Anti-bot bypass rate'},
    {value: '120', suffix: '+', label: 'Countries covered by proxies'},
    {value: '99.99', suffix: '%', label: 'Platform uptime SLA'},
  ];

  readonly features: FeatureItem[] = [
    {
      title: 'Schema-driven extraction',
      desc: 'Define extraction with CSS, XPath, regex or let an LLM detect the structure automatically from a prompt.',
      tone: 'blue',
      icon: 'schema',
    },
    {
      title: '4-level anti-bot bypass',
      desc: 'Stealth browser, residential proxy rotation, fingerprint variation, and dedicated engines for Cloudflare and DataDome.',
      tone: 'pink',
      icon: 'shield',
    },
    {
      title: 'Self-healing scrapers',
      desc: 'When a layout shift breaks a selector, the AI detects the rupture and proposes a corrected selector — no manual patch.',
      tone: 'mauve',
      icon: 'heal',
    },
    {
      title: 'Visual LLM extraction',
      desc: 'A vision-capable LLM reads the rendered page and returns structured JSON — ideal for layouts where selectors are brittle or absent.',
      tone: 'green',
      icon: 'vision',
    },
    {
      title: 'Real-time monitoring',
      desc: 'Live metrics, structured logs, success rates, proxy health, and configurable alerts on Slack, email, or webhook.',
      tone: 'blue',
      icon: 'chart',
    },
    {
      title: 'Clean data pipeline',
      desc: 'Format normalization, dedup, validation with strict schemas, missing-value strategies, and AI-powered KPI extraction.',
      tone: 'pink',
      icon: 'pipeline',
    },
    {
      title: 'CAPTCHA solving (extensible)',
      desc: 'Plug in your own CAPTCHA-solving provider through the plugin SDK. You stay in control of which provider runs and where.',
      tone: 'mauve',
      icon: 'captcha',
    },
    {
      title: 'Plugin architecture',
      desc: 'Extend with custom extractors, transformers, and destinations through a clean plugin SDK without touching the core.',
      tone: 'blue',
      icon: 'plug',
    },
    {
      title: 'No-code wizard',
      desc: 'A point-and-click builder for non-technical users. Pick fields visually, pick a schedule, hit export. No code, no syntax.',
      tone: 'pink',
      icon: 'wizard',
    },
    {
      title: 'Multi-format export',
      desc: 'JSON, CSV, Parquet, PostgreSQL, MongoDB, REST API, or webhook. Stream straight into your stack in real time.',
      tone: 'green',
      icon: 'export',
    },
    {
      title: 'GDPR-ready by default',
      desc: 'Robots.txt respected, auto-anonymization of PII, audit trail, configurable retention. Compliance baked in — not bolted on.',
      tone: 'blue',
      icon: 'gdpr',
    },
  ];

  readonly comparison: ComparisonRow[] = [
    {
      label: 'Open source (self-hostable, no vendor lock-in)',
      us: 'yes', apify: 'no', octoparse: 'no', brightdata: 'no', scrapy: 'yes'
    },
    {
      label: 'Integrated cleaning & normalization pipeline',
      us: 'yes', apify: 'partial', octoparse: 'no', brightdata: 'partial', scrapy: 'partial'
    },
    {
      label: 'AI post-processing (KPIs, classification, anomalies)',
      us: 'yes', apify: 'partial', octoparse: 'no', brightdata: 'partial', scrapy: 'no'
    },
    {
      label: 'Self-healing selectors (AI auto-repair on layout shift)',
      us: 'yes', apify: 'no', octoparse: 'no', brightdata: 'no', scrapy: 'no'
    },
    {
      label: 'Visual LLM extraction (vision-to-JSON)',
      us: 'yes', apify: 'partial', octoparse: 'no', brightdata: 'partial', scrapy: 'no'
    },
    {
      label: 'Anti-bot difficulty scoring before launch',
      us: 'yes', apify: 'no', octoparse: 'no', brightdata: 'no', scrapy: 'no'
    },
    {
      label: 'Multi-mode extraction (CSS, XPath, Regex, LLM)',
      us: 'yes', apify: 'yes', octoparse: 'partial', brightdata: 'partial', scrapy: 'yes'
    },
    {
      label: 'JavaScript rendering (SPA, dynamic content)',
      us: 'yes', apify: 'yes', octoparse: 'yes', brightdata: 'yes', scrapy: 'partial'
    },
    {
      label: 'Anti-bot bypass (Cloudflare, DataDome, etc.)',
      us: 'yes', apify: 'yes', octoparse: 'partial', brightdata: 'yes', scrapy: 'no'
    },
    {
      label: 'CAPTCHA solving via external providers (extensible)',
      us: 'partial', apify: 'partial', octoparse: 'partial', brightdata: 'yes', scrapy: 'no'
    },
    {
      label: 'Residential proxy network',
      us: 'partial', apify: 'yes', octoparse: 'partial', brightdata: 'yes', scrapy: 'no'
    },
    {
      label: 'Distributed architecture (task queue, workers)',
      us: 'yes', apify: 'yes', octoparse: 'partial', brightdata: 'yes', scrapy: 'partial'
    },
    {
      label: 'Plugin system for custom logic',
      us: 'yes', apify: 'partial', octoparse: 'no', brightdata: 'no', scrapy: 'yes'
    },
    {
      label: 'No-code visual builder',
      us: 'yes', apify: 'partial', octoparse: 'yes', brightdata: 'partial', scrapy: 'no'
    },
    {
      label: 'Real-time monitoring & alerts',
      us: 'yes', apify: 'yes', octoparse: 'partial', brightdata: 'yes', scrapy: 'no'
    },
    {
      label: 'Multi-format export (JSON, CSV, DB, webhook)',
      us: 'yes', apify: 'yes', octoparse: 'partial', brightdata: 'yes', scrapy: 'partial'
    },
    {
      label: 'Free tier',
      us: 'Self-hosted', apify: '5k req/mo', octoparse: 'Limited', brightdata: 'Trial only', scrapy: 'Unlimited'
    },
    {
      label: 'Starting price',
      us: '€0 / €19', apify: '€39', octoparse: '$89', brightdata: '$499+', scrapy: '€0'
    },
  ];


  readonly billing = signal<Billing>('monthly');

  readonly plans: PricingPlan[] = [
    {
      name: 'Starter',
      priceMonthly: 19,
      tagline: 'For individuals and small projects getting started with structured data.',
      ctaLabel: 'Start free trial',
      featured: false,
      features: [
        '50k requests / month',
        'Up to 10 active jobs',
        'CSS, XPath, Regex & LLM extraction',
        'Shared proxy pool',
        'No-code wizard & templates',
        'Email support',
      ],
    },
    {
      name: 'Pro',
      priceMonthly: 49,
      tagline: 'Hosted, scaled, and managed. Zero infra to worry about.',
      ctaLabel: 'Start free trial',
      featured: true,
      features: [
        '500k requests / month',
        'Residential proxy pool (120+ countries)',
        'Cloudflare & DataDome bypass',
        'AI-powered & visual LLM extraction',
        'Self-healing selectors',
        'Real-time dashboard & alerts',
        'Priority support',
      ],
    },
    {
      name: 'Enterprise',
      priceMonthly: null,
      tagline: 'For teams running mission-critical data pipelines at scale.',
      ctaLabel: 'Talk to sales',
      featured: false,
      features: [
        'Unlimited requests',
        'Dedicated infrastructure',
        'Private / on-premise deployment',
        'Custom LLM integration',
        '99.99% uptime SLA',
        'SSO / SAML / SCIM',
        'Dedicated account manager',
      ],
    },
  ];

  readonly faqs = signal<FaqItem[]>([
    {
      q: 'Is web scraping legal?',
      a: 'Scraping publicly available data is generally legal, but each site has its own Terms of Service. Our platform respects robots.txt by default, rate-limits requests, and gives you the controls to stay compliant with GDPR and regional regulations.',
      open: true,
    },
    {
      q: 'How do you bypass anti-bot systems?',
      a: 'We combine four layers: browser fingerprint rotation, residential proxy pools across 120+ countries, human-like behavior simulation (mouse movement, scroll patterns), and specialized bypass engines for Cloudflare, DataDome, PerimeterX, and Akamai.',
      open: false,
    },
    {
      q: 'What extraction methods does ElyaScrape support?',
      a: 'Five modes you can mix within a single job: CSS selectors and XPath for precise targeting, regular expressions for pattern-based text, LLM-powered semantic extraction where you describe the data in plain language, and a visual LLM mode that reads the rendered page directly. An auto mode can also pick the best strategy for you.',
      open: false,
    },
    {
      q: 'How does visual LLM extraction work?',
      a: 'When a page has no stable HTML structure to target, the platform sends a rendered capture of the page to a vision-capable LLM, which returns structured JSON matching your schema. It is the most resilient mode for layouts that change often or rely heavily on dynamic rendering.',
      open: false,
    },
    {
      q: 'What happens when a website changes its structure?',
      a: 'Our AI-powered schema detection automatically adapts to minor layout changes. For major changes, you get an instant alert with a diff of what broke, and the visual editor lets you re-map fields in seconds without writing code.',
      open: false,
    },
    {
      q: 'How much does it cost?',
      a: 'Plans start at €19/month for Starter and €49/month for Pro, with custom pricing for Enterprise workloads. Every plan begins with a free trial so you can validate your pipelines before committing — no credit card required.',
      open: false,
    },
    {
      q: 'Do you store my scraped data?',
      a: 'Your data streams directly from our workers to your chosen destination (database, storage bucket, webhook). We only keep execution metadata and versioned snapshots for monitoring and debugging, which you can purge anytime.',
      open: false,
    },
  ]);

  readonly showCookies = signal(false);
  readonly showAnnounce = signal(true);
  readonly showScrollTop = signal(false);
  private scrollEl: HTMLElement | null = null;
  private readonly scrollHandler = () => this.onScroll();
  @ViewChildren('reveal') revealEls?: QueryList<ElementRef<HTMLElement>>;
  private revealObserver?: IntersectionObserver;

  ngOnInit(): void {
    try {
      const stored = localStorage.getItem(COOKIE_KEY);
      if (!stored) {
        setTimeout(() => this.showCookies.set(true), 800);
      }
    } catch {
      this.showCookies.set(true);
    }

    try {
      if (localStorage.getItem(ANNOUNCE_KEY) === ANNOUNCE_ID) {
        this.showAnnounce.set(false);
      }
    } catch { /* keep banner visible */
    }
  }

  ngAfterViewInit(): void {
    this.setupRevealObserver();
    this.setupScrollListener();
  }

  ngOnDestroy(): void {
    this.revealObserver?.disconnect();
    this.teardownScrollListener();
  }


  scrollToNext(): void {
    const trust = document.querySelector('.trust') as HTMLElement;
    trust?.scrollIntoView({behavior: 'smooth'});
  }

  scrollToTop(): void {
    if (this.scrollEl) {
      this.scrollEl.scrollTo({top: 0, behavior: 'smooth'});
    } else {
      window.scrollTo({top: 0, behavior: 'smooth'});
    }
  }

  scrollToFeatures(event: Event): void {
    event.preventDefault();
    document.getElementById('features')?.scrollIntoView({behavior: 'smooth'});
  }

  setDemoMode(mode: DemoMode): void {
    this.demoMode.set(mode);
    this.demoResult.set(null);
  }

  runDemo(): void {
    this.demoRunning.set(true);
    this.demoResult.set(null);
    setTimeout(() => {
      this.demoRunning.set(false);
      this.demoResult.set(this.buildDemoResult(this.demoUrl(), this.demoFormat()));
    }, DEMO_DELAY_MS);
  }

  toggleFaq(index: number): void {
    this.faqs.update(items =>
      items.map((it, i) => ({...it, open: i === index ? !it.open : it.open})),
    );
  }

  goToLogin(): void {
    this.auth.login();
  }

  goToRegister(): void {
    this.auth.register();
  }

  acceptCookies(): void {
    try {
      localStorage.setItem(COOKIE_KEY, 'accepted');
    } catch { /* noop */
    }
    this.showCookies.set(false);
  }

  rejectCookies(): void {
    try {
      localStorage.setItem(COOKIE_KEY, 'rejected');
    } catch { /* noop */
    }
    this.showCookies.set(false);
  }

  dismissAnnounce(): void {
    try {
      localStorage.setItem(ANNOUNCE_KEY, ANNOUNCE_ID);
    } catch { /* noop */
    }
    this.showAnnounce.set(false);
  }


  private setupScrollListener(): void {
    this.scrollEl =
      document.querySelector<HTMLElement>('.app-content--landing') ??
      document.querySelector<HTMLElement>('.app-content') ??
      null;

    if (this.scrollEl) {
      this.scrollEl.addEventListener('scroll', this.scrollHandler, {passive: true});
    }
    window.addEventListener('scroll', this.scrollHandler, {passive: true, capture: true});
  }

  private teardownScrollListener(): void {
    this.scrollEl?.removeEventListener('scroll', this.scrollHandler);
    window.removeEventListener('scroll', this.scrollHandler, {capture: true} as EventListenerOptions);
  }

  private onScroll(): void {
    const top = this.scrollEl ? this.scrollEl.scrollTop : window.scrollY;
    this.showScrollTop.set(top > SCROLL_TOP_THRESHOLD);
  }

  private createParticle(id: number): Particle {
    return {
      id,
      x: Math.random() * 100,
      y: Math.random() * 100,
      size: Math.random() * 4 + 2,
      duration: Math.random() * 15 + 10,
      delay: Math.random() * 5,
      color: PARTICLE_COLORS[Math.floor(Math.random() * PARTICLE_COLORS.length)],
    };
  }

  private buildDemoResult(url: string, format: DemoFormat): string {
    const builders: Record<DemoFormat, () => string> = {
      json: () => this.buildJsonResult(url),
      csv: () => this.buildCsvResult(url),
      html: () => this.buildHtmlResult(),
      markdown: () => this.buildMarkdownResult(url),
    };
    return builders[format]();
  }

  private buildJsonResult(url: string): string {
    return JSON.stringify([
      {title: 'Product Alpha', price: 29.99, currency: 'EUR', in_stock: true, url: `https://${url}/alpha`},
      {title: 'Product Beta', price: 49.50, currency: 'EUR', in_stock: true, url: `https://${url}/beta`},
      {title: 'Product Gamma', price: 19.00, currency: 'EUR', in_stock: false, url: `https://${url}/gamma`},
    ], null, 2);
  }

  private buildCsvResult(url: string): string {
    return [
      'title,price,currency,in_stock,url',
      `Product Alpha,29.99,EUR,true,https://${url}/alpha`,
      `Product Beta,49.50,EUR,true,https://${url}/beta`,
      `Product Gamma,19.00,EUR,false,https://${url}/gamma`,
    ].join('\n');
  }

  private buildHtmlResult(): string {
    const products = [
      {name: 'Product Alpha', price: '€29.99', stock: 'In Stock', stockClass: 'in-stock'},
      {name: 'Product Beta', price: '€49.50', stock: 'In Stock', stockClass: 'in-stock'},
      {name: 'Product Gamma', price: '€19.00', stock: 'Out of Stock', stockClass: 'out-stock'},
    ];
    return products.map(p =>
      `<div class="product">\n` +
      `  <h2>${p.name}</h2>\n` +
      `  <span class="price">${p.price}</span>\n` +
      `  <span class="stock ${p.stockClass}">${p.stock}</span>\n` +
      `</div>`,
    ).join('\n');
  }

  private buildMarkdownResult(url: string): string {
    const products = [
      {name: 'Product Alpha', price: '€29.99', stock: 'In Stock', path: 'alpha'},
      {name: 'Product Beta', price: '€49.50', stock: 'In Stock', path: 'beta'},
      {name: 'Product Gamma', price: '€19.00', stock: 'Out of Stock', path: 'gamma'},
    ];
    const sections = products.map(p =>
      `## ${p.name}\n` +
      `- **Price:** ${p.price}\n` +
      `- **Status:** ${p.stock}\n` +
      `- **URL:** https://${url}/${p.path}`,
    );
    return `# Extraction Results\n\n${sections.join('\n\n')}`;
  }

  private setupRevealObserver(): void {
    if (typeof IntersectionObserver === 'undefined') {
      this.revealEls?.forEach(ref => ref.nativeElement.classList.add('is-revealed'));
      return;
    }

    this.revealObserver = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-revealed');
          this.revealObserver?.unobserve(entry.target);
        }
      }
    }, {threshold: 0.12, rootMargin: '0px 0px -60px 0px'});

    this.revealEls?.forEach(ref => {
      const el = ref.nativeElement;
      const rect = el.getBoundingClientRect();
      if (rect.top < window.innerHeight && rect.bottom > 0) {
        el.classList.add('is-revealed');
      } else {
        this.revealObserver?.observe(el);
      }
    });
  }
}
