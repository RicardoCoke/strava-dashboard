import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { longRunDrift } from '../../testing/long-run-drift-fixtures';
import { LongRunDriftService } from './long-run-drift.service';

describe('LongRunDriftService', () => {
  it('R4 - getLongRunDrift calls api long-run-drift', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(LongRunDriftService);
    const http = TestBed.inject(HttpTestingController);
    let body: unknown;

    service.getLongRunDrift().subscribe((value) => (body = value));
    const request = http.expectOne('/api/long-run-drift');
    expect(request.request.method).toBe('GET');
    request.flush(longRunDrift());

    expect(body).toEqual(longRunDrift());
    http.verify();
  });
});
