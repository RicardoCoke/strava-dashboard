import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { isWeeklySummariesResponse } from './weekly-summary.models';
import { WeeklySummaryService } from './weekly-summary.service';

// All data here is invented.
const BODY = {
  summaries: [
    {
      weekStart: '2026-11-02',
      weekEnd: '2026-11-08',
      inProgress: true,
      runCount: 5,
      revision: 'abc',
      notes: [
        {
          metric: 'distance',
          tone: 'neutral',
          text: 'Volume 50.0 km over 5 runs (+50.0 km vs last week)',
        },
      ],
    },
  ],
};

describe('WeeklySummaryService', () => {
  it('R14 - single GET /api/weekly-summaries', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(WeeklySummaryService);
    const http = TestBed.inject(HttpTestingController);
    let body: unknown;

    service.getSummaries().subscribe((value) => (body = value));
    const request = http.expectOne('/api/weekly-summaries');
    expect(request.request.method).toBe('GET');
    request.flush(BODY);

    expect(body).toEqual(BODY);
    http.verify();
  });

  it('R14 - isWeeklySummariesResponse accepts the contract and rejects others', () => {
    expect(isWeeklySummariesResponse(BODY)).toBe(true);
    expect(isWeeklySummariesResponse({ summaries: [] })).toBe(true);
    expect(
      isWeeklySummariesResponse({
        summaries: [{ ...BODY.summaries[0], notes: [{ tone: 'loud' }] }],
      }),
    ).toBe(false);
    expect(isWeeklySummariesResponse(null)).toBe(false);
  });
});
