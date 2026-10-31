import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/running-dashboard/running-dashboard-page').then(
        (m) => m.RunningDashboardPage,
      ),
  },
  {
    path: 'connect',
    loadComponent: () => import('./features/connect/connect-page').then((m) => m.ConnectPage),
  },
  { path: '**', redirectTo: '' },
];
