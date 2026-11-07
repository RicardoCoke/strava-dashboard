import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/running-dashboard/dashboard-shell').then((m) => m.DashboardShell),
  },
  {
    path: 'connect',
    loadComponent: () => import('./features/connect/connect-page').then((m) => m.ConnectPage),
  },
  { path: '**', redirectTo: '' },
];
