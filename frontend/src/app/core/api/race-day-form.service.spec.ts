import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { notEnoughRunsForm, raceDayForm } from '../../testing/race-day-form-fixtures';
import { isRaceDayFormResponse } from './race-day-form.models';
import { RaceDayFormService } from './race-day-form.service';

describe('RaceDayFormService', () => {
  it('R10 - getRaceDayForm calls /api/race-day-form', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    TestBed.inject(RaceDayFormService).getRaceDayForm().subscribe();
    expect(http.expectOne('/api/race-day-form').request.method).toBe('GET');
    http.verify();
  });

  it('R16 - shape guard accepts the contract and rejects other bodies', () => {
    expect(isRaceDayFormResponse(raceDayForm())).toBe(true);
    expect(isRaceDayFormResponse(notEnoughRunsForm())).toBe(true);
    expect(isRaceDayFormResponse(raceDayForm({ days: [] }))).toBe(false);
    expect(isRaceDayFormResponse({ projection: 'maybe' })).toBe(false);
    expect(isRaceDayFormResponse(null)).toBe(false);
  });
});
