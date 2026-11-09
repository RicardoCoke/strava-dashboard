import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class RingsService {
  private readonly http = inject(HttpClient);

  /** The body is checked with `isRingsResponse` by the caller. */
  getRings(): Observable<unknown> {
    return this.http.get<unknown>('/api/rings');
  }
}
