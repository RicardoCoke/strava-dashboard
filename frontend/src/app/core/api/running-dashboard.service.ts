import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RunningDashboardResponse } from './running-dashboard.models';

@Injectable({ providedIn: 'root' })
export class RunningDashboardService {
  private readonly http = inject(HttpClient);

  getDashboard(): Observable<RunningDashboardResponse> {
    return this.http.get<RunningDashboardResponse>('/api/dashboard/running');
  }
}
