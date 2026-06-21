import { TestBed } from '@angular/core/testing';
import { LoadingService } from '@core/services/loading.service';

describe('LoadingService', () => {
  let service: LoadingService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(LoadingService);
  });

  it('starts with loading disabled', () => {
    expect(service.isLoading()).toBe(false);
  });

  it('activates loading on show()', () => {
    service.show();
    expect(service.isLoading()).toBe(true);
  });

  it('keeps loading active until all show() calls are balanced by hide()', () => {
    service.show();
    service.show();
    service.hide();
    expect(service.isLoading()).toBe(true);

    service.hide();
    expect(service.isLoading()).toBe(false);
  });

  it('deactivates loading on a single balanced hide()', () => {
    service.show();
    service.hide();
    expect(service.isLoading()).toBe(false);
  });

  it('extra hide() calls beyond the counter do not cause negative state', () => {
    service.hide();
    service.hide();
    expect(service.isLoading()).toBe(false);
  });

  it('can be toggled on/off multiple times independently', () => {
    service.show();
    service.hide();
    service.show();
    expect(service.isLoading()).toBe(true);
    service.hide();
    expect(service.isLoading()).toBe(false);
  });
});