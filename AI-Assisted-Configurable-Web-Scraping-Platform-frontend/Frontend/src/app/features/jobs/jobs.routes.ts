import {Routes} from '@angular/router';

export const jobsRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./job-list/job-list.component').then(m => m.JobListComponent),
  },
  {
    path: 'templates',
    loadComponent: () =>
      import('./job-templates/job-templates.component').then(m => m.JobTemplatesComponent),
  },
  {
    path: 'agenda',
    loadComponent: () =>
      import('./job-agenda/job-agenda.component').then(m => m.JobAgendaComponent),
  },
  {
    path: 'create',
    loadComponent: () =>
      import('./job-workspace/job-workspace.component').then(m => m.JobWorkspaceComponent),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./job-detail/job-detail.component').then(m => m.JobDetailComponent),
  },
  {
    path: ':id/edit',
    loadComponent: () =>
      import('./job-workspace/job-workspace.component').then(m => m.JobWorkspaceComponent),
  },
  {
    path: ':id/run',
    loadComponent: () =>
      import('./job-workspace/job-workspace.component').then(m => m.JobWorkspaceComponent),
    data: {runMode: true},
  },
  {
    path: ':id/pipeline',
    loadComponent: () =>
      import('./data-pipeline/data-pipeline.component')
        .then(m => m.DataPipelineComponent),
  },
  {
    path: ':id/data-pipeline',
    loadComponent: () => import('./data-pipeline/data-pipeline.component')
      .then(m => m.DataPipelineComponent),
  },
  {
    path: ':id/data-pipeline/history',
    loadComponent: () =>
      import('./data-pipeline/pipeline-history/pipeline-history.component')
        .then(m => m.PipelineHistoryComponent),
  },
  {
    path: ':id/captcha-intervention',
    loadComponent: () =>
      import('./captcha-intervention/captcha-intervention.component')
        .then(m => m.CaptchaInterventionComponent),
  },


];
