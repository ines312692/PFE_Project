import { TestBed }                                            from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient }   from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController }   from '@angular/common/http/testing';
import { provideRouter, Router }                             from '@angular/router';
import { signal }                                            from '@angular/core';

import { authInterceptor }    from '@core/interceptors/auth.interceptor';
import { loadingInterceptor } from '@core/interceptors/loading.interceptor';
import { errorInterceptor }   from '@core/interceptors/error.interceptor';
import { LoadingService }     from '@core/services/loading.service';
import { AuthService }        from '@core/auth/auth.service';


describe('Interceptors chain (auth → loading → error)', () => {
  let http:           HttpClient;
  let httpTesting:    HttpTestingController;
  let router:         Router;
  let loadingService: LoadingService;
  let authMock:       { logout: jest.Mock; currentUser: ReturnType<typeof signal> };

  beforeEach(() => {
    authMock = {
      logout:      jest.fn().mockResolvedValue(undefined),
      currentUser: signal(null),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withInterceptors([authInterceptor, loadingInterceptor, errorInterceptor])
        ),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authMock },
      ],
    });

    http           = TestBed.inject(HttpClient);
    httpTesting    = TestBed.inject(HttpTestingController);
    router         = TestBed.inject(Router);
    loadingService = TestBed.inject(LoadingService);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpTesting.verify());

  // ── auth + loading: happy path ────────────────────────────────────────────

  describe('successful request — all interceptors cooperate', () => {
    it('attaches withCredentials: true (authInterceptor)', () => {
      http.get('/api/data').subscribe();
      const req = httpTesting.expectOne('/api/data');
      expect(req.request.withCredentials).toBe(true);
      req.flush({ ok: true });
    });

    it('sets isLoading to true while the request is in flight (loadingInterceptor)', () => {
      http.get('/api/data').subscribe();
      expect(loadingService.isLoading()).toBe(true);
      httpTesting.expectOne('/api/data').flush({});
    });

    it('resets isLoading to false after completion (loadingInterceptor)', () => {
      http.get('/api/data').subscribe();
      httpTesting.expectOne('/api/data').flush({});
      expect(loadingService.isLoading()).toBe(false);
    });

    it('passes the response through unchanged to the subscriber', () => {
      let result: unknown;
      http.get('/api/data').subscribe(r => (result = r));
      httpTesting.expectOne('/api/data').flush({ answer: 42 });
      expect(result).toEqual({ answer: 42 });
    });
  });

  describe('401 Unauthorized', () => {
    it('calls auth.logout() (errorInterceptor)', () => {
      http.get('/api/secret').subscribe({ error: () => {} });
      httpTesting.expectOne('/api/secret')
        .flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
      expect(authMock.logout).toHaveBeenCalledTimes(1);
    });

    it('resets isLoading after the 401 (loadingInterceptor finalize)', () => {
      http.get('/api/secret').subscribe({ error: () => {} });
      httpTesting.expectOne('/api/secret')
        .flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
      expect(loadingService.isLoading()).toBe(false);
    });

    it('still includes withCredentials on the request that returns 401', () => {
      http.get('/api/secret').subscribe({ error: () => {} });
      const req = httpTesting.expectOne('/api/secret');
      expect(req.request.withCredentials).toBe(true);
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });

    it('rethrows the error to the subscriber', () => {
      let caught: unknown;
      http.get('/api/secret').subscribe({ error: e => (caught = e) });
      httpTesting.expectOne('/api/secret')
        .flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
      expect((caught as any).status).toBe(401);
    });
  });

  describe('403 Forbidden', () => {
    it('navigates to /dashboard (errorInterceptor)', () => {
      http.get('/api/admin').subscribe({ error: () => {} });
      httpTesting.expectOne('/api/admin')
        .flush('Forbidden', { status: 403, statusText: 'Forbidden' });
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('resets isLoading after the 403', () => {
      http.get('/api/admin').subscribe({ error: () => {} });
      httpTesting.expectOne('/api/admin')
        .flush('Forbidden', { status: 403, statusText: 'Forbidden' });
      expect(loadingService.isLoading()).toBe(false);
    });

    it('does NOT call logout on 403', () => {
      http.get('/api/admin').subscribe({ error: () => {} });
      httpTesting.expectOne('/api/admin')
        .flush('Forbidden', { status: 403, statusText: 'Forbidden' });
      expect(authMock.logout).not.toHaveBeenCalled();
    });
  });

  describe('network error', () => {
    it('resets isLoading even on a network failure', () => {
      http.get('/api/data').subscribe({ error: () => {} });
      httpTesting.expectOne('/api/data').error(new ErrorEvent('NetworkError'));
      expect(loadingService.isLoading()).toBe(false);
    });

    it('does not call logout on a network error', () => {
      http.get('/api/data').subscribe({ error: () => {} });
      httpTesting.expectOne('/api/data').error(new ErrorEvent('NetworkError'));
      expect(authMock.logout).not.toHaveBeenCalled();
    });
  });

  describe('concurrent requests', () => {
    it('all concurrent requests get withCredentials', () => {
      http.get('/a').subscribe();
      http.get('/b').subscribe();
      const [ra, rb] = httpTesting.match(() => true);
      expect(ra.request.withCredentials).toBe(true);
      expect(rb.request.withCredentials).toBe(true);
      ra.flush({});
      rb.flush({});
    });

    it('loading stays true until the last request completes', () => {
      http.get('/a').subscribe();
      http.get('/b').subscribe();

      const reqs = httpTesting.match(() => true);
      reqs[0].flush({});
      expect(loadingService.isLoading()).toBe(true);
      reqs[1].flush({});
      expect(loadingService.isLoading()).toBe(false);
    });

    it('a 401 on one request does not block loading from resetting', () => {
      http.get('/a').subscribe({ error: () => {} });
      http.get('/b').subscribe();

      const [ra, rb] = httpTesting.match(() => true);
      ra.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
      rb.flush({});
      expect(loadingService.isLoading()).toBe(false);
    });
  });
});
