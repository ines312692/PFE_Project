import { TestBed }                      from '@angular/core/testing';
import { provideRouter, Router }        from '@angular/router';
import { NO_ERRORS_SCHEMA }             from '@angular/core';
import { of, throwError }              from 'rxjs';

import { JobTemplatesComponent }       from '@features/jobs/job-templates/job-templates.component';
import { TemplateService }             from '@core/services/template.service';
import { JobTemplate }                 from '@core/models/job-templates.model';
import { mockTemplate }                from '../../../helpers/mock-factories';


describe('JobTemplatesComponent', () => {
  let component: JobTemplatesComponent;
  let router: Router;
  let templateSvc: { getAll: jest.Mock };

  const sampleTemplates: JobTemplate[] = [
    mockTemplate({ id: 't1', name: 'Amazon Prices',    category: 'E_COMMERCE', popularity: 90, tags: ['amazon', 'prices'],    description: 'Scrape product prices' }),
    mockTemplate({ id: 't2', name: 'HN Front Page',    category: 'NEWS',       popularity: 80, tags: ['hackernews', 'news'],   description: 'Hacker News scraper'   }),
    mockTemplate({ id: 't3', name: 'LinkedIn Jobs',    category: 'JOB_BOARDS', popularity: 70, tags: ['linkedin', 'jobs'],    description: 'Job listings scraper'  }),
    mockTemplate({ id: 't4', name: 'Zillow Listings',  category: 'REAL_ESTATE', popularity: 60, tags: ['zillow', 'homes'],   description: 'Real estate listings'  }),
    mockTemplate({ id: 't5', name: 'Stock Quotes',     category: 'FINANCE',    popularity: 50, tags: ['stocks', 'finance'],  description: 'Stock price tracker'   }),
  ];

  function setup(templates = sampleTemplates) {
    templateSvc = { getAll: jest.fn().mockReturnValue(of(templates)) };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports:   [JobTemplatesComponent],
      providers: [
        provideRouter([]),
        { provide: TemplateService, useValue: templateSvc },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    const fixture = TestBed.createComponent(JobTemplatesComponent);
    component = fixture.componentInstance;
    router    = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  }

  beforeEach(() => setup());


  describe('ngOnInit()', () => {
    it('loads templates and populates all()', () => {
      expect(component.all()).toHaveLength(5);
    });

    it('sets loading to false after loading', () => {
      expect(component.loading()).toBe(false);
    });

    it('sets error to true on API failure', () => {
      templateSvc.getAll.mockReturnValue(throwError(() => new Error('fail')));
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports:   [JobTemplatesComponent],
        providers: [
          provideRouter([]),
          { provide: TemplateService, useValue: templateSvc },
        ],
        schemas: [NO_ERRORS_SCHEMA],
      });
      const fixture = TestBed.createComponent(JobTemplatesComponent);
      const c = fixture.componentInstance;
      fixture.detectChanges();
      expect(c.error()).toBe(true);
      expect(c.loading()).toBe(false);
    });
  });

  describe('visible() — category filter', () => {
    it('returns all templates when activeFilter is ALL', () => {
      expect(component.visible()).toHaveLength(5);
    });

    it('filters to the selected category', () => {
      component.activeFilter.set('E_COMMERCE');
      expect(component.visible()).toHaveLength(1);
      expect(component.visible()[0].id).toBe('t1');
    });

    it('returns an empty array when no template matches the category', () => {
      component.activeFilter.set('SOCIAL');
      expect(component.visible()).toHaveLength(0);
    });
  });

  describe('visible() — search', () => {
    it('filters by template name', () => {
      component.search.set('Amazon');
      expect(component.visible()).toHaveLength(1);
      expect(component.visible()[0].id).toBe('t1');
    });

    it('filters by description', () => {
      component.search.set('real estate listings');
      expect(component.visible()).toHaveLength(1);
      expect(component.visible()[0].id).toBe('t4');
    });

    it('filters by tag', () => {
      component.search.set('hackernews');
      expect(component.visible()).toHaveLength(1);
      expect(component.visible()[0].id).toBe('t2');
    });

    it('is case-insensitive', () => {
      component.search.set('LINKEDIN');
      expect(component.visible()).toHaveLength(1);
    });

    it('returns empty when nothing matches', () => {
      component.search.set('xyzzy-nomatch');
      expect(component.visible()).toHaveLength(0);
    });
  });

  describe('visible() — combined filter + search', () => {
    it('applies both category and search', () => {
      component.activeFilter.set('NEWS');
      component.search.set('hacker');
      expect(component.visible()).toHaveLength(1);
      expect(component.visible()[0].id).toBe('t2');
    });

    it('returns empty when search matches but category does not', () => {
      component.activeFilter.set('E_COMMERCE');
      component.search.set('hacker');
      expect(component.visible()).toHaveLength(0);
    });
  });

  describe('visible() — popularity sort', () => {
    it('sorts by popularity descending', () => {
      const popularities = component.visible().map(t => t.popularity);
      expect(popularities).toEqual([...popularities].sort((a, b) => b - a));
    });
  });
  describe('categories()', () => {
    it('always starts with ALL', () => {
      expect(component.categories()[0]).toBe('ALL');
    });

    it('contains each unique category from the loaded templates', () => {
      const cats = component.categories();
      expect(cats).toContain('E_COMMERCE');
      expect(cats).toContain('NEWS');
      expect(cats).toContain('JOB_BOARDS');
    });

    it('does not contain duplicates', () => {
      const cats = component.categories();
      expect(cats.length).toBe(new Set(cats).size);
    });
  });

  // ── filterCount / filterLabel ─────────────────────────────────────────────

  describe('filterCount()', () => {
    it('returns the total count for ALL', () => {
      expect(component.filterCount('ALL')).toBe(5);
    });

    it('returns the count for a specific category', () => {
      expect(component.filterCount('E_COMMERCE')).toBe(1);
      expect(component.filterCount('NEWS')).toBe(1);
    });

    it('returns 0 for a category with no templates', () => {
      expect(component.filterCount('SOCIAL')).toBe(0);
    });
  });

  describe('filterLabel()', () => {
    it('returns "All" for ALL', () => {
      expect(component.filterLabel('ALL')).toBe('All');
    });

    it('returns the CATEGORY_META label for known categories', () => {
      expect(component.filterLabel('E_COMMERCE')).toBe('E-Commerce');
      expect(component.filterLabel('NEWS')).toBe('News');
    });
  });

  describe('clearSearch()', () => {
    it('resets the search signal to empty string', () => {
      component.search.set('amazon');
      component.clearSearch();
      expect(component.search()).toBe('');
    });

    it('restores all templates after clear', () => {
      component.search.set('amazon');
      component.clearSearch();
      expect(component.visible()).toHaveLength(5);
    });
  });

  describe('useTemplate()', () => {
    it('navigates to /jobs/create with templateId and jobType query params', () => {
      const tpl = mockTemplate({ id: 'tpl-99', jobType: 'CRAWLER' });
      component.useTemplate(tpl);
      expect(router.navigate).toHaveBeenCalledWith(
        ['/jobs', 'create'],
        { queryParams: { templateId: 'tpl-99', jobType: 'CRAWLER' } }
      );
    });
  });

  describe('goBack()', () => {
    it('navigates to /jobs', () => {
      component.goBack();
      expect(router.navigate).toHaveBeenCalledWith(['/jobs']);
    });
  });
});
