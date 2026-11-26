import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CompareFilter } from './workout-compare.models';

@Injectable({ providedIn: 'root' })
export class WorkoutCompareService {
  private readonly http = inject(HttpClient);

  /** The body is checked with `isCompareRunsResponse` by the caller. */
  getRuns(type: CompareFilter): Observable<unknown> {
    return this.http.get<unknown>('/api/workout-compare/runs', { params: { type } });
  }

  /** The body is checked with `isWorkoutCompareResponse` by the caller. */
  compare(a: number, b: number): Observable<unknown> {
    return this.http.get<unknown>('/api/workout-compare', { params: { a, b } });
  }
}
