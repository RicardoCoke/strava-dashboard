import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RunWeatherResponse } from '../../../core/api/run-weather.models';
import { trainingPlan } from '../../../testing/training-plan-fixtures';
import { TrainingPlanStore } from '../training-plan.store';
import { BuildCalendarSection } from './build-calendar-section';

// All forecast values here are invented.
describe('BuildCalendarSection weather', () => {
  let fixture: ComponentFixture<BuildCalendarSection>;
  let http: HttpTestingController;
  const body: RunWeatherResponse = {
    days: [{ date: '2026-08-18', condition: 'rain', rainMm: 1.2 }],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TrainingPlanStore],
    });
    fixture = TestBed.createComponent(BuildCalendarSection);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/training-plan').flush(trainingPlan());
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const weatherRequest = () => http.expectOne('/api/run-weather');

  it('018 AC8.2 - icons appear when the weather arrives', () => {
    const request = weatherRequest();
    expect(element().querySelectorAll('.day').length).toBeGreaterThan(0);
    expect(element().querySelector('.weather')).toBeNull();

    request.flush(body);
    fixture.detectChanges();
    expect(element().querySelectorAll('.weather').length).toBe(1);
  });

  it('018 AC8.3 - failed or malformed weather leaves the calendar intact', () => {
    weatherRequest().flush(
      { error: 'weather_unavailable', message: 'x' },
      { status: 503, statusText: 'Error' },
    );
    fixture.detectChanges();
    expect(element().querySelectorAll('.day').length).toBeGreaterThan(0);
    expect(element().querySelector('.weather')).toBeNull();
    expect(element().querySelector('.weather-credit')).toBeNull();
    expect(element().textContent).not.toContain('forecast');
  });

  it('018 AC8.3 - a body without days shows no weather', () => {
    weatherRequest().flush({});
    fixture.detectChanges();
    expect(element().querySelector('.weather')).toBeNull();
    expect(element().querySelector('.weather-credit')).toBeNull();
  });

  it('018 R9 - credit shows only with weather', () => {
    const request = weatherRequest();
    expect(element().querySelector('.weather-credit')).toBeNull();
    request.flush(body);
    fixture.detectChanges();

    const credit = element().querySelector('.weather-credit');
    expect(credit?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
      'Weather data by Open-Meteo.com (CC BY 4.0)',
    );
    const links = Array.from(credit?.querySelectorAll('a') ?? []);
    expect(links.map((link) => link.getAttribute('href'))).toEqual([
      'https://open-meteo.com/',
      'https://creativecommons.org/licenses/by/4.0/',
    ]);
    links.forEach((link) => {
      expect(link.getAttribute('target')).toBe('_blank');
      expect(link.getAttribute('rel')).toBe('noopener noreferrer');
    });
  });

  it('018 R9 - no credit when the forecast has no days', () => {
    weatherRequest().flush({ days: [] });
    fixture.detectChanges();
    expect(element().querySelector('.weather-credit')).toBeNull();
  });
});
