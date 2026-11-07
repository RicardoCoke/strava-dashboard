import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { PersonStore } from '../person/person.store';

/** Every API call says whose data it wants (feature 019). */
export const personInterceptor: HttpInterceptorFn = (req, next) =>
  req.url.startsWith('/api/')
    ? next(req.clone({ setHeaders: { 'X-Person': inject(PersonStore).person() } }))
    : next(req);
