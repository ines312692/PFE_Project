import { TestBed }                                          from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router }                           from '@angular/router';
import { errorInterceptor }                                from '@core/interceptors/error.interceptor';
import { AuthService }                                     from '@core/auth/auth.service';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let router: Router;
  let authService: { logout: jest.Mock };

  beforeEach(() => {
    authService = { logout: jest.fn().mockResolvedValue(undefined) };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authService },
      ],
    });

    http        = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    router      = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpTesting.verify());

  // ── 401 handling ───────────────────────────────────────────────────────────

  describe('401 Unauthorized', () => {
    it('calls auth.logout()', () => {
      http.get('/api/secret').subscribe({ error: () => {} });
      httpTesting.expectOne('/api/secret')
        .flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
      expect(authService.logout).toHaveBeenCalledTimes(1);
    });

    it('does NOT navigate to /dashboard', () => {
      http.get('/api/secret').subscribe({ error: () => {} });
      httpTesting.expectOne('/api/secret')
        .flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('rethrows the 401 error to the subscriber', () => {
      let error: any;
      http.get('/api/secret').subscribe({ error: e => (error = e) });
      httpTesting.expectOne('/api/secret')
        .flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
      expect(error.status).toBe(401);
    });
  });

  // ── 403 handling ───────────────────────────────────────────────────────────

  describe('403 Forbidden', () => {
    it('navigates to /dashboard', () => {
      http.get('/api/admin').subscribe({ error: () => {} });
      httpTesting.expectOne('/api/admin')
        .flush('Forbidden', { status: 403, statusText: 'Forbidden' });
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('does NOT call auth.logout()', () => {
      http.get('/api/admin').subscribe({ error: () => {} });
      httpTesting.expectOne('/api/admin')
        .flush('Forbidden', { status: 403, statusText: 'Forbidden' });
      expect(authService.logout).not.toHaveBeenCalled();
    });

    it('rethrows the 403 error to the subscriber', () => {
      let error: any;
      http.get('/api/admin').subscribe({ error: e => (error = e) });
      httpTesting.expectOne('/api/admin')
        .flush('Forbidden', { status: 403, statusText: 'Forbidden' });
      expect(error.status).toBe(403);
    });
  });

  // ── other errors ───────────────────────────────────────────────────────────

  describe('other HTTP errors', () => {
    it.each([404, 422, 500, 503])('rethrows %i without any side effects', status => {
      let error: any;
      http.get('/api/data').subscribe({ error: e => (error = e) });
      httpTesting.expectOne('/api/data')
        .flush('Error', { status, statusText: 'Error' });

      expect(authService.logout).not.toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
      expect(error.status).toBe(status);
    });

    it('rethrows network errors without side effects', () => {
      let error: unknown;
      http.get('/api/data').subscribe({ error: e => (error = e) });
      httpTesting.expectOne('/api/data').error(new ErrorEvent('NetworkError'));

      expect(authService.logout).not.toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
      expect(error).toBeTruthy();
    });
  });

  // ── success pass-through ───────────────────────────────────────────────────

  describe('successful responses', () => {
    it('passes 2xx responses through unchanged', () => {
      let result: unknown;
      http.get('/api/data').subscribe(r => (result = r));
      httpTesting.expectOne('/api/data').flush({ ok: true });

      expect(result).toEqual({ ok: true });
      expect(authService.logout).not.toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });
});
