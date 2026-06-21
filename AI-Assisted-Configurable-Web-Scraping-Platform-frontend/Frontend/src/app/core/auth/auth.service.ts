import {Injectable, signal, computed, PLATFORM_ID, inject} from '@angular/core';
import {isPlatformBrowser} from '@angular/common';
import {HttpClient} from '@angular/common/http';
import {Router} from '@angular/router';
import {UserProfile, UserRole} from '../models/user.model';
import {CookieStorageService} from '../services/cookie-storage.service';
import {environment} from '../../../environments/environment';

const THEME_KEY = 'elyadata-theme';

@Injectable({providedIn: 'root'})
export class AuthService {

  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly cookieStorage = inject(CookieStorageService);

  private readonly apiUrl = environment.apiUrl;

  private _currentUser = signal<UserProfile | null>(null);
  private _initialized = signal(false);
  private _initializing = signal(false);

  readonly currentUser = this._currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this._currentUser() !== null);

  readonly role = computed(() => this._currentUser()?.role ?? null);

  hasRole(...roles: UserRole[]): boolean {
    const current = this._currentUser()?.role;
    return current != null && roles.includes(current);
  }
  async initialize(): Promise<void> {
    if (!this.isBrowser || this._initializing()) return;

    this._initializing.set(true);

    try {
      const user = await this.http
        .get<MeResponse>(`${this.apiUrl}/v1/auth/me`, {withCredentials: true})
        .toPromise();

      if (user?.authenticated) {
        this._currentUser.set({
          id: user.id ?? '',
          username: user.username ?? '',
          email: user.email ?? '',
          role: (user.role as UserRole) ?? 'READER',
          enabled: true,
          createdAt: '',
          updatedAt: '',
        });
      } else {
        this._currentUser.set(null);
      }
    } catch (err) {
      console.warn('[AuthService] /me failed (user not authenticated):', err);
      this._currentUser.set(null);
    } finally {
      this._initialized.set(true);
      this._initializing.set(false);
    }
  }

  login(): void {
    if (!this.isBrowser) return;
    const theme = this.getTheme();
    window.location.href = `${this.apiUrl}/v1/auth/login?theme=${theme}`;
  }

  register(): void {
    if (!this.isBrowser) return;
    const theme = this.getTheme();
    window.location.href = `${this.apiUrl}/v1/auth/register?theme=${theme}`;
  }

  async logout(): Promise<void> {
    if (!this.isBrowser) return;

    try {
      await this.http
        .post(`${this.apiUrl}/v1/auth/logout`, {}, {withCredentials: true})
        .toPromise();
    } catch {
      // Best effort
    }

    this._currentUser.set(null);
    this.router.navigate(['/']);
  }

  private getTheme(): string {
    if (!this.isBrowser) return 'light';
    return this.cookieStorage.getItem(THEME_KEY) ?? 'light';
  }
}

interface MeResponse {
  authenticated: boolean;
  id?: string;
  username?: string;
  email?: string;
  role?: string;
}
