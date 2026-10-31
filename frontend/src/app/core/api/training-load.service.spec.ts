import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { trainingLoad } from '../../testing/training-load-fixtures';
import { TrainingLoadResponse, isTrainingLoadResponse } from './training-load.models';
import { TrainingLoadService } from './training-load.service';

describe('TrainingLoadService', () => {
  it('005 R9 - getTrainingLoad calls /api/training-load', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(TrainingLoadService);
    const http = TestBed.inject(HttpTestingController);
    let received: TrainingLoadResponse | undefined;

    service.getTrainingLoad().subscribe((data) => (received = data));
    const request = http.expectOne('/api/training-load');
    expect(request.request.method).toBe('GET');
    request.flush(trainingLoad());

    expect(received?.days.length).toBe(185);
    http.verify();
  });

  it('005 R13 - shape guard accepts the contract and rejects other bodies', () => {
    expect(isTrainingLoadResponse(trainingLoad())).toBe(true);
    expect(isTrainingLoadResponse(trainingLoad({ days: [] }))).toBe(false);
    expect(isTrainingLoadResponse({ days: 'x' })).toBe(false);
    expect(isTrainingLoadResponse(null)).toBe(false);
  });
});
