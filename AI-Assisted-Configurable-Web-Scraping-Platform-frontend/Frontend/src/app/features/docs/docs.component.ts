import { Component, signal, computed, inject } from '@angular/core';
import { CommonModule }                        from '@angular/common';
import { RouterModule }                        from '@angular/router';
import { ThemeService }                        from '../../core/services/theme.service';


export interface DocPage {
  id:       string;
  label:    string;
  section:  string;
}

export interface DocSection {
  title: string;
  pages: DocPage[];
}

@Component({
  selector:    'app-docs',
  standalone:  true,
  imports:     [CommonModule, RouterModule],
  templateUrl: './docs.component.html',
  styleUrls:   ['./docs.component.scss'],
})
export class DocsComponent {
  readonly theme = inject(ThemeService);

  readonly sections: DocSection[] = [
    {
      title: 'Getting started',
      pages: [
        { id: 'introduction',   label: 'Introduction',    section: 'Getting started' },
        { id: 'quick-start',    label: 'Quick start',     section: 'Getting started' },
        { id: 'authentication', label: 'Authentication',  section: 'Getting started' },
      ],
    },
    {
      title: 'Core concepts',
      pages: [
        { id: 'jobs',           label: 'Jobs & pipelines',   section: 'Core concepts' },
        { id: 'job-types',      label: 'Scraper vs Crawler', section: 'Core concepts' },
        { id: 'extraction',     label: 'Extraction modes',   section: 'Core concepts' },
        { id: 'browser-engine', label: 'Browser engine',     section: 'Core concepts' },
      ],
    },
    {
      title: 'Crawling & site analysis',
      pages: [
        { id: 'site-analysis',  label: 'Site analysis', section: 'Crawling & site analysis' },
        { id: 'crawling',       label: 'Crawling',      section: 'Crawling & site analysis' },
        { id: 'pagination',     label: 'Pagination',    section: 'Crawling & site analysis' },
      ],
    },
    {
      title: 'Anti-bot & stealth',
      pages: [
        { id: 'anti-bot', label: 'Anti-bot & stealth', section: 'Anti-bot & stealth' },
        { id: 'proxy',    label: 'Proxy rotation',     section: 'Anti-bot & stealth' },
        { id: 'captcha',  label: 'CAPTCHA solving',    section: 'Anti-bot & stealth' },
      ],
    },
    {
      title: 'Configuration',
      pages: [
        { id: 'browser-config', label: 'Browser & network', section: 'Configuration' },
        { id: 'retry-policy',   label: 'Retry policy',      section: 'Configuration' },
        { id: 'scheduling',     label: 'Scheduling',        section: 'Configuration' },
      ],
    },
    {
      title: 'Data pipeline',
      pages: [
        { id: 'schemas',          label: 'Extraction schemas',    section: 'Data pipeline' },
        { id: 'css-xpath',        label: 'CSS / XPath selectors', section: 'Data pipeline' },
        { id: 'selector-healing', label: 'Selector auto-healing', section: 'Data pipeline' },
        { id: 'llm-extraction',   label: 'LLM extraction',        section: 'Data pipeline' },
        { id: 'data-cleaning',    label: 'Data cleaning',         section: 'Data pipeline' },
        { id: 'export-etl',       label: 'Export & ETL',          section: 'Data pipeline' },
        { id: 'data-versioning',  label: 'Data versioning',       section: 'Data pipeline' },
      ],
    },
    {
      title: 'Monitoring',
      pages: [
        { id: 'monitoring',    label: 'Dashboard & logs', section: 'Monitoring' },
        { id: 'notifications', label: 'Notifications',    section: 'Monitoring' },
      ],
    },
    {
      title: 'API reference',
      pages: [
        { id: 'rest-api',    label: 'REST API',     section: 'API reference' },
        { id: 'webhooks',    label: 'Webhooks',     section: 'API reference' },
        { id: 'error-codes', label: 'Error codes',  section: 'API reference' },
      ],
    },
  ];

  activePage  = signal<string>('introduction');
  searchQuery = signal<string>('');

  readonly flatPages = computed(() =>
    this.sections.flatMap(s => s.pages)
  );

  readonly filteredSections = computed(() => {
    const q = this.searchQuery().toLowerCase().trim();
    if (!q) return this.sections;
    return this.sections
      .map(s => ({ ...s, pages: s.pages.filter(p => p.label.toLowerCase().includes(q)) }))
      .filter(s => s.pages.length > 0);
  });

  readonly currentPage = computed(() =>
    this.flatPages().find(p => p.id === this.activePage())
  );

  readonly prevPage = computed(() => {
    const flat = this.flatPages();
    const idx  = flat.findIndex(p => p.id === this.activePage());
    return idx > 0 ? flat[idx - 1] : null;
  });

  readonly nextPage = computed(() => {
    const flat = this.flatPages();
    const idx  = flat.findIndex(p => p.id === this.activePage());
    return idx < flat.length - 1 ? flat[idx + 1] : null;
  });

  navigate(id: string): void {
    this.activePage.set(id);
  }

  /** Bascule le thème via le service global (source de vérité unique). */
  toggleDarkMode(): void {
    this.theme.toggle();
  }

  onSearch(event: Event): void {
    this.searchQuery.set((event.target as HTMLInputElement).value);
  }
}
