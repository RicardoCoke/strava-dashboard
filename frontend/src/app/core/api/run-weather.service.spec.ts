import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RunWeatherResponse } from './run-weather.models';
import { RunWeatherService } from './run-weather.service';

describe('RunWeatherService', () => {
  it('018 R6 - getRunWeather calls the endpoint', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const body: RunWeatherResponse = {
      days: [{ date: '2026-11-05', condition: 'rain', rainMm: 1.2 }],
    };
    let received: RunWeatherResponse | undefined;

    TestBed.inject(RunWeatherService)
      .getRunWeather()
      .subscribe((value) => (received = value));
    const request = http.expectOne('/api/run-weather');
    expect(request.request.method).toBe('GET');
    request.flush(body);

    expect(received).toEqual(body);
    http.verify();
  });
});
