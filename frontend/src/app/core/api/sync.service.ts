import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** The background-sync API (feature 009). Bodies are `unknown` until `isSyncStatusResponse` checks them. */
@Injectable({ providedIn: 'root' })
export class SyncService {
  private readonly http = inject(HttpClient);

  getStatus(): Observable<unknown> {
    return this.http.get<unknown>('/api/sync');
  }

  /** Starts a sync if one is due; Angular's XSRF support adds the X-XSRF-TOKEN header. */
  start(): Observable<unknown> {
    return this.http.post<unknown>('/api/sync', {});
  }
}
