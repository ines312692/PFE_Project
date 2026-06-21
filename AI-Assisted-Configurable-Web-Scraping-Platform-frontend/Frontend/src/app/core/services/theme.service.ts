import {Injectable, signal, effect, PLATFORM_ID, inject} from '@angular/core';
import {isPlatformBrowser} from '@angular/common';
import {CookieStorageService} from './cookie-storage.service';


export type Theme = 'light' | 'dark' | 'system';

@Injectable({providedIn: 'root'})
export class ThemeService {

  private readonly STORAGE_KEY = 'elyadata-theme';
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly cookieStore = inject(CookieStorageService);

  theme = signal<Theme>(this.getInitialTheme());

  constructor() {
    effect(() => {
      const t = this.theme();
      if (!this.isBrowser) return;
      document.documentElement.classList.toggle('dark', t === 'dark');
      document.documentElement.classList.toggle('light', t === 'light');
      this.cookieStore.setItem(this.STORAGE_KEY, t);
    });
  }

  toggle(): void {
    this.theme.update(t => t === 'dark' ? 'light' : 'dark');
  }

  isDark(): boolean {
    return this.theme() === 'dark';
  }

  private getInitialTheme(): Theme {
    if (!this.isBrowser) return 'light';
    const fromCookie = this.cookieStore.getItem(this.STORAGE_KEY) as Theme | null;
    if (fromCookie === 'dark' || fromCookie === 'light') return fromCookie;
    const fromStorage = localStorage.getItem(this.STORAGE_KEY) as Theme | null;
    if (fromStorage === 'dark' || fromStorage === 'light') {
      this.cookieStore.setItem(this.STORAGE_KEY, fromStorage);
      localStorage.removeItem(this.STORAGE_KEY);
      return fromStorage;
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  setMode(mode: Theme): void {
    this.theme.set(mode);
    localStorage.setItem(this.STORAGE_KEY, mode);
  }
}
