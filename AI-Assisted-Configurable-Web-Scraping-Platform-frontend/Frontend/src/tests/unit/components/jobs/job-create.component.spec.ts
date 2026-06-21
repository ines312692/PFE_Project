import { TestBed }                      from '@angular/core/testing';
import { provideRouter, Router }        from '@angular/router';
import { ActivatedRoute }               from '@angular/router';
import { NO_ERRORS_SCHEMA, signal }     from '@angular/core';
import { of, throwError }              from 'rxjs';

import { JobCreateComponent }          from '@features/jobs/job-create/job-create.component';
import { JobService }                  from '@core/services/job.service';
import { TemplateService }             from '@core/services/template.service';
import { ToastService }                from '@core/services/toast.service';
import { SiteProbeService }            from '@core/services/site-probe.service';
import { mockJob, mockApiResponse, mockTemplate, mockProbeResponse } from '../../../helpers/mock-factories';


function makeRoute(jobId: string | null = null, templateId: string | null = null, jobType: string | null = null) {
  return {
    snapshot: {
      paramMap:      { get: jest.fn().mockReturnValue(jobId)      },
      queryParamMap: { get: jest.fn((key: string) => key === 'templateId' ? templateId : jobType) },
    },
  };
}


describe('JobCreateComponent', () => {
  let component: JobCreateComponent;
  let router: Router;
  let jobSvc:      { getAll: jest.Mock; getById: jest.Mock; create: jest.Mock; update: jest.Mock; loading: ReturnType<typeof signal<boolean>> };
  let templateSvc: { getById: jest.Mock };
  let toast:       { success: jest.Mock; error: jest.Mock; warning: jest.Mock };
  let probeSvc:    { probe: jest.Mock };

  beforeEach(() => {
    jobSvc = {
      loading:  signal(false),
      getAll:   jest.fn().mockReturnValue(of(mockApiResponse([]))),
      getById:  jest.fn().mockReturnValue(of(mockApiResponse(mockJob()))),
      create:   jest.fn().mockReturnValue(of(mockApiResponse(mockJob({ id: 'new-job' })))),
      update:   jest.fn().mockReturnValue(of(mockApiResponse(mockJob()))),
    };
    templateSvc = { getById: jest.fn().mockReturnValue(of(mockTemplate())) };
    toast       = { success: jest.fn(), error: jest.fn(), warning: jest.fn() };
    probeSvc    = { probe: jest.fn().mockReturnValue(of(mockApiResponse(mockProbeResponse()))) };

    TestBed.configureTestingModule({
      imports:   [JobCreateComponent],
      providers: [
        provideRouter([]),
        { provide: JobService,      useValue: jobSvc       },
        { provide: TemplateService, useValue: templateSvc  },
        { provide: ToastService,    useValue: toast        },
        { provide: SiteProbeService, useValue: probeSvc    },
        { provide: ActivatedRoute,  useValue: makeRoute()  },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    jest.spyOn(window, 'scrollTo').mockImplementation(() => {});

    const fixture = TestBed.createComponent(JobCreateComponent);
    component = fixture.componentInstance;
    router    = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => jest.restoreAllMocks());


  describe('initial state', () => {
    it('starts on step 1', () => {
      expect(component.currentStep()).toBe(1);
    });

    it('is not loading', () => {
      expect(component.loading()).toBe(false);
    });

    it('has no error', () => {
      expect(component.error()).toBeNull();
    });

    it('isEditMode() is false when no job id in route', () => {
      expect(component.isEditMode()).toBe(false);
    });
  });

  // ── isEditMode ─────────────────────────────────────────────────────────────

  describe('isEditMode()', () => {
    it('returns true when the route has a job id', () => {
      // TestBed has already been instantiated by beforeEach — reset before re-configuring
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports:   [JobCreateComponent],
        providers: [
          provideRouter([]),
          { provide: JobService,       useValue: jobSvc       },
          { provide: TemplateService,  useValue: templateSvc  },
          { provide: ToastService,     useValue: toast        },
          { provide: SiteProbeService, useValue: probeSvc     },
          { provide: ActivatedRoute,   useValue: makeRoute('job-123') },
        ],
        schemas: [NO_ERRORS_SCHEMA],
      });
      const fixture = TestBed.createComponent(JobCreateComponent);
      const c = fixture.componentInstance;
      fixture.detectChanges();
      expect(c.isEditMode()).toBe(true);
    });
  });


  describe('step navigation', () => {
    it('goToStep(2) advances when step 1 is valid', () => {
      component.form.name      = 'My Job';
      component.form.targetUrl = 'https://example.com';
      component.goToStep(2);
      expect(component.currentStep()).toBe(2);
    });

    it('goToStep(2) is blocked and sets an error when name is empty', () => {
      component.form.name      = '';
      component.form.targetUrl = 'https://example.com';
      component.goToStep(2);
      expect(component.currentStep()).toBe(1);
      expect(component.error()).toBeTruthy();
    });

    it('goToStep(2) is blocked when targetUrl is empty', () => {
      component.form.name      = 'My Job';
      component.form.targetUrl = '';
      component.goToStep(2);
      expect(component.error()).toContain('URL');
    });

    it('goToStep(2) is blocked when targetUrl is not a valid URL', () => {
      component.form.name      = 'My Job';
      component.form.targetUrl = 'not-a-url';
      component.goToStep(2);
      expect(component.error()).toContain('valid URL');
    });

    it('goToStep() clears the error when moving to a previous step', () => {
      component.form.name      = 'My Job';
      component.form.targetUrl = 'https://example.com';
      component.goToStep(2);
      component.goToStep(1);
      expect(component.error()).toBeNull();
    });

    it('prevStep() decrements the current step', () => {
      component.form.name      = 'My Job';
      component.form.targetUrl = 'https://example.com';
      component.goToStep(2);
      component.prevStep();
      expect(component.currentStep()).toBe(1);
    });

    it('prevStep() does nothing on step 1', () => {
      component.prevStep();
      expect(component.currentStep()).toBe(1);
    });
  });


  describe('step 1 validation', () => {
    it('requires a cron expression when triggerType is SCHEDULED', () => {
      component.form.name          = 'Job';
      component.form.targetUrl     = 'https://example.com';
      component.form.triggerType   = 'SCHEDULED';
      component.form.cronExpression = '';
      component.goToStep(2);
      expect(component.error()).toContain('Cron');
    });

    it('allows advancing when a cron expression is provided', () => {
      component.form.name           = 'Job';
      component.form.targetUrl      = 'https://example.com';
      component.form.triggerType    = 'SCHEDULED';
      component.form.cronExpression = '0 */6 * * *';
      component.goToStep(2);
      expect(component.currentStep()).toBe(2);
    });
  });

  describe('schema validation (step 4)', () => {
    function advanceTo5(): void {
      component.form.name      = 'Job';
      component.form.targetUrl = 'https://example.com';
      component.goToStep(2);
      component.goToStep(3);
      component.goToStep(4);
    }

    it('LLM mode requires a prompt', () => {
      advanceTo5();
      component.form.extractionMode = 'LLM';
      component.form.extractionSchema.llmPrompt = '';
      component.goToStep(5);
      expect(component.error()).toContain('LLM prompt');
    });

    it('REGEX mode requires a pattern', () => {
      advanceTo5();
      component.form.extractionMode = 'REGEX';
      component.form.extractionSchema.regexPattern = '';
      component.goToStep(5);
      expect(component.error()).toContain('Regex pattern');
    });

    it('CSS_SELECTOR mode requires at least one field', () => {
      advanceTo5();
      component.form.extractionMode = 'CSS_SELECTOR';
      component.form.extractionSchema.fields = [];
      component.goToStep(5);
      expect(component.error()).toContain('At least one field');
    });

    it('CSS_SELECTOR blocks when a field is missing a selector', () => {
      advanceTo5();
      component.form.extractionMode = 'CSS_SELECTOR';
      component.form.extractionSchema.fields = [
        { name: 'title', selector: '', type: 'TEXT', attribute: null, required: true },
      ];
      component.goToStep(5);
      expect(component.error()).toContain('selector');
    });
  });

  describe('seed URLs', () => {
    it('addSeedUrl() appends a valid URL to scraperConfig.seedUrls', () => {
      component.newSeedUrl = 'https://page2.com';
      component.addSeedUrl();
      expect(component.form.scraperConfig.seedUrls).toContain('https://page2.com');
      expect(component.newSeedUrl).toBe('');
    });

    it('addSeedUrl() sets an error for an invalid URL', () => {
      component.newSeedUrl = 'bad-url';
      component.addSeedUrl();
      expect(component.form.scraperConfig.seedUrls).toHaveLength(0);
      expect(component.error()).toContain('Invalid seed URL');
    });

    it('addSeedUrl() does nothing for an empty string', () => {
      component.newSeedUrl = '';
      component.addSeedUrl();
      expect(component.form.scraperConfig.seedUrls).toHaveLength(0);
    });

    it('removeSeedUrl() removes by index', () => {
      component.form.scraperConfig.seedUrls = ['https://a.com', 'https://b.com'];
      component.removeSeedUrl(0);
      expect(component.form.scraperConfig.seedUrls).toEqual(['https://b.com']);
    });
  });

  describe('toggle methods', () => {
    it('toggleJavascriptEnabled() flips javascriptEnabled', () => {
      const before = component.form.browserConfig.javascriptEnabled;
      component.toggleJavascriptEnabled();
      expect(component.form.browserConfig.javascriptEnabled).toBe(!before);
    });

    it('toggleStealthMode() flips stealthMode', () => {
      const before = component.form.browserConfig.stealthMode;
      component.toggleStealthMode();
      expect(component.form.browserConfig.stealthMode).toBe(!before);
    });

    it('toggleCircuitBreaker() flips circuitBreakerEnabled', () => {
      const before = component.form.retryPolicy.circuitBreakerEnabled;
      component.toggleCircuitBreaker();
      expect(component.form.retryPolicy.circuitBreakerEnabled).toBe(!before);
    });

    it('toggleSinglePageMode() flips singlePageMode', () => {
      const before = component.form.scraperConfig.singlePageMode;
      component.toggleSinglePageMode();
      expect(component.form.scraperConfig.singlePageMode).toBe(!before);
    });

    it('toggleScrollToBottom() flips scrollToBottom', () => {
      const before = component.form.scraperConfig.scrollToBottom;
      component.toggleScrollToBottom();
      expect(component.form.scraperConfig.scrollToBottom).toBe(!before);
    });

    it('toggleFollowRedirects() flips followRedirects', () => {
      const before = component.form.crawlerConfig.followRedirects;
      component.toggleFollowRedirects();
      expect(component.form.crawlerConfig.followRedirects).toBe(!before);
    });
  });

  describe('cancel()', () => {
    it('navigates to /jobs', () => {
      component.cancel();
      expect(router.navigate).toHaveBeenCalledWith(['/jobs']);
    });
  });

  describe('createAnother()', () => {
    it('resets the form and goes back to step 1', () => {
      component.form.name = 'Old Job';
      component.createAnother();
      expect(component.form.name).toBe('');
      expect(component.currentStep()).toBe(1);
    });

    it('clears the created and error signals', () => {
      component.error.set('some error');
      component.createAnother();
      expect(component.error()).toBeNull();
    });
  });

  describe('submit() — create', () => {
    beforeEach(() => {
      component.form.name           = 'My Job';
      component.form.targetUrl      = 'https://example.com';
      component.form.extractionMode = 'AUTO';
    });

    it('calls jobService.create() with a valid payload', () => {
      component.submit();
      expect(jobSvc.create).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'My Job', targetUrl: 'https://example.com' })
      );
    });

    it('sets the created signal on success', () => {
      component.submit();
      expect(component.created()).not.toBeNull();
    });

    it('shows a success toast on creation', () => {
      component.submit();
      expect(toast.success).toHaveBeenCalledWith('Job created successfully.');
    });

    it('sets an error signal and stops loading on failure', () => {
      jobSvc.create.mockReturnValue(throwError(() => ({ error: { message: 'Server error' } })));
      component.submit();
      expect(component.loading()).toBe(false);
      expect(component.error()).toBe('Server error');
    });

    it('does not call create when step 1 validation fails', () => {
      component.form.name = '';
      component.submit();
      expect(jobSvc.create).not.toHaveBeenCalled();
    });
  });

  describe('schema event handlers', () => {
    it('onSchemaChange() updates the schema', () => {
      const schema = { llmPrompt: 'Extract prices' };
      component.onSchemaChange(schema);
      expect(component.form.extractionSchema).toEqual(schema);
    });

    it('onModeChange() updates extractionMode', () => {
      component.onModeChange('LLM');
      expect(component.form.extractionMode).toBe('LLM');
    });
  });
});
