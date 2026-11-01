import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { EfficiencyFactorResponse } from './efficiency-factor.models';

@Injectable({ providedIn: 'root' })
export class EfficiencyFactorService {
  private readonly http = inject(HttpClient);

  getEfficiencyFactor(): Observable<EfficiencyFactorResponse> {
    return this.http.get<EfficiencyFactorResponse>('/api/efficiency-factor');
  }
}
