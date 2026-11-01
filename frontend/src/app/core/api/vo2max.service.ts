import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Vo2maxResponse } from './vo2max.models';

@Injectable({ providedIn: 'root' })
export class Vo2maxService {
  private readonly http = inject(HttpClient);

  getVo2max(): Observable<Vo2maxResponse> {
    return this.http.get<Vo2maxResponse>('/api/vo2max');
  }

  /** The hill- and drift-adjusted estimate (feature 016); the body is checked by the caller. */
  getNormalizedVo2max(): Observable<unknown> {
    return this.http.get<unknown>('/api/vo2max/normalized');
  }
}
