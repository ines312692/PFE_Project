import {Component, computed, signal, inject, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';

import {TemplateService} from '../../../core/services/template.service';
import {CATEGORY_META, JobTemplate, TemplateCategory} from '../../../core/models/job-templates.model';

type Filter = TemplateCategory | 'ALL';

@Component({
  selector: 'app-job-templates',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './job-templates.component.html',
  styleUrl: './job-templates.component.scss',
})
export class JobTemplatesComponent implements OnInit {

  private readonly router = inject(Router);
  private readonly templateService = inject(TemplateService);

  readonly categoryMeta = CATEGORY_META;

  all = signal<JobTemplate[]>([]);
  loading = signal(true);
  error = signal(false);

  activeFilter = signal<Filter>('ALL');
  search = signal('');

  ngOnInit(): void {
    this.templateService.getAll().subscribe({
      next: tpls => {
        this.all.set(tpls);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  categories = computed<Filter[]>(() => [
    'ALL',
    ...[...new Set(this.all().map(t => t.category))] as TemplateCategory[],
  ]);

  visible = computed(() => {
    const f = this.activeFilter();
    const query = this.search().toLowerCase().trim();
    return this.all()
      .filter(t => {
        const okCat = f === 'ALL' || t.category === f;
        const okQuery = !query
          || t.name.toLowerCase().includes(query)
          || t.description.toLowerCase().includes(query)
          || t.tags.some(g => g.toLowerCase().includes(query));
        return okCat && okQuery;
      })
      .sort((a, b) => b.popularity - a.popularity);
  });

  filterCount(f: Filter): number {
    return f === 'ALL'
      ? this.all().length
      : this.all().filter(t => t.category === f).length;
  }

  filterLabel(f: Filter): string {
    return f === 'ALL' ? 'All' : CATEGORY_META[f].label;
  }

  onSearch(e: Event): void {
    this.search.set((e.target as HTMLInputElement).value);
  }

  clearSearch(): void {
    this.search.set('');
  }

  useTemplate(tpl: JobTemplate): void {
    void this.router.navigate(['/jobs', 'create'], {
      queryParams: {templateId: tpl.id, jobType: tpl.jobType},
    });
  }

  brandKey(name: string): string {
    const n = name.toLowerCase();
    if (n.includes('amazon')) return 'amazon';
    if (n.includes('linkedin')) return 'linkedin';
    if (n.includes('reddit')) return 'reddit';
    if (n.includes('shopify')) return 'shopify';
    if (n.includes('indeed')) return 'indeed';
    if (n.includes('twitter') || n.includes('x.com')) return 'twitter';
    return '';
  }

  costLabel(tpl: JobTemplate): string {
    return (tpl as any).cost ?? 'No Extra Cost';
  }

  isPaid(tpl: JobTemplate): boolean {
    return this.costLabel(tpl).startsWith('$');
  }
}
