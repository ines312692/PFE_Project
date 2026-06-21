import { TestBed }                                    from '@angular/core/testing';
import { provideHttpClient }                           from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { UserService }                                 from '@core/services/user.service';
import { mockApiResponse, mockUserProfile }            from '../../../helpers/mock-factories';

describe('UserService', () => {
  let service: UserService;
  let http: HttpTestingController;

  const ACCOUNT_URL  = '/v1/account';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());


  describe('initial state', () => {
    it('starts with a null profile', () => {
      expect(service.profile()).toBeNull();
    });

    it('starts not loading', () => {
      expect(service.loading()).toBe(false);
    });
  });


  describe('getMyProfile()', () => {
    it('sets loading to true while the request is in-flight', () => {
      service.getMyProfile().subscribe();
      expect(service.loading()).toBe(true);
      http.expectOne(ACCOUNT_URL).flush(mockApiResponse(mockUserProfile()));
    });

    it('sets the profile signal and clears loading on success', () => {
      const profile = mockUserProfile({ id: 'u42', username: 'alice', role: 'ADMIN' });

      service.getMyProfile().subscribe();
      http.expectOne(ACCOUNT_URL).flush(mockApiResponse(profile));

      expect(service.profile()).toEqual(profile);
      expect(service.loading()).toBe(false);
    });

    it('clears loading on HTTP error', () => {
      service.getMyProfile().subscribe({ error: () => {} });
      http.expectOne(ACCOUNT_URL).error(new ErrorEvent('NetworkError'));
      expect(service.loading()).toBe(false);
    });

    it('does not update profile on HTTP error', () => {
      service.getMyProfile().subscribe({ error: () => {} });
      http.expectOne(ACCOUNT_URL).error(new ErrorEvent('NetworkError'));
      expect(service.profile()).toBeNull();
    });

    it('sends a GET request to the correct URL', () => {
      service.getMyProfile().subscribe();
      const req = http.expectOne(ACCOUNT_URL);
      expect(req.request.method).toBe('GET');
      req.flush(mockApiResponse(mockUserProfile()));
    });

    it('overwrites a previously loaded profile on a second call', () => {
      const first  = mockUserProfile({ username: 'alice' });
      const second = mockUserProfile({ username: 'bob'   });

      service.getMyProfile().subscribe();
      http.expectOne(ACCOUNT_URL).flush(mockApiResponse(first));
      expect(service.profile()?.username).toBe('alice');

      service.getMyProfile().subscribe();
      http.expectOne(ACCOUNT_URL).flush(mockApiResponse(second));
      expect(service.profile()?.username).toBe('bob');
    });
  });
});
