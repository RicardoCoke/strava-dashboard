import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TrainingLoadResponse } from './training-load.models';

@Injectable({ providedIn: 'root' })
export class TrainingLoadService {
  private readonly http = inject(HttpClient);

  getTrainingLoad(): Observable<TrainingLoadResponse> {
    return this.http.get<TrainingLoadResponse>('/api/training-load');
  }
}
