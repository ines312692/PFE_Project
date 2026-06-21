import {Component, signal, inject, computed} from '@angular/core';
import {RouterOutlet, Router, NavigationEnd} from '@angular/router';
import {CommonModule} from '@angular/common';
import {NavbarComponent} from './layout/navbar/navbar.component';
import {SidebarComponent} from './layout/sidebar/sidebar.component';
import {FooterComponent} from './layout/footer/footer.component';
import {toSignal} from '@angular/core/rxjs-interop';
import {filter, map} from 'rxjs';
import {NotificationToastComponent} from './shared/components/notification-toast/notification-toast.component';
import {AssistantComponent} from './shared/components/assistant/assistant.component';


const AUTH_ROUTES = ['/login', '/register'];
const PUBLIC_ROUTES = ['/login', '/register', '/', '/docs'];

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent, SidebarComponent, FooterComponent, NotificationToastComponent, AssistantComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})

export class AppComponent {
  private router = inject(Router);
  sidebarForceExpand = signal(false);
  isLoggedIn = signal({ role: 'ADMIN', fullName: 'Ines Tmimi', email: 'ines@elyadata.com' });

  currentUrl = toSignal(
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
      map(e => (e as NavigationEnd).urlAfterRedirects)
    ),
    { initialValue: this.router.url }
  );

  private currentPath = computed(() => {
    const url = this.currentUrl();
    return url.split('?')[0].split('#')[0] || '/';
  });

  isAuthRoute = computed(() =>
    AUTH_ROUTES.some(r => this.currentPath().startsWith(r))
  );

  isPublicRoute = computed(() =>
    PUBLIC_ROUTES.some(r => this.currentPath() === r)
  );

  isLanding = computed(() => this.currentPath() === '/');

  onSidebarToggle(): void {
    this.sidebarForceExpand.update(v => !v);
  }

  onSidebarExpandedChange(expanded: boolean): void {
    if (!expanded) this.sidebarForceExpand.set(false);
  }
}
