import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class WellnessService {
  private readonly http = inject(HttpClient);

  /** The body is checked with `isWellnessResponse` by the caller. */
  getWellness(): Observable<unknown> {
    return this.http.get<unknown>('/api/wellness');
  }
}
