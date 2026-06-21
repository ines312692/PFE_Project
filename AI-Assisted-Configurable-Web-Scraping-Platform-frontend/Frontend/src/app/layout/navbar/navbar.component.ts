import {
  Component, output, computed, inject, signal,
  HostListener, OnInit, OnDestroy, ElementRef, ViewChild
} from '@angular/core';
import {RouterLink, Router} from '@angular/router';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {DomSanitizer, SafeHtml} from '@angular/platform-browser';
import {AuthService} from '../../core/auth/auth.service';
import {ThemeService} from '../../core/services/theme.service';
import {NotificationService} from '../../core/services/notification.service';
import {ToastService} from '../../core/services/toast.service';
import {UserService} from '../../core/services/user.service';
import {NAV_ITEMS} from './nav-items.config';
import {BreadcrumbComponent} from '../breadcrumb/breadcrumb.component';

interface SearchResultItem {
  label: string;
  route: string;
  icon: SafeHtml;
}

interface SearchResultGroup {
  label: string;
  items: SearchResultItem[];
}

const ICON_MAP: Record<string, string> = {
  dashboard: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>`,
  project: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>`,
  work: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/></svg>`,
  template: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>`,
  monitor_heart: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>`,
  schema: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>`,
  hub: `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14M4.93 4.93a10 10 0 0 0 0 14.14"/></svg>`,
};

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, BreadcrumbComponent],
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.scss'],
})
export class NavbarComponent implements OnInit, OnDestroy {

  @ViewChild('searchInput') searchInputRef!: ElementRef<HTMLInputElement>;

  sidebarToggle = output<void>();

  private auth = inject(AuthService);
  private toast = inject(ToastService);
  private router = inject(Router);
  private sanitizer = inject(DomSanitizer);
  private userService = inject(UserService);
  theme = inject(ThemeService);
  notifSvc = inject(NotificationService);
  themePanelOpen = signal(false);
  cadeauOpen = signal(false);

  toggleCadeau(): void {
    this.cadeauOpen.update(v => !v);
    this.dropdownOpen.set(false);
    this.notifOpen.set(false);
    this.themePanelOpen.set(false);
  }

  toggleThemePanel(): void {
    this.themePanelOpen.update(v => !v);
    this.dropdownOpen.set(false);
    this.notifOpen.set(false);
  }

  setTheme(mode: 'light' | 'dark' | 'system'): void {
    this.theme.setMode(mode);
    this.themePanelOpen.set(false);
  }

  currentUser = computed(() => {
    const user = this.auth.currentUser();
    const profile = this.userService.profile();
    return {
      username: user?.username || 'User',
      role: user?.role || 'READER',
      avatarUrl: profile?.avatarUrl ?? null,
    };
  });

  dropdownOpen = signal(false);
  notifOpen = signal(false);
  searchFocused = signal(false);
  searchQuery = signal('');
  activeIndex = signal(-1);

  private debounceTimer: ReturnType<typeof setTimeout> | null = null;

  private safeIcon(key: string): SafeHtml {
    const raw = ICON_MAP[key] ?? ICON_MAP['work'];
    return this.sanitizer.bypassSecurityTrustHtml(raw);
  }

  flatResults = computed<SearchResultItem[]>(() =>
    this.searchResults().flatMap(g => g.items)
  );

  searchResults = computed<SearchResultGroup[]>(() => {
    const q = this.searchQuery().toLowerCase().trim();
    if (!q) return [];

    const matched = NAV_ITEMS.filter(item =>
      item.label.toLowerCase().includes(q) ||
      item.route.toLowerCase().includes(q)
    ).map(item => ({
      label: item.label,
      route: item.route,
      icon: this.safeIcon(item.icon),
    }));

    return matched.length ? [{label: 'Navigation', items: matched}] : [];
  });

  highlight(text: string): SafeHtml {
    const q = this.searchQuery().trim();
    if (!q) return this.sanitizer.bypassSecurityTrustHtml(text);
    const escaped = q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const highlighted = text.replace(
      new RegExp(`(${escaped})`, 'gi'),
      '<mark class="search-highlight">$1</mark>'
    );
    return this.sanitizer.bypassSecurityTrustHtml(highlighted);
  }

  globalIndex(groupIdx: number, itemIdx: number): number {
    let idx = 0;
    const groups = this.searchResults();
    for (let g = 0; g < groupIdx; g++) idx += groups[g].items.length;
    return idx + itemIdx;
  }

  ngOnInit(): void {
    this.notifSvc.connect();
    if (!this.userService.profile()) {
      this.userService.getMyProfile().subscribe();
    }
  }

  ngOnDestroy(): void {
    this.notifSvc.disconnect();
  }

  toggleSidebar(): void {
    this.sidebarToggle.emit();
  }

  toggleDropdown(): void {
    this.dropdownOpen.update(v => !v);
    this.notifOpen.set(false);
  }

  logout(): void {
    this.notifSvc.disconnect();
    this.auth.logout();
  }

  toggleNotifications(): void {
    this.notifOpen.update(v => !v);
    this.dropdownOpen.set(false);
  }

  markAllAsRead(): void {
    this.notifSvc.markAllAsRead();
    this.toast.show('All notifications marked as viewed', 'success');
  }

  onSearchInput(): void {
    this.activeIndex.set(-1);
    if (this.debounceTimer) clearTimeout(this.debounceTimer);
    this.debounceTimer = setTimeout(() => {
    }, 250);
  }

  onSearchBlur(): void {
    setTimeout(() => this.searchFocused.set(false), 150);
  }

  clearSearch(): void {
    this.searchQuery.set('');
    this.activeIndex.set(-1);
    this.searchFocused.set(false);
    this.searchInputRef?.nativeElement.blur();
  }

  navigateToActive(): void {
    const item = this.flatResults()[this.activeIndex()];
    if (item) {
      this.router.navigateByUrl(item.route as string);
      this.clearSearch();
    }
  }

  openNotification(n: { id: string }): void {
    this.notifSvc.markAsRead(n.id);
    this.notifOpen.set(false);
    this.router.navigate(['/notifications'], {queryParams: {id: n.id}});
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
      e.preventDefault();
      this.searchInputRef?.nativeElement.focus();
      this.searchFocused.set(true);
      return;
    }

    if (this.searchFocused() && this.flatResults().length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        this.activeIndex.update(i => Math.min(i + 1, this.flatResults().length - 1));
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        this.activeIndex.update(i => Math.max(i - 1, 0));
        return;
      }
      if (e.key === 'Enter') {
        e.preventDefault();
        this.navigateToActive();
        return;
      }
    }

    if (e.key === 'Escape') {
      this.clearSearch();
      this.dropdownOpen.set(false);
      this.notifOpen.set(false);
      this.themePanelOpen.set(false);
      this.cadeauOpen.set(false);
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(e: MouseEvent): void {
    const target = e.target as HTMLElement;
    if (!target.closest('.navbar__avatar-wrap')) this.dropdownOpen.set(false);
    if (!target.closest('.navbar__notif-wrap')) this.notifOpen.set(false);
    if (!target.closest('.navbar__theme-wrap')) this.themePanelOpen.set(false);
    if (!target.closest('.navbar__cadeau-wrap')) this.cadeauOpen.set(false);
    if (!target.closest('.navbar__search-wrap')) {
      this.searchFocused.set(false);
      this.activeIndex.set(-1);
    }
  }
}
