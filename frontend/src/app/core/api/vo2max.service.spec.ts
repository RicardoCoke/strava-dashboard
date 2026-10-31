import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { emptyVo2max, vo2max } from '../../testing/vo2max-fixtures';
import { Vo2maxResponse, isVo2maxResponse } from './vo2max.models';
import { Vo2maxService } from './vo2max.service';

describe('Vo2maxService', () => {
  it('008 R9 - getVo2max calls /api/vo2max', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(Vo2maxService);
    const http = TestBed.inject(HttpTestingController);
    let received: Vo2maxResponse | undefined;

    service.getVo2max().subscribe((data) => (received = data));
    const request = http.expectOne('/api/vo2max');
    expect(request.request.method).toBe('GET');
    request.flush(vo2max());

    expect(received?.weeks.length).toBe(27);
    http.verify();
  });

  it('008 R13 - shape guard accepts the contract and rejects other bodies', () => {
    expect(isVo2maxResponse(vo2max())).toBe(true);
    expect(isVo2maxResponse(emptyVo2max())).toBe(true);
    expect(isVo2maxResponse(vo2max({ weeks: [] }))).toBe(false);
    expect(isVo2maxResponse({ weeks: 'x' })).toBe(false);
    expect(isVo2maxResponse(null)).toBe(false);
  });
});
