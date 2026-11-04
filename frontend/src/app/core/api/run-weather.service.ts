import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RunWeatherResponse } from './run-weather.models';

@Injectable({ providedIn: 'root' })
export class RunWeatherService {
  private readonly http = inject(HttpClient);

  getRunWeather(): Observable<RunWeatherResponse> {
    return this.http.get<RunWeatherResponse>('/api/run-weather');
  }
}
