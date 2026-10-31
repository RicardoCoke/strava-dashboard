import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { TrainingPlanResponse } from './training-plan.models';

@Injectable({ providedIn: 'root' })
export class TrainingPlanService {
  private readonly http = inject(HttpClient);

  getTrainingPlan(): Observable<TrainingPlanResponse> {
    return this.http.get<TrainingPlanResponse>('/api/training-plan');
  }
}
