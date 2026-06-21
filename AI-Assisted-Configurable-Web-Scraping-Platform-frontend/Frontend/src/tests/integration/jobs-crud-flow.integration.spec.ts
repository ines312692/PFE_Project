import { TestBed }                                          from '@angular/core/testing';
import { provideHttpClient, withInterceptors }             from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router }                           from '@angular/router';
import { NO_ERRORS_SCHEMA, signal }                        from '@angular/core';

import { authInterceptor }    from '@core/interceptors/auth.interceptor';
import { loadingInterceptor } from '@core/interceptors/loading.interceptor';
import { errorInterceptor }   from '@core/interceptors/error.interceptor';
import { JobListComponent }   from '@features/jobs/job-list/job-list.component';
import { ToastService }       from '@core/services/toast.service';
import { LoadingService }     from '@core/services/loading.service';
import { AuthService }        from '@core/auth/auth.service';
import { mockJob, mockApiResponse } from '../helpers/mock-factories';

describe('Jobs CRUD flow (JobListComponent + real JobService + interceptors)', () => {
  let http:           HttpTestingController;
  let router:         Router;
  let loadingService: LoadingService;
  let toast:          { success: jest.Mock; error: jest.Mock; warning: jest.Mock };
  let authMock:       { logout: jest.Mock; currentUser: ReturnType<typeof signal> };

  const BASE = '/v1/jobs';

  beforeEach(() => {
    toast    = { success: jest.fn(), error: jest.fn(), warning: jest.fn() };
    authMock = { logout: jest.fn().mockResolvedValue(undefined), currentUser: signal(null) };

    TestBed.configureTestingModule({
      imports:   [JobListComponent],
      providers: [
        provideHttpClient(
          withInterceptors([authInterceptor, loadingInterceptor, errorInterceptor])
        ),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ToastService, useValue: toast   },
        { provide: AuthService,  useValue: authMock },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });
    http           = TestBed.inject(HttpTestingController);
    router         = TestBed.inject(Router);
    loadingService = TestBed.inject(LoadingService);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  describe('loading jobs on init', () => {
    it('ngOnInit triggers getAll() — request is in flight', () => {
      const fixture = TestBed.createComponent(JobListComponent);
      fixture.detectChanges();                         // triggers ngOnInit

      const req = http.expectOne(BASE);
      expect(req.request.method).toBe('GET');
      expect(req.request.withCredentials).toBe(true); // auth interceptor applied
      req.flush(mockApiResponse([mockJob({ id: 'j1' }), mockJob({ id: 'j2' })]));
    });

    it('populates the jobs signal after a successful response', () => {
      const fixture = TestBed.createComponent(JobListComponent);
      const c = fixture.componentInstance;
      fixture.detectChanges();

      http.expectOne(BASE).flush(
        mockApiResponse([mockJob({ id: 'j1' }), mockJob({ id: 'j2' })])
      );

      expect(c.jobs()).toHaveLength(2);
      expect(c.totalElements()).toBe(2);
    });

    it('sets loading via the LoadingService (loading interceptor)', () => {
      const fixture = TestBed.createComponent(JobListComponent);
      fixture.detectChanges();

      expect(loadingService.isLoading()).toBe(true);
      http.expectOne(BASE).flush(mockApiResponse([]));
      expect(loadingService.isLoading()).toBe(false);
    });

    it('calls auth.logout() on a 401 from getAll()', () => {
      const fixture = TestBed.createComponent(JobListComponent);
      fixture.detectChanges();

      http.expectOne(BASE).flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
      expect(authMock.logout).toHaveBeenCalledTimes(1);
    });

    it('resets loading after a 401 error', () => {
      const fixture = TestBed.createComponent(JobListComponent);
      fixture.detectChanges();

      http.expectOne(BASE).flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
      expect(loadingService.isLoading()).toBe(false);
    });
  });


  describe('delete flow', () => {
    function setupWithJobs() {
      const fixture = TestBed.createComponent(JobListComponent);
      const c = fixture.componentInstance;
      fixture.detectChanges();

      http.expectOne(BASE).flush(
        mockApiResponse([mockJob({ id: 'j1' }), mockJob({ id: 'j2' })])
      );
      return c;
    }

    it('DELETE request has withCredentials (auth interceptor)', () => {
      const c = setupWithJobs();
      c.confirmDelete('j1', new MouseEvent('click'));
      c.deleteJob();

      const req = http.expectOne(`${BASE}/j1`);
      expect(req.request.method).toBe('DELETE');
      expect(req.request.withCredentials).toBe(true);
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('removes the job from the list after successful delete', () => {
      const c = setupWithJobs();
      c.confirmDelete('j1', new MouseEvent('click'));
      c.deleteJob();

      http.expectOne(`${BASE}/j1`).flush(null, { status: 204, statusText: 'No Content' });

      expect(c.jobs().map(j => j.id)).not.toContain('j1');
      expect(c.totalElements()).toBe(1);
    });

    it('shows success toast after delete', () => {
      const c = setupWithJobs();
      c.confirmDelete('j1', new MouseEvent('click'));
      c.deleteJob();

      http.expectOne(`${BASE}/j1`).flush(null, { status: 204, statusText: 'No Content' });
      expect(toast.success).toHaveBeenCalledWith('Job deleted successfully.');
    });

    it('shows error toast when delete fails', () => {
      const c = setupWithJobs();
      c.confirmDelete('j1', new MouseEvent('click'));
      c.deleteJob();

      http.expectOne(`${BASE}/j1`)
        .flush('Server Error', { status: 500, statusText: 'Server Error' });
      expect(toast.error).toHaveBeenCalledWith('Failed to delete the job.');
    });

    it('403 on delete navigates to /dashboard (error interceptor)', () => {
      const c = setupWithJobs();
      c.confirmDelete('j1', new MouseEvent('click'));
      c.deleteJob();

      http.expectOne(`${BASE}/j1`)
        .flush('Forbidden', { status: 403, statusText: 'Forbidden' });
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });
  });
  describe('pause flow', () => {
    function setupRunning() {
      const fixture = TestBed.createComponent(JobListComponent);
      const c = fixture.componentInstance;
      fixture.detectChanges();

      http.expectOne(BASE).flush(
        mockApiResponse([mockJob({ id: 'j1', status: 'RUNNING' })])
      );
      return c;
    }

    it('PAUSE request has withCredentials', () => {
      const c = setupRunning();
      c.pauseJob('j1', new MouseEvent('click'));

      const req = http.expectOne(`${BASE}/j1/pause`);
      expect(req.request.withCredentials).toBe(true);
      req.flush(mockApiResponse(mockJob({ id: 'j1', status: 'PAUSED' })));
    });

    it('updates the job status in the list after pause', () => {
      const c = setupRunning();
      c.pauseJob('j1', new MouseEvent('click'));

      http.expectOne(`${BASE}/j1/pause`)
        .flush(mockApiResponse(mockJob({ id: 'j1', status: 'PAUSED' })));

      expect(c.jobs()[0].status).toBe('PAUSED');
      expect(toast.warning).toHaveBeenCalledWith('Job paused.');
    });
  });


  describe('duplicate flow', () => {
    it('prepends the copy to the list and increments totalElements', () => {
      const fixture = TestBed.createComponent(JobListComponent);
      const c = fixture.componentInstance;
      fixture.detectChanges();

      http.expectOne(BASE).flush(mockApiResponse([mockJob({ id: 'j1' })]));
      expect(c.totalElements()).toBe(1);

      c.duplicateJob('j1', new MouseEvent('click'));
      http.expectOne(`${BASE}/j1/duplicate`)
        .flush(mockApiResponse(mockJob({ id: 'j1-copy', name: 'My Job (copy)' })));

      expect(c.jobs()[0].id).toBe('j1-copy');
      expect(c.totalElements()).toBe(2);
      expect(toast.success).toHaveBeenCalledWith('Job duplicated successfully.');
    });
  });

  describe('client-side filtering after real HTTP load', () => {
    function setupWithMixedJobs() {
      const fixture = TestBed.createComponent(JobListComponent);
      const c = fixture.componentInstance;
      fixture.detectChanges();

      http.expectOne(BASE).flush(mockApiResponse([
        mockJob({ id: 'j1', name: 'Alpha',  status: 'RUNNING',   priority: 'HIGH'   }),
        mockJob({ id: 'j2', name: 'Beta',   status: 'COMPLETED', priority: 'NORMAL' }),
        mockJob({ id: 'j3', name: 'Gamma',  status: 'FAILED',    priority: 'HIGH'   }),
      ]));
      return c;
    }

    it('filteredJobs reflects real data from the service signal', () => {
      const c = setupWithMixedJobs();
      expect(c.filteredJobs()).toHaveLength(3);
    });

    it('search filter works on real data', () => {
      const c = setupWithMixedJobs();
      c.searchQuery.set('alpha');
      expect(c.filteredJobs()).toHaveLength(1);
      expect(c.filteredJobs()[0].id).toBe('j1');
    });

    it('priority filter works on real data', () => {
      const c = setupWithMixedJobs();
      c.filterPriority.set('HIGH');
      expect(c.filteredJobs()).toHaveLength(2);
    });

    it('countByStatus reflects real data', () => {
      const c = setupWithMixedJobs();
      expect(c.countByStatus('RUNNING')).toBe(1);
      expect(c.countByStatus('COMPLETED')).toBe(1);
      expect(c.countByStatus('FAILED')).toBe(1);
    });
  });
});
