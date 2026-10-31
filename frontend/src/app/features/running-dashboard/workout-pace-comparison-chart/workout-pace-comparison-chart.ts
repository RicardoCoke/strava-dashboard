import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  PaceTypeComparison,
  WorkoutPaceComparison,
} from '../../../core/api/running-dashboard.models';
import { ChartComponent } from '../../../shared/chart/chart';
import { formatPaceChange } from '../../../shared/format/pace';
import { Tone, toneForChange } from '../../../shared/format/tone';
import { PacePipe } from '../../../shared/pipes/pace.pipe';
import {
  buildWorkoutPaceOptions,
  comparablePaceTypes,
} from '../chart-options/workout-pace.options';

/** "5x6min: no paced runs in the previous 30 days" (or "last 30 days", or "either period"). */
export function missingWindowText(type: PaceTypeComparison): string {
  const missing =
    !type.current && !type.previous
      ? 'either period'
      : !type.previous
        ? 'the previous 30 days'
        : 'the last 30 days';
  return `${type.label}: no paced runs in ${missing}`;
}

@Component({
  selector: 'app-workout-pace-comparison-chart',
  imports: [ChartComponent, PacePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h2>Workout pace: last 30 days vs previous 30 days</h2>
      @if (comparison().types.length === 0) {
        <p class="empty">No workouts (e.g. 5x6min) in the last 60 days</p>
      } @else {
        @if (drawn().length > 0) {
          <app-chart
            [options]="options()"
            label="Average rep pace per workout type, two 30-day periods"
          />
          <ul class="changes">
            @for (row of drawn(); track row.type.typeKey) {
              <li>
                <strong>{{ row.type.label }}</strong
                >: {{ row.previous | pace }} → {{ row.current | pace }} ·
                <span class="change" [class]="'tone-' + changeTone(row.type)">{{
                  changeText(row.type)
                }}</span>
              </li>
            }
          </ul>
        }
        @if (notEnough().length > 0) {
          <h3>Not enough data yet</h3>
          <ul class="not-enough empty">
            @for (type of notEnough(); track type.typeKey) {
              <li>{{ missingText(type) }}</li>
            }
          </ul>
        }
      }
    </section>
  `,
})
export class WorkoutPaceComparisonChart {
  readonly comparison = input.required<WorkoutPaceComparison>();

  protected readonly drawn = computed(() => comparablePaceTypes(this.comparison().types));
  protected readonly notEnough = computed(() => {
    const drawn = new Set(this.drawn().map((row) => row.type));
    return this.comparison().types.filter((type) => !drawn.has(type));
  });
  protected readonly options = computed(() => buildWorkoutPaceOptions(this.comparison().types));

  protected changeText(type: PaceTypeComparison): string {
    return formatPaceChange(type.changeSecondsPerKm ?? 0);
  }

  protected changeTone(type: PaceTypeComparison): Tone {
    return toneForChange(type.changeSecondsPerKm ?? 0);
  }

  protected missingText(type: PaceTypeComparison): string {
    return missingWindowText(type);
  }
}
