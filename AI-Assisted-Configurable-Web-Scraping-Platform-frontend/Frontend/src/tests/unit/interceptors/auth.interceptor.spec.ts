import { TestBed }                                          from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { authInterceptor }                                 from '@core/interceptors/auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http       = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  // ── withCredentials ────────────────────────────────────────────────────────

  it('adds withCredentials: true to every outgoing request', () => {
    http.get('/api/data').subscribe();
    const req = httpTesting.expectOne('/api/data');
    expect(req.request.withCredentials).toBe(true);
    req.flush({});
  });

  it('adds withCredentials even on POST requests', () => {
    http.post('/api/resource', { key: 'value' }).subscribe();
    const req = httpTesting.expectOne('/api/resource');
    expect(req.request.withCredentials).toBe(true);
    req.flush({});
  });

  it('does not mutate other request properties', () => {
    http.get('/api/data', { headers: { 'X-Custom': 'header' } }).subscribe();
    const req = httpTesting.expectOne('/api/data');
    expect(req.request.headers.get('X-Custom')).toBe('header');
    req.flush({});
  });

  // ── pass-through ───────────────────────────────────────────────────────────

  it('passes the response through to the subscriber unchanged', () => {
    let result: unknown;
    http.get('/api/data').subscribe(r => (result = r));
    httpTesting.expectOne('/api/data').flush({ id: 1, name: 'test' });
    expect(result).toEqual({ id: 1, name: 'test' });
  });

  // ── error pass-through ─────────────────────────────────────────────────────

  it('rethrows HTTP errors without swallowing them', () => {
    let error: unknown;
    http.get('/api/data').subscribe({ error: e => (error = e) });
    httpTesting.expectOne('/api/data').error(new ErrorEvent('NetworkError'));
    expect(error).toBeTruthy();
  });

  it('rethrows 4xx errors to the subscriber', () => {
    let threwError = false;
    http.get('/api/data').subscribe({ error: () => (threwError = true) });
    httpTesting.expectOne('/api/data').flush('Not Found', { status: 404, statusText: 'Not Found' });
    expect(threwError).toBe(true);
  });
});
