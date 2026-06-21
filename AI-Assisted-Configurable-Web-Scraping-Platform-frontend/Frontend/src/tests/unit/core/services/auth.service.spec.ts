import { TestBed }                                   from '@angular/core/testing';
import { provideHttpClient }                          from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router }                      from '@angular/router';
import { AuthService }                               from '@core/auth/auth.service';
import { CookieStorageService }                      from '@core/services/cookie-storage.service';

const assignMock = jest.fn();

beforeAll(() => {
  Object.defineProperty(window.location, 'assign', {
    configurable: true,
    writable:     true,
    value:        assignMock,
  });
});

beforeEach(() => assignMock.mockReset());


describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let router: Router;
  let cookieStorage: jest.Mocked<Pick<CookieStorageService, 'getItem' | 'setItem' | 'removeItem'>>;

  beforeEach(() => {
    cookieStorage = {
      getItem:    jest.fn().mockReturnValue(null),
      setItem:    jest.fn(),
      removeItem: jest.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CookieStorageService, useValue: cookieStorage },
      ],
    });

    service = TestBed.inject(AuthService);
    http    = TestBed.inject(HttpTestingController);
    router  = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());


  describe('initial state', () => {
    it('starts unauthenticated with no current user', () => {
      expect(service.currentUser()).toBeNull();
      expect(service.isAuthenticated()).toBe(false);
    });
  });

  // ── initialize() ──────────────────────────────────────────────────────────

  describe('initialize()', () => {
    it('sets the user when the API reports authenticated', async () => {
      const promise = service.initialize();
      http.expectOne('/v1/auth/me').flush({
        authenticated: true,
        id: 'u1',
        username: 'alice',
        email: 'alice@example.com',
        role: 'ADMIN',
      });
      await promise;

      expect(service.currentUser()).toMatchObject({ id: 'u1', username: 'alice', role: 'ADMIN' });
      expect(service.isAuthenticated()).toBe(true);
    });

    it('leaves user as null when the API reports not authenticated', async () => {
      const promise = service.initialize();
      http.expectOne('/v1/auth/me').flush({ authenticated: false });
      await promise;

      expect(service.currentUser()).toBeNull();
      expect(service.isAuthenticated()).toBe(false);
    });

    it('leaves user as null when the API call fails', async () => {
      const promise = service.initialize();
      http.expectOne('/v1/auth/me').error(new ErrorEvent('NetworkError'));
      await promise;

      expect(service.currentUser()).toBeNull();
    });

    it('defaults role to READER when the API omits it', async () => {
      const promise = service.initialize();
      http.expectOne('/v1/auth/me').flush({ authenticated: true, id: 'u2', username: 'bob', email: 'bob@test.com' });
      await promise;

      expect(service.currentUser()?.role).toBe('READER');
    });

    it('is idempotent — a concurrent call while already initializing is a no-op', async () => {
      const p1 = service.initialize();
      const p2 = service.initialize();
      http.expectOne('/v1/auth/me').flush({ authenticated: false });
      await Promise.all([p1, p2]);
    });
  });

  describe('login()', () => {
    it('redirects to the Keycloak login URL', () => {
      cookieStorage.getItem.mockReturnValue('dark');

      service.login();

      expect(assignMock).toHaveBeenCalledWith(expect.stringContaining('/v1/auth/login'));
      expect(assignMock).toHaveBeenCalledWith(expect.stringContaining('theme=dark'));
    });

    it('defaults to the light theme when no cookie is set', () => {
      cookieStorage.getItem.mockReturnValue(null);

      service.login();

      expect(assignMock).toHaveBeenCalledWith(expect.stringContaining('theme=light'));
    });
  });


  describe('register()', () => {
    it('redirects to the Keycloak register URL', () => {
      service.register();

      expect(assignMock).toHaveBeenCalledWith(expect.stringContaining('/v1/auth/register'));
    });
  });


  describe('logout()', () => {
    async function logIn(): Promise<void> {
      const p = service.initialize();
      http.expectOne('/v1/auth/me').flush({
        authenticated: true, id: 'u1', username: 'alice', email: 'a@a.com', role: 'OPERATOR',
      });
      await p;
    }

    it('clears the current user and navigates home after a successful logout', async () => {
      await logIn();
      expect(service.isAuthenticated()).toBe(true);

      const promise = service.logout();
      http.expectOne('/v1/auth/logout').flush({});
      await promise;

      expect(service.currentUser()).toBeNull();
      expect(service.isAuthenticated()).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/']);
    });

    it('still clears the user even when the logout API call fails', async () => {
      await logIn();

      const promise = service.logout();
      http.expectOne('/v1/auth/logout').error(new ErrorEvent('NetworkError'));
      await promise;

      expect(service.currentUser()).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith(['/']);
    });
  });
});
