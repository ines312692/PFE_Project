import { TestBed }                      from '@angular/core/testing';
import { provideRouter, Router }        from '@angular/router';
import { ActivatedRoute }               from '@angular/router';
import { NO_ERRORS_SCHEMA }             from '@angular/core';
import { of, throwError }              from 'rxjs';

beforeAll(() => {
  (URL as any).createObjectURL = jest.fn().mockReturnValue('blob:mock-url');
  (URL as any).revokeObjectURL = jest.fn();
});

import { JobDetailComponent }          from '@features/jobs/job-detail/job-detail.component';
import { JobService }                  from '@core/services/job.service';
import { Job }                         from '@core/models/job.model';
import { ScrapeResultResponse }        from '@core/models/scrape-result.model';
import { mockJob, mockApiResponse }    from '../../../helpers/mock-factories';


function makeRoute(id = 'job-1') {
  return { snapshot: { paramMap: { get: jest.fn().mockReturnValue(id) } } };
}

function makeScrapeResult(overrides: Partial<ScrapeResultResponse> = {}): ScrapeResultResponse {
  return {
    id:           'r1',
    jobId:        'job-1',
    status:       'completed',
    records:      [{ name: 'Item 1', price: '10' }, { name: 'Item 2', price: '20' }],
    errors:       [],
    logs:         ['[INFO] Started', '[INFO] Done'],
    pagesCrawled: 5,
    durationMs:   3000,
    engineUsed:   'PLAYWRIGHT',
    scrapedAt:    '2025-01-01T12:00:00Z',
    ...overrides,
  };
}

