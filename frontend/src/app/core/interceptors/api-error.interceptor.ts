import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

const REAUTH_REQUIRED = 'strava_reauth_required';

function errorCode(body: unknown): string | null {
  if (typeof body === 'object' && body !== null && 'error' in body) {
    const code = (body as { error: unknown }).error;
    return typeof code === 'string' ? code : null;
  }
  return null;
}

/** A 401 from the API means there's no Strava connection: send the user to the connect page. */
export const apiErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  return next(req).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        req.url.startsWith('/api/')
      ) {
        const reauth = errorCode(error.error) === REAUTH_REQUIRED;
        void router.navigate(['/connect'], reauth ? { queryParams: { reason: 'reauth' } } : {});
      }
      return throwError(() => error);
    }),
  );
};
