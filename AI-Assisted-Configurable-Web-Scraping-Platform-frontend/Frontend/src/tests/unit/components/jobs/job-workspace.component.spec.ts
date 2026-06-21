import { TestBed }                      from '@angular/core/testing';
import { provideRouter, Router }        from '@angular/router';
import { ActivatedRoute }               from '@angular/router';
import { NO_ERRORS_SCHEMA }             from '@angular/core';
import { of }                           from 'rxjs';

import { JobWorkspaceComponent }       from '@features/jobs/job-workspace/job-workspace.component';
import { JobService }                  from '@core/services/job.service';
import { TemplateService }             from '@core/services/template.service';
import { ToastService }                from '@core/services/toast.service';
import { SiteProbeService }            from '@core/services/site-probe.service';
import { mockJob, mockApiResponse, mockTemplate } from '../../../helpers/mock-factories';
import {
  DEFAULT_SPLIT, MIN_PCT, MAX_PCT,
  HISTORY_DEFAULT_PX,
} from '@core/constants/workspace.constants';

const localStorageMock = {
  getItem:    jest.fn().mockReturnValue(null),
  setItem:    jest.fn(),
  removeItem: jest.fn(),
  clear:      jest.fn(),
};

beforeAll(() => {
  Object.defineProperty(window, 'localStorage', {
    value:        localStorageMock,
    writable:     true,
    configurable: true,
  });
});

beforeEach(() => {
  localStorageMock.getItem.mockReturnValue(null);
  localStorageMock.setItem.mockClear();
});


function makeRoute(id: string | null = null, templateId: string | null = null, jobType: string | null = null) {
  return {
    snapshot: {
      paramMap:      { get: jest.fn().mockReturnValue(id) },
      queryParamMap: {
        get: jest.fn((key: string) => {
          if (key === 'templateId') return templateId;
          if (key === 'jobType')    return jobType;
          return null;
        }),
      },
    },
  };
}


