import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { RunWeatherService } from '../core/api/run-weather.service';

/** Answers the build calendar's weather request with no days, so page specs don't see it. */
export function stubRunWeather(): void {
  TestBed.overrideProvider(RunWeatherService, {
    useValue: { getRunWeather: () => of({ days: [] }) },
  });
}
