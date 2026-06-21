import {Component, input, output, signal, computed, inject, effect} from '@angular/core';
import {RouterLink, RouterLinkActive} from '@angular/router';
import {CommonModule} from '@angular/common';
import {AuthService} from '../../core/auth/auth.service';
import {ThemeService} from '../../core/services/theme.service';
import {UserService} from '../../core/services/user.service';
import {NAV_ITEMS, NavItem} from '../navbar/nav-items.config';

const SECTION_MAP: Record<string, string> = {
  home: 'Main',
  dashboard: 'Main',
  project: 'Main',
  work: 'Main',
  template: 'Main',
  monitor_heart: 'Main',
  schema: 'Configuration',
  hub: 'Configuration',
  users: 'Administration',
};

const SECTION_ORDER = ['Main', 'Configuration', 'Administration'];

interface SidebarSection {
  label: string;
  items: NavItem[];
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {

  forceExpand = input<boolean>(false);
  expandedChange = output<boolean>();

  private auth = inject(AuthService);
  private userService = inject(UserService);
  theme = inject(ThemeService);

  private _expanded = signal(false);
  isExpanded = computed(() => this._expanded() || this.forceExpand());

  currentUser = computed(() => {
    const user = this.auth.currentUser();
    const profile = this.userService.profile();
    return {
      username: user?.username || 'User',
      role: user?.role || 'READER',
      avatarUrl: profile?.avatarUrl ?? null,
    };
  });

  navSections = computed<SidebarSection[]>(() => {
    const role = this.currentUser().role;

    const map = new Map<string, NavItem[]>();

    for (const item of NAV_ITEMS) {
      const allowed = item.roles.includes('*') || item.roles.includes(role);
      if (!allowed) continue;

      const label = SECTION_MAP[item.icon] ?? 'Main';
      if (!map.has(label)) map.set(label, []);
      map.get(label)!.push(item);
    }

    return SECTION_ORDER
      .filter(l => map.has(l))
      .map(l => ({label: l, items: map.get(l)!}));
  });

  constructor() {
    effect(() => {
      const expanded = this.isExpanded();
      document.documentElement.style.setProperty('--sidebar-width', expanded ? '260px' : '50px');
      document.documentElement.classList.toggle('sidebar-open', expanded);
    });
  }

  expand(): void {
    this._expanded.set(true);
    this.expandedChange.emit(true);
  }

  collapse(): void {
    this._expanded.set(false);
    this.expandedChange.emit(false);
  }

  onLinkClick(): void {
    if (window.innerWidth <= 768) {
      this.collapse();
    }
  }

  logout(): void {
    this.auth.logout();
  }
}
