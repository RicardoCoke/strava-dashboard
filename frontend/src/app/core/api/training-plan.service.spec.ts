import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { trainingPlan } from '../../testing/training-plan-fixtures';
import { TrainingPlanResponse } from './training-plan.models';
import { TrainingPlanService } from './training-plan.service';

describe('TrainingPlanService', () => {
  it('004 R8 - getTrainingPlan calls /api/training-plan', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(TrainingPlanService);
    const http = TestBed.inject(HttpTestingController);
    let received: TrainingPlanResponse | undefined;

    service.getTrainingPlan().subscribe((data) => (received = data));
    const request = http.expectOne('/api/training-plan');
    expect(request.request.method).toBe('GET');
    request.flush(trainingPlan());

    expect(received?.weeks.length).toBe(16);
    http.verify();
  });
});
