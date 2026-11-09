import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideEchartsCore } from 'ngx-echarts';
import { routes } from './app.routes';
import { personInterceptor } from './core/interceptors/person.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([personInterceptor]), withXsrfConfiguration({})),
    // ECharts loads lazily, so it stays out of the initial bundle.
    provideEchartsCore({
      echarts: () => import('./shared/chart/echarts-setup').then((m) => m.echarts),
    }),
  ],
};
