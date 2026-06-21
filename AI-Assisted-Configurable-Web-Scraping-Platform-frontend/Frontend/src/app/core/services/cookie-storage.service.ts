import {Injectable, PLATFORM_ID, inject} from '@angular/core';
import {isPlatformBrowser} from '@angular/common';


@Injectable({providedIn: 'root'})
export class CookieStorageService {

  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly memoryStore = new Map<string, string>();

  private readonly TTL_SECONDS = 365 * 24 * 60 * 60;

  getItem(key: string): string | null {
    if (!this.isBrowser) return this.memoryStore.get(key) ?? null;

    const name = encodeURIComponent(key) + '=';
    for (let c of document.cookie.split(';')) {
      c = c.trim();
      if (c.startsWith(name)) {
        return decodeURIComponent(c.substring(name.length));
      }
    }
    return null;
  }

  setItem(key: string, data: string): void {
    if (!this.isBrowser) {
      this.memoryStore.set(key, data);
      return;
    }

    const expires = new Date(Date.now() + this.TTL_SECONDS * 1000).toUTCString();
    document.cookie =
      `${encodeURIComponent(key)}=${encodeURIComponent(data)}` +
      `; expires=${expires}` +
      `; path=/` +
      `; SameSite=Strict`;
  }
}