describe('JobWorkspaceComponent', () => {
  let component: JobWorkspaceComponent;
  let router: Router;
  let jobSvc: { getHistory: jest.Mock };

  function setup(routeOverride = makeRoute()) {

    jobSvc = {
      getHistory: jest.fn().mockReturnValue(of({ data: [] })),
      // needed by JobCreateComponent (child):
      getById:    jest.fn().mockReturnValue(of(mockApiResponse(mockJob()))),
      create:     jest.fn().mockReturnValue(of(mockApiResponse(mockJob()))),
      update:     jest.fn().mockReturnValue(of(mockApiResponse(mockJob()))),
      getAll:     jest.fn().mockReturnValue(of(mockApiResponse([]))),
    } as any;

    const templateSvc: any = {
      getById: jest.fn().mockReturnValue(of(mockTemplate())),
      getAll:  jest.fn().mockReturnValue(of([])),
    };
    const toast: any = { success: jest.fn(), error: jest.fn(), warning: jest.fn() };
    const probeSvc: any = { probe: jest.fn().mockReturnValue(of(null)) };

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports:   [JobWorkspaceComponent],
      providers: [
        provideRouter([]),
        { provide: JobService,       useValue: jobSvc       },
        { provide: TemplateService,  useValue: templateSvc  },
        { provide: ToastService,     useValue: toast        },
        { provide: SiteProbeService, useValue: probeSvc     },
        { provide: ActivatedRoute,   useValue: routeOverride },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    TestBed.overrideComponent(JobWorkspaceComponent, {
      set: {
        template: `
          <div #workspaceEl class="workspace">
            <div class="workspace__panel workspace__panel--left"  [ngStyle]="leftStyle"></div>
            <div class="workspace__panel workspace__panel--right" [ngStyle]="rightStyle"></div>
            <div class="workspace__panel workspace__panel--history"></div>
          </div>
        `,
      },
    });

    const fixture = TestBed.createComponent(JobWorkspaceComponent);
    component = fixture.componentInstance;
    router    = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  }

  beforeEach(() => setup());


  describe('initial state (no route params)', () => {
    it('shows the type picker on first load', () => {
      expect(component.showTypePicker()).toBe(true);
    });

    it('starts with SCRAPER as default type', () => {
      expect(component.selectedType()).toBe('SCRAPER');
    });

    it('starts with the default split percentage', () => {
      expect(component.splitPct()).toBe(DEFAULT_SPLIT);
    });

    it('starts with the default history panel size', () => {
      expect(component.historySizePx()).toBe(HISTORY_DEFAULT_PX);
    });

    it('does not load history when there is no job id', () => {
      expect(jobSvc.getHistory).not.toHaveBeenCalled();
    });
  });


  describe('edit mode — route has a job id', () => {
    beforeEach(() => setup(makeRoute('job-42')));

    it('hides the type picker', () => {
      expect(component.showTypePicker()).toBe(false);
    });

    it('sets activeJobId from the route param', () => {
      expect(component.activeJobId()).toBe('job-42');
    });

    it('loads history for the existing job', () => {
      expect(jobSvc.getHistory).toHaveBeenCalledWith('job-42');
    });

    it('sets isEditMode to true', () => {
      expect(component.isEditMode()).toBe(true);
    });
  });


  describe('template route', () => {
    beforeEach(() => setup(makeRoute(null, 'tpl-1', 'CRAWLER')));

    it('hides the type picker when coming from a template', () => {
      expect(component.showTypePicker()).toBe(false);
    });

    it('sets the selected type from the query param', () => {
      expect(component.selectedType()).toBe('CRAWLER');
    });
  });


  describe('selectJobType()', () => {
    it('sets the selectedType', () => {
      jest.useFakeTimers();
      component.selectJobType('CRAWLER');
      expect(component.selectedType()).toBe('CRAWLER');
      jest.runAllTimers();
      jest.useRealTimers();
    });

    it('sets pickerLeaving to true during the animation', () => {
      jest.useFakeTimers();
      component.selectJobType('CRAWLER');
      expect(component.pickerLeaving()).toBe(true);
      jest.useRealTimers();
    });

    it('hides the picker after the animation delay', () => {
      jest.useFakeTimers();
      component.selectJobType('SCRAPER');
      jest.advanceTimersByTime(500);
      expect(component.showTypePicker()).toBe(false);
      expect(component.pickerLeaving()).toBe(false);
      jest.useRealTimers();
    });
  });

  describe('reopenTypePicker()', () => {
    it('shows the type picker again', () => {
      jest.useFakeTimers();
      component.selectJobType('SCRAPER');
      jest.advanceTimersByTime(500);
      expect(component.showTypePicker()).toBe(false);
      component.reopenTypePicker();
      expect(component.showTypePicker()).toBe(true);
      jest.useRealTimers();
    });
  });


  describe('onJobCreated()', () => {
    it('sets activeJobId from the created job', () => {
      const job = mockJob({ id: 'new-job-99' });
      component.onJobCreated(job);
      expect(component.activeJobId()).toBe('new-job-99');
    });
  });

  describe('onJobLaunched()', () => {
    it('refreshes history when there is an active job', () => {
      component.activeJobId.set('job-1');
      component.onJobLaunched();
      expect(jobSvc.getHistory).toHaveBeenCalledWith('job-1');
    });

    it('does not call getHistory when no job is active', () => {
      component.activeJobId.set(null);
      component.onJobLaunched();
      expect(jobSvc.getHistory).not.toHaveBeenCalled();
    });
  });


  describe('split panel getters', () => {
    it('leftPct reflects the split percentage', () => {
      component.splitPct.set(55);
      expect(component.leftPct).toBe(55);
    });

    it('rightPct is 100 - leftPct', () => {
      component.splitPct.set(55);
      expect(component.rightPct).toBe(45);
    });

    it('leftStyle returns flex values based on splitPct', () => {
      component.splitPct.set(60);
      component.isNarrow.set(false);
      expect(component.leftStyle).toEqual({ flex: '0 0 60%', maxWidth: '60%' });
    });

    it('leftStyle returns empty object on narrow screens', () => {
      component.isNarrow.set(true);
      expect(component.leftStyle).toEqual({});
    });

    it('rightStyle returns flex values for the right panel', () => {
      component.splitPct.set(60);
      component.isNarrow.set(false);
      expect(component.rightStyle).toEqual({ flex: '0 0 40%', maxWidth: '40%' });
    });
  });


  describe('expandLeft()', () => {
    it('sets splitPct to MAX_PCT', () => {
      component.expandLeft();
      expect(component.splitPct()).toBe(MAX_PCT);
    });
  });

  describe('expandRight()', () => {
    it('sets splitPct to MIN_PCT', () => {
      component.expandRight();
      expect(component.splitPct()).toBe(MIN_PCT);
    });
  });

  describe('resetSplit()', () => {
    it('resets splitPct to DEFAULT_SPLIT', () => {
      component.splitPct.set(70);
      component.resetSplit();
      expect(component.splitPct()).toBe(DEFAULT_SPLIT);
    });
  });

  describe('resetHistSplit()', () => {
    it('resets historySizePx to HISTORY_DEFAULT_PX', () => {
      component.historySizePx.set(500);
      component.resetHistSplit();
      expect(component.historySizePx()).toBe(HISTORY_DEFAULT_PX);
    });
  });


  describe('onResize()', () => {
    it('sets isNarrow based on window.innerWidth', () => {
      Object.defineProperty(window, 'innerWidth', { value: 700, writable: true });
      component.onResize();
      expect(component.isNarrow()).toBe(true);

      Object.defineProperty(window, 'innerWidth', { value: 1200, writable: true });
      component.onResize();
      expect(component.isNarrow()).toBe(false);
    });
  });

  describe('goBack()', () => {
    it('navigates to /jobs', () => {
      component.goBack();
      expect(router.navigate).toHaveBeenCalledWith(['/jobs']);
    });
  });
});
