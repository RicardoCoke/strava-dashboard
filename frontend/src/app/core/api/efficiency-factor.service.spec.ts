import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { efficiencyFactor } from '../../testing/efficiency-factor-fixtures';
import { EfficiencyFactorService } from './efficiency-factor.service';

describe('EfficiencyFactorService', () => {
  it('014 R7 - getEfficiencyFactor calls GET /api/efficiency-factor', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(EfficiencyFactorService);
    const http = TestBed.inject(HttpTestingController);
    let body: unknown;

    service.getEfficiencyFactor().subscribe((value) => (body = value));
    const request = http.expectOne('/api/efficiency-factor');
    expect(request.request.method).toBe('GET');
    request.flush(efficiencyFactor());

    expect(body).toEqual(efficiencyFactor());
    http.verify();
  });
});