describe('JobDetailComponent', () => {
  let component: JobDetailComponent;
  let router: Router;
  let jobSvc: {
    getById:    jest.Mock;
    getResults: jest.Mock;
    getHistory: jest.Mock;
    pause:      jest.Mock;
  };

  function setup(job: Job = mockJob()) {
    jobSvc = {
      getById:    jest.fn().mockReturnValue(of(mockApiResponse(job))),
      getResults: jest.fn().mockReturnValue(of(mockApiResponse(makeScrapeResult()))),
      getHistory: jest.fn().mockReturnValue(of({ data: [] })),
      pause:      jest.fn().mockReturnValue(of(mockApiResponse(mockJob({ status: 'PAUSED' })))),
    };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports:   [JobDetailComponent],
      providers: [
        provideRouter([]),
        { provide: JobService,     useValue: jobSvc        },
        { provide: ActivatedRoute, useValue: makeRoute()   },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    const fixture = TestBed.createComponent(JobDetailComponent);
    component = fixture.componentInstance;
    router    = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  }

  beforeEach(() => setup());

  describe('ngOnInit()', () => {
    it('loads the job and sets the job signal', () => {
      expect(jobSvc.getById).toHaveBeenCalledWith('job-1');
      expect(component.job()).not.toBeNull();
    });

    it('loads execution history on init', () => {
      expect(jobSvc.getHistory).toHaveBeenCalledWith('job-1');
    });

    it('loads results when the job is COMPLETED', () => {
      setup(mockJob({ status: 'COMPLETED' }));
      expect(jobSvc.getResults).toHaveBeenCalledWith('job-1');
    });

    it('does not load results for non-COMPLETED jobs', () => {
      setup(mockJob({ status: 'RUNNING' }));
      expect(jobSvc.getResults).not.toHaveBeenCalled();
    });

    it('navigates to /jobs when getById fails', () => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports:   [JobDetailComponent],
        providers: [
          provideRouter([]),
          { provide: JobService, useValue: { getById: jest.fn().mockReturnValue(throwError(() => new Error())), getHistory: jest.fn().mockReturnValue(of({ data: [] })) } },
          { provide: ActivatedRoute, useValue: makeRoute() },
        ],
        schemas: [NO_ERRORS_SCHEMA],
      });
      const fixture = TestBed.createComponent(JobDetailComponent);
      const r = TestBed.inject(Router);
      jest.spyOn(r, 'navigate').mockResolvedValue(true);
      fixture.detectChanges();
      expect(r.navigate).toHaveBeenCalledWith(['/jobs']);
    });
  });

  describe('computed — canLaunch / canPause / isCompleted', () => {
    it.each(['CREATED', 'PAUSED', 'FAILED', 'CANCELLED', 'COMPLETED'])('canLaunch is true for %s', status => {
      component.job.set(mockJob({ status: status as any }));
      expect(component.canLaunch()).toBe(true);
    });

    it('canLaunch is false for RUNNING', () => {
      component.job.set(mockJob({ status: 'RUNNING' }));
      expect(component.canLaunch()).toBe(false);
    });

    it('canPause is true only for RUNNING', () => {
      component.job.set(mockJob({ status: 'RUNNING' }));
      expect(component.canPause()).toBe(true);
    });

    it('canPause is false for PAUSED', () => {
      component.job.set(mockJob({ status: 'PAUSED' }));
      expect(component.canPause()).toBe(false);
    });

    it('isCompleted is true only for COMPLETED', () => {
      component.job.set(mockJob({ status: 'COMPLETED' }));
      expect(component.isCompleted()).toBe(true);
    });

    it('isCompleted is false for RUNNING', () => {
      component.job.set(mockJob({ status: 'RUNNING' }));
      expect(component.isCompleted()).toBe(false);
    });
  });

  describe('computed — isScraper / isCrawler', () => {
    it('isScraper is true for SCRAPER jobs', () => {
      component.job.set(mockJob({ jobType: 'SCRAPER' }));
      expect(component.isScraper()).toBe(true);
      expect(component.isCrawler()).toBe(false);
    });

    it('isCrawler is true for CRAWLER jobs', () => {
      component.job.set(mockJob({ jobType: 'CRAWLER' }));
      expect(component.isCrawler()).toBe(true);
      expect(component.isScraper()).toBe(false);
    });
  });

  describe('computed — extraction schema helpers', () => {
    it('hasFields is true when the schema has at least one field', () => {
      component.job.set(mockJob({
        extractionSchema: { fields: [{ name: 'title', selector: 'h1', type: 'TEXT', attribute: null, required: true }] },
      }));
      expect(component.hasFields()).toBe(true);
    });

    it('hasFields is false when fields is empty', () => {
      component.job.set(mockJob({ extractionSchema: { fields: [] } }));
      expect(component.hasFields()).toBe(false);
    });

    it('isLlm is true when extractionMode is LLM', () => {
      component.job.set(mockJob({ extractionMode: 'LLM' }));
      expect(component.isLlm()).toBe(true);
    });

    it('isRegex is true when extractionMode is REGEX', () => {
      component.job.set(mockJob({ extractionMode: 'REGEX' }));
      expect(component.isRegex()).toBe(true);
    });

    it('llmModelLabel returns a human-readable label', () => {
      component.job.set(mockJob({ extractionSchema: { llmModel: 'GPT_4O' } }));
      expect(component.llmModelLabel()).toBe('GPT-4o');
    });

    it('llmModelLabel falls back to the raw key for unknown models', () => {
      component.job.set(mockJob({ extractionSchema: { llmModel: 'UNKNOWN_MODEL' as any } }));
      expect(component.llmModelLabel()).toBe('UNKNOWN_MODEL');
    });
  });

  describe('computed — hasSeedUrls', () => {
    it('is true when scraperConfig has seed URLs', () => {
      component.job.set(mockJob({
        scraperConfig: { seedUrls: ['https://a.com'], singlePageMode: true, waitForSelector: null, scrollToBottom: false, clickBeforeExtract: null, llmExtractionPrompt: null },
      }));
      expect(component.hasSeedUrls()).toBe(true);
    });

    it('is false when seedUrls is empty', () => {
      component.job.set(mockJob({
        scraperConfig: { seedUrls: [], singlePageMode: true, waitForSelector: null, scrollToBottom: false, clickBeforeExtract: null, llmExtractionPrompt: null },
      }));
      expect(component.hasSeedUrls()).toBe(false);
    });
  });

  describe('tableColumns / resultsJson', () => {
    beforeEach(() => {
      component.scrapeResult.set(makeScrapeResult());
    });

    it('tableColumns derives column names from the first record', () => {
      expect(component.tableColumns()).toEqual(['name', 'price']);
    });

    it('tableColumns is empty when there are no records', () => {
      component.scrapeResult.set(makeScrapeResult({ records: [] }));
      expect(component.tableColumns()).toEqual([]);
    });

    it('resultsJson is valid JSON', () => {
      expect(() => JSON.parse(component.resultsJson())).not.toThrow();
    });
  });

  describe('tabs computed', () => {
    it('does not include the Results tab for non-COMPLETED jobs', () => {
      component.job.set(mockJob({ status: 'RUNNING' }));
      expect(component.tabs().map(t => t.id)).not.toContain('results');
    });

    it('includes the Results tab for COMPLETED jobs', () => {
      component.job.set(mockJob({ status: 'COMPLETED' }));
      expect(component.tabs().map(t => t.id)).toContain('results');
    });

    it('shows the execution count in the History tab label', () => {
      component.executions.set([
        { id: 'e1', num: 1, status: 'completed', date: 'Jan 1', duration: '2s', records: 5, errors: [], logs: [] },
        { id: 'e2', num: 2, status: 'failed',    date: 'Jan 2', duration: '1s', records: 0, errors: [], logs: [] },
      ]);
      const historyTab = component.tabs().find(t => t.id === 'history')!;
      expect(historyTab.label).toBe('History (2)');
    });
  });

  describe('setTab()', () => {
    it('updates the active tab', () => {
      component.setTab('network');
      expect(component.activeTab()).toBe('network');
    });
  });

  describe('selectExec()', () => {
    it('selects an execution', () => {
      component.selectExec('exec-1');
      expect(component.selectedExecId()).toBe('exec-1');
    });

    it('deselects when the same execution is clicked again', () => {
      component.selectExec('exec-1');
      component.selectExec('exec-1');
      expect(component.selectedExecId()).toBeNull();
    });
  });

  describe('statusLabel()', () => {
    it('returns a human-readable label for known statuses', () => {
      expect(component.statusLabel('completed')).toBe('Completed');
      expect(component.statusLabel('failed')).toBe('Failed');
    });

    it('falls back to the raw status string for unknown statuses', () => {
      expect(component.statusLabel('custom-status')).toBe('custom-status');
    });
  });

  describe('pause()', () => {
    it('calls jobService.pause() and updates the job signal', () => {
      component.job.set(mockJob({ id: 'job-1', status: 'RUNNING' }));
      component.pause();
      expect(jobSvc.pause).toHaveBeenCalledWith('job-1');
      expect(component.job()?.status).toBe('PAUSED');
    });
  });

  describe('navigation', () => {
    it('goBack() navigates to /jobs', () => {
      component.goBack();
      expect(router.navigate).toHaveBeenCalledWith(['/jobs']);
    });

    it('goToEdit() navigates to /jobs/:id/edit', () => {
      component.job.set(mockJob({ id: 'job-42' }));
      component.goToEdit();
      expect(router.navigate).toHaveBeenCalledWith(['/jobs', 'job-42', 'edit']);
    });

    it('launch() navigates to /jobs/:id/run', () => {
      component.job.set(mockJob({ id: 'job-42' }));
      component.launch();
      expect(router.navigate).toHaveBeenCalledWith(['/jobs', 'job-42', 'run']);
    });
  });


  describe('exportCsv()', () => {
    beforeEach(() => {
      // Reset call count between tests
      (URL.createObjectURL as jest.Mock).mockClear();
      jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    });

    it('does not throw when records exist', () => {
      component.scrapeResult.set(makeScrapeResult());
      expect(() => component.exportCsv()).not.toThrow();
    });

    it('calls URL.createObjectURL when records exist', () => {
      component.scrapeResult.set(makeScrapeResult());
      component.exportCsv();
      expect(URL.createObjectURL).toHaveBeenCalledTimes(1);
    });

    it('does nothing when there are no records', () => {
      component.scrapeResult.set(makeScrapeResult({ records: [] }));
      component.exportCsv();
      expect(URL.createObjectURL).not.toHaveBeenCalled();
    });

    it('does nothing when scrapeResult is null', () => {
      component.scrapeResult.set(null);
      component.exportCsv();
      expect(URL.createObjectURL).not.toHaveBeenCalled();
    });
  });
});
