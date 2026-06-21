import {Routes} from '@angular/router';
import {AdminComponent} from './admin.component';
import {UserListComponent} from './users/user-list.component';

export const adminRoutes: Routes = [
  {
    path: '',
    component: AdminComponent,
    children: [
      {path: '', redirectTo: 'users', pathMatch: 'full'},

      {path: 'users', component: UserListComponent},

      {path: 'proxies', redirectTo: 'proxies/dashboard', pathMatch: 'full'},
      {
        path: 'proxies/dashboard',
        loadComponent: () =>
          import('./proxy-dashboard/proxy-dashboard.component')
            .then(m => m.ProxyDashboardComponent),
      },
      {
        path: 'proxies/list',
        loadComponent: () =>
          import('./proxy-list/proxy-list.component')
            .then(m => m.ProxyListComponent),
      },

      {
        path: 'audit-logs',
        loadComponent: () =>
          import('./audit-logs/audit-logs.component')
            .then(m => m.AuditLogsComponent),
      },
    ],
  },
];
