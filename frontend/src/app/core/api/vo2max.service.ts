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
}
