import { TestBed }                                           from '@angular/core/testing';
import { provideRouter, Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { signal }                                           from '@angular/core';
import { roleGuard }                                        from '@core/guards/role.guard';
import { AuthService }                                      from '@core/auth/auth.service';
import { UserRole }                                         from '@core/models/auth.model';


function makeUserSignal(role: UserRole | null) {
  const user = role
    ? { id: 'u1', username: 'test', email: 'test@test.com', role }
    : null;
  return signal(user);
}

const route = {} as ActivatedRouteSnapshot;
const state = {} as RouterStateSnapshot;


describe('roleGuard', () => {
  let router: Router;
  let authMock: { currentUser: ReturnType<typeof signal> };

  beforeEach(() => {
    authMock = { currentUser: makeUserSignal(null) };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authMock },
      ],
    });

    router = TestBed.inject(Router);
    jest.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  describe('access granted', () => {
    it('returns true when the user has the sole allowed role', () => {
      authMock.currentUser = makeUserSignal('ADMIN');
      const result = TestBed.runInInjectionContext(() =>
        roleGuard(['ADMIN'])(route, state)
      );
      expect(result).toBe(true);
    });

    it('returns true when the user role is one of several allowed roles', () => {
      authMock.currentUser = makeUserSignal('OPERATOR');
      const result = TestBed.runInInjectionContext(() =>
        roleGuard(['ADMIN', 'OPERATOR'])(route, state)
      );
      expect(result).toBe(true);
    });

    it('does not navigate when access is granted', () => {
      authMock.currentUser = makeUserSignal('ADMIN');
      TestBed.runInInjectionContext(() => roleGuard(['ADMIN'])(route, state));
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('grants READER access when READER is in the allowed list', () => {
      authMock.currentUser = makeUserSignal('READER');
      const result = TestBed.runInInjectionContext(() =>
        roleGuard(['READER', 'OPERATOR', 'ADMIN'])(route, state)
      );
      expect(result).toBe(true);
    });
  });


  describe('access denied — insufficient role', () => {
    it('returns false when the user role is not in the allowed list', () => {
      authMock.currentUser = makeUserSignal('READER');
      const result = TestBed.runInInjectionContext(() =>
        roleGuard(['ADMIN'])(route, state)
      );
      expect(result).toBe(false);
    });

    it('navigates to /dashboard when the role is not allowed', () => {
      authMock.currentUser = makeUserSignal('READER');
      TestBed.runInInjectionContext(() => roleGuard(['ADMIN'])(route, state));
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('returns false when OPERATOR tries to access an ADMIN-only route', () => {
      authMock.currentUser = makeUserSignal('OPERATOR');
      const result = TestBed.runInInjectionContext(() =>
        roleGuard(['ADMIN'])(route, state)
      );
      expect(result).toBe(false);
    });
  });


  describe('access denied — unauthenticated user', () => {
    it('returns false when currentUser is null', () => {
      authMock.currentUser = makeUserSignal(null);
      const result = TestBed.runInInjectionContext(() =>
        roleGuard(['ADMIN'])(route, state)
      );
      expect(result).toBe(false);
    });

    it('navigates to /dashboard when the user is not authenticated', () => {
      authMock.currentUser = makeUserSignal(null);
      TestBed.runInInjectionContext(() => roleGuard(['ADMIN'])(route, state));
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });

    it('navigates to /dashboard even for an open role list when unauthenticated', () => {
      authMock.currentUser = makeUserSignal(null);
      TestBed.runInInjectionContext(() =>
        roleGuard(['ADMIN', 'OPERATOR', 'READER'])(route, state)
      );
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });
  });

  describe('edge cases', () => {
    it('returns false for an empty allowed roles list regardless of user role', () => {
      authMock.currentUser = makeUserSignal('ADMIN');
      const result = TestBed.runInInjectionContext(() =>
        roleGuard([])(route, state)
      );
      expect(result).toBe(false);
    });

    it('navigates to /dashboard for an empty allowed roles list', () => {
      authMock.currentUser = makeUserSignal('ADMIN');
      TestBed.runInInjectionContext(() => roleGuard([])(route, state));
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    });
  });
});
