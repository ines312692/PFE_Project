import {Routes} from '@angular/router';

export const projectsRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./project-list/project-list.component').then(m => m.ProjectListComponent),
  },
  {
    path: 'new',
    loadComponent: () =>
      import('./project-workspace/project-workspace.component').then(m => m.ProjectWorkspaceComponent),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./project-detail/project-detail.component').then(m => m.ProjectDetailComponent),
  },
  {
    path: ':id/edit',
    loadComponent: () =>
      import('./project-workspace/project-workspace.component').then(m => m.ProjectWorkspaceComponent),
  },
];
