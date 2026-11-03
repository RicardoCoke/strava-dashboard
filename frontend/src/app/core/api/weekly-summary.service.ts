import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { WeeklySummariesResponse } from './weekly-summary.models';

@Injectable({ providedIn: 'root' })
export class WeeklySummaryService {
  private readonly http = inject(HttpClient);

  getSummaries(): Observable<WeeklySummariesResponse> {
    return this.http.get<WeeklySummariesResponse>('/api/weekly-summaries');
  }
}
