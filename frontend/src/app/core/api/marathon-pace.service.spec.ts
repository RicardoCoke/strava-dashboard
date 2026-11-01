import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { marathonPace, mpSummary, mpWeeks } from '../../testing/marathon-pace-fixtures';
import { MarathonPaceResponse, isMarathonPaceResponse } from './marathon-pace.models';
import { MarathonPaceService } from './marathon-pace.service';

describe('MarathonPaceService', () => {
  it('R5 - getMarathonPace requests GET /api/marathon-pace', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(MarathonPaceService);
    const http = TestBed.inject(HttpTestingController);
    let received: MarathonPaceResponse | undefined;

    service.getMarathonPace().subscribe((data) => (received = data));
    const request = http.expectOne('/api/marathon-pace');
    expect(request.request.method).toBe('GET');
    request.flush(marathonPace());

    expect(received?.weeks.length).toBe(16);
    http.verify();
  });

  it('R7 - shape guard accepts the contract and rejects other bodies', () => {
    expect(isMarathonPaceResponse(marathonPace())).toBe(true);
    expect(isMarathonPaceResponse(marathonPace({ weeks: [] }))).toBe(false);
    expect(isMarathonPaceResponse({ ...marathonPace(), summary: null })).toBe(false);
    const [first, ...rest] = mpWeeks();
    const noLabel: Record<string, unknown> = { ...first };
    delete noLabel['label'];
    expect(isMarathonPaceResponse({ ...marathonPace(), weeks: [noLabel, ...rest] })).toBe(false);
    expect(
      isMarathonPaceResponse(marathonPace({ summary: mpSummary({ thisWeekSharePercent: null }) })),
    ).toBe(true);
    expect(
      isMarathonPaceResponse({
        ...marathonPace(),
        summary: { ...mpSummary(), thisWeekSharePercent: '25' },
      }),
    ).toBe(false);
    expect(isMarathonPaceResponse({ weeks: 'x' })).toBe(false);
    expect(isMarathonPaceResponse(null)).toBe(false);
  });
});
