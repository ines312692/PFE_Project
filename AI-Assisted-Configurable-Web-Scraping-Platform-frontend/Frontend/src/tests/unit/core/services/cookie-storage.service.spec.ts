import { TestBed }            from '@angular/core/testing';
import { PLATFORM_ID }        from '@angular/core';
import { CookieStorageService } from '@core/services/cookie-storage.service';

function clearCookie(key: string): void {
  document.cookie = `${encodeURIComponent(key)}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
}

describe('CookieStorageService — browser environment', () => {
  let service: CookieStorageService;
  const KEY = 'test-key';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: PLATFORM_ID, useValue: 'browser' }],
    });
    service = TestBed.inject(CookieStorageService);
    clearCookie(KEY);
  });

  afterEach(() => clearCookie(KEY));

  it('returns null when the cookie does not exist', () => {
    expect(service.getItem(KEY)).toBeNull();
  });

  it('stores and retrieves a value', () => {
    service.setItem(KEY, 'dark');
    expect(service.getItem(KEY)).toBe('dark');
  });

  it('overwrites an existing value', () => {
    service.setItem(KEY, 'light');
    service.setItem(KEY, 'dark');
    expect(service.getItem(KEY)).toBe('dark');
  });

  it('removes a value via removeItem()', () => {
    service.setItem(KEY, 'dark');
    service.removeItem(KEY);
    expect(service.getItem(KEY)).toBeNull();
  });
});

describe('CookieStorageService — server (SSR) environment', () => {
  let service: CookieStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: PLATFORM_ID, useValue: 'server' }],
    });
    service = TestBed.inject(CookieStorageService);
  });

  it('stores values in the in-memory fallback store', () => {
    service.setItem('ssr-key', 'value');
    expect(service.getItem('ssr-key')).toBe('value');
  });

  it('returns null for unknown keys', () => {
    expect(service.getItem('unknown')).toBeNull();
  });

  it('removes values from the in-memory store', () => {
    service.setItem('ssr-key', 'value');
    service.removeItem('ssr-key');
    expect(service.getItem('ssr-key')).toBeNull();
  });
});
