import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/running-dashboard/dashboard-shell').then((m) => m.DashboardShell),
  },
  {
    path: 'recovery',
    loadComponent: () => import('./features/recovery/recovery-shell').then((m) => m.RecoveryShell),
  },
  { path: '**', redirectTo: '' },
];
