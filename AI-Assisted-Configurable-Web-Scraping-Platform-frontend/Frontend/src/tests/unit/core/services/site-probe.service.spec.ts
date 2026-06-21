import { TestBed }                                    from '@angular/core/testing';
import { provideHttpClient }                           from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SiteProbeService }                            from '@core/services/site-probe.service';
import { mockApiResponse, mockProbeResponse }          from '../../../helpers/mock-factories';

describe('SiteProbeService', () => {
  let service: SiteProbeService;
  let http: HttpTestingController;

  const BASE = '/v1/jobs/probe';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SiteProbeService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('probe()', () => {
    it('sends a POST to the probe endpoint with the target URL in the body', () => {
      service.probe('https://example.com').subscribe();

      const req = http.expectOne(BASE);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ url: 'https://example.com' });
      req.flush(mockApiResponse(mockProbeResponse()));
    });

    it('returns the full ApiResponse on success', () => {
      const result = mockProbeResponse({ url: 'https://example.com', reachable: true });

      let response: ReturnType<typeof mockApiResponse<typeof result>> | null | undefined;
      service.probe('https://example.com').subscribe(r => (response = r as any));

      http.expectOne(BASE).flush(mockApiResponse(result));

      expect(response?.data.reachable).toBe(true);
      expect(response?.data.httpStatus).toBe(200);
    });

    it('returns null — does not throw — when the HTTP call fails', () => {
      let response: unknown = undefined;
      let threw = false;

      service.probe('https://unreachable.com').subscribe({
        next:  r => (response = r),
        error: () => (threw = true),
      });

      http.expectOne(BASE).error(new ErrorEvent('NetworkError'));

      expect(threw).toBe(false);
      expect(response).toBeNull();
    });

    it('returns null — does not throw — on a 5xx server error', () => {
      let response: unknown = undefined;

      service.probe('https://broken.com').subscribe(r => (response = r));
      http.expectOne(BASE).flush('Internal Server Error', { status: 500, statusText: 'Server Error' });

      expect(response).toBeNull();
    });

    it('detects Cloudflare protection signal', () => {
      const result = mockProbeResponse({
        signals:    ['CLOUDFLARE', 'LIKELY_JS_RENDERED'],
        httpStatus: 403,
        reachable:  false,
      });

      let response: any;
      service.probe('https://cf-protected.com').subscribe(r => (response = r));
      http.expectOne(BASE).flush(mockApiResponse(result));

      expect(response.data.signals).toContain('CLOUDFLARE');
      expect(response.data.reachable).toBe(false);
    });

    it('handles an unreachable site that requires authentication', () => {
      const result = mockProbeResponse({
        signals:    ['AUTH_REQUIRED'],
        httpStatus: 401,
        reachable:  false,
      });

      let response: any;
      service.probe('https://private-site.com').subscribe(r => (response = r));
      http.expectOne(BASE).flush(mockApiResponse(result));

      expect(response.data.signals).toContain('AUTH_REQUIRED');
    });

    it('handles an empty signals array for a clean public site', () => {
      const result = mockProbeResponse({ signals: [], reachable: true });

      let response: any;
      service.probe('https://clean.com').subscribe(r => (response = r));
      http.expectOne(BASE).flush(mockApiResponse(result));

      expect(response.data.signals).toHaveLength(0);
      expect(response.data.reachable).toBe(true);
    });
  });
});
