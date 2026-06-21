import { TestBed }                                           from '@angular/core/testing';
import { provideRouter, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { authGuard }                                        from '@core/guards/auth.guard';

describe('authGuard', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([])],
    });
  });

  it('always returns true — currently a pass-through guard', () => {
    const result = TestBed.runInInjectionContext(() => authGuard(route, state));
    expect(result).toBe(true);
  });

  it('returns true regardless of the route snapshot', () => {
    const customRoute = { url: [], params: {}, data: {} } as unknown as ActivatedRouteSnapshot;
    const result = TestBed.runInInjectionContext(() => authGuard(customRoute, state));
    expect(result).toBe(true);
  });
});
