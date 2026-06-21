import {Routes} from '@angular/router';
import {inject} from '@angular/core';
import {Router} from '@angular/router';
import {authGuard} from './core/guards/auth.guard';
import {roleGuard} from './core/guards/role.guard';
import {AuthService} from './core/auth/auth.service';

const landingOrHomeGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAuthenticated() ? router.createUrlTree(['/home']) : true;
};

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [landingOrHomeGuard],
    loadComponent: () =>
      import('./features/landing/landing.component').then(m => m.LandingComponent),
  },

  {
    path: 'docs',
    loadComponent: () =>
      import('./features/docs/docs.component').then(m => m.DocsComponent),
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then(m => m.RegisterComponent),
  },
  {
    path: 'callback',
    loadComponent: () =>
      import('./features/auth/callback/callback.component').then(m => m.CallbackComponent),
  },

  /* ── Authenticated ───────────────────────────────────────────── */
  {
    path: 'home',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/home/home.component').then(m => m.HomeComponent),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent),
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/profile/profile.component').then(m => m.ProfileComponent),
  },
  {
    path: 'projects',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/projects/projects.routes').then(m => m.projectsRoutes),
  },
  {
    path: 'jobs',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/jobs/jobs.routes').then(m => m.jobsRoutes),
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/settings/settings.component').then(m => m.SettingsComponent),
  },
  {
    path: 'notifications',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/notifications/notifications.routes').then(m => m.NOTIFICATION_ROUTES),
  },
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard],
    data: {roles: ['ADMIN']},
    loadChildren: () =>
      import('./features/admin/admin.routes').then(m => m.adminRoutes),
  },
  {
    path: 'help',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/help/help.component').then(m => m.HelpComponent),
  },

  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found.component').then(m => m.NotFoundComponent),
  },
];
