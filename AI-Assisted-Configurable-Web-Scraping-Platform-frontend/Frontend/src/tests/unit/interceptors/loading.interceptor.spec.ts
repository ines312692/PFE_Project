import { TestBed }                                          from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { loadingInterceptor }                              from '@core/interceptors/loading.interceptor';
import { LoadingService }                                  from '@core/services/loading.service';

describe('loadingInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let loadingService: LoadingService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([loadingInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http           = TestBed.inject(HttpClient);
    httpTesting    = TestBed.inject(HttpTestingController);
    loadingService = TestBed.inject(LoadingService);
  });

  afterEach(() => httpTesting.verify());

  // ── show() on start ────────────────────────────────────────────────────────

  describe('request start', () => {
    it('calls loading.show() when a request is initiated', () => {
      jest.spyOn(loadingService, 'show');
      http.get('/test').subscribe();
      expect(loadingService.show).toHaveBeenCalledTimes(1);
      httpTesting.expectOne('/test').flush({});
    });

    it('sets isLoading to true while the request is in flight', () => {
      http.get('/test').subscribe();
      expect(loadingService.isLoading()).toBe(true);
      httpTesting.expectOne('/test').flush({});
    });
  });

  // ── hide() on finalize ─────────────────────────────────────────────────────

  describe('request completion', () => {
    it('calls loading.hide() after a successful response', () => {
      jest.spyOn(loadingService, 'hide');
      http.get('/test').subscribe();
      httpTesting.expectOne('/test').flush({ data: 'ok' });
      expect(loadingService.hide).toHaveBeenCalledTimes(1);
    });

    it('sets isLoading to false after completion', () => {
      http.get('/test').subscribe();
      httpTesting.expectOne('/test').flush({});
      expect(loadingService.isLoading()).toBe(false);
    });

    it('calls loading.hide() even when the request fails', () => {
      jest.spyOn(loadingService, 'hide');
      http.get('/test').subscribe({ error: () => {} });
      httpTesting.expectOne('/test').error(new ErrorEvent('NetworkError'));
      expect(loadingService.hide).toHaveBeenCalledTimes(1);
    });

    it('sets isLoading to false after an HTTP error', () => {
      http.get('/test').subscribe({ error: () => {} });
      httpTesting.expectOne('/test').flush('Server Error', { status: 500, statusText: 'Error' });
      expect(loadingService.isLoading()).toBe(false);
    });
  });

  // ── concurrent requests ────────────────────────────────────────────────────

  describe('concurrent requests', () => {
    it('keeps isLoading true while at least one request is pending', () => {
      http.get('/a').subscribe();
      http.get('/b').subscribe();

      expect(loadingService.isLoading()).toBe(true);

      httpTesting.expectOne('/a').flush({});
      expect(loadingService.isLoading()).toBe(true); // /b still pending

      httpTesting.expectOne('/b').flush({});
      expect(loadingService.isLoading()).toBe(false);
    });

    it('calls show() once per request', () => {
      jest.spyOn(loadingService, 'show');
      http.get('/a').subscribe();
      http.get('/b').subscribe();
      expect(loadingService.show).toHaveBeenCalledTimes(2);
      httpTesting.expectOne('/a').flush({});
      httpTesting.expectOne('/b').flush({});
    });

    it('calls hide() once per request', () => {
      jest.spyOn(loadingService, 'hide');
      http.get('/a').subscribe();
      http.get('/b').subscribe();
      httpTesting.expectOne('/a').flush({});
      httpTesting.expectOne('/b').flush({});
      expect(loadingService.hide).toHaveBeenCalledTimes(2);
    });

    it('handles a mix of success and error responses', () => {
      http.get('/ok').subscribe();
      http.get('/fail').subscribe({ error: () => {} });

      httpTesting.expectOne('/ok').flush({});
      httpTesting.expectOne('/fail').error(new ErrorEvent('Fail'));

      expect(loadingService.isLoading()).toBe(false);
    });
  });
});
