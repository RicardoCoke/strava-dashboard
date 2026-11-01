import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LongRunDriftService {
  private readonly http = inject(HttpClient);

  /** Unknown until checked with `isLongRunDriftResponse`. */
  getLongRunDrift(): Observable<unknown> {
    return this.http.get<unknown>('/api/long-run-drift');
  }
}
