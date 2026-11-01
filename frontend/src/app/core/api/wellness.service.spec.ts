import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { emptyWellness, wellness } from '../../testing/wellness-fixtures';
import { isWellnessResponse } from './wellness.models';
import { WellnessService } from './wellness.service';

describe('WellnessService', () => {
  it('R8 - getWellness calls GET /api/wellness', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(WellnessService);
    const http = TestBed.inject(HttpTestingController);
    let received: unknown;

    service.getWellness().subscribe((data) => (received = data));
    const request = http.expectOne('/api/wellness');
    expect(request.request.method).toBe('GET');
    request.flush(wellness());

    expect(isWellnessResponse(received)).toBe(true);
    http.verify();
  });

  it('R9 - shape guard accepts the contract and rejects other bodies', () => {
    expect(isWellnessResponse(wellness())).toBe(true);
    expect(isWellnessResponse(emptyWellness())).toBe(true);
    expect(isWellnessResponse(wellness({ days: [] }))).toBe(false);
    expect(isWellnessResponse({ days: 'x' })).toBe(false);
    expect(isWellnessResponse(null)).toBe(false);
  });
});
