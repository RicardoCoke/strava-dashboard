import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';
import { RunWeatherDay } from '../../../core/api/run-weather.models';
import { RunWeatherService } from '../../../core/api/run-weather.service';
import { BuildCalendar } from '../build-calendar/build-calendar';
import { TargetPaces } from '../target-paces/target-paces';
import { TrainingPlanStore } from '../training-plan.store';

/**
 * The "Build calendar" card at the bottom of the dashboard: target paces and the calendar.
 * It renders only once the plan has loaded; the top card shows the loading and error states.
 * Weather is a separate request, so a slow or failed forecast only leaves the tiles without icons.
 */
@Component({
  selector: 'app-build-calendar-section',
  imports: [TargetPaces, BuildCalendar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.data(); as data) {
      <section class="card plan-calendar" aria-labelledby="plan-calendar-heading">
        <h2 id="plan-calendar-heading">Build calendar</h2>
        <app-target-paces [paces]="data.targetPaces" />
        <app-build-calendar [weeks]="data.weeks" [progress]="data.progress" [weather]="weather()" />
        @if (weather().length > 0) {
          <p class="weather-credit">
            Weather data by
            <a href="https://open-meteo.com/" target="_blank" rel="noopener noreferrer"
              >Open-Meteo.com</a
            >
            (<a
              href="https://creativecommons.org/licenses/by/4.0/"
              target="_blank"
              rel="noopener noreferrer"
              >CC BY 4.0</a
            >)
          </p>
        }
      </section>
    }
  `,
  styles: `
    :host {
      display: block;
      margin-top: 1rem;
    }

    .weather-credit {
      margin: 0.5rem 0 0;
      font-size: 0.75rem;
      color: var(--color-text-muted);
    }

    .weather-credit a {
      color: inherit;
    }
  `,
})
export class BuildCalendarSection implements OnInit {
  protected readonly store = inject(TrainingPlanStore);
  protected readonly weather = toSignal(
    inject(RunWeatherService)
      .getRunWeather()
      .pipe(
        map((body): RunWeatherDay[] => (Array.isArray(body?.days) ? body.days : [])),
        catchError(() => of<RunWeatherDay[]>([])),
      ),
    { initialValue: [] },
  );

  ngOnInit(): void {
    this.store.ensureLoaded();
  }
}
