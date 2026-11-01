import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** The race-day form projection (feature 011). The body is `unknown` until `isRaceDayFormResponse` checks it. */
@Injectable({ providedIn: 'root' })
export class RaceDayFormService {
  private readonly http = inject(HttpClient);

  getRaceDayForm(): Observable<unknown> {
    return this.http.get<unknown>('/api/race-day-form');
  }
}
