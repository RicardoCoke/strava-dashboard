import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import {
  PaceTypeComparison,
  WorkoutPaceComparison,
} from '../../../core/api/running-dashboard.models';
import { ChartComponent } from '../../../shared/chart/chart';
import { Tone } from '../../../shared/format/tone';
import {
  buildWorkoutPaceTrendOptions,
  notEnoughPaceText,
  paceChangeText,
  paceChangeTone,
  paceSummaryPrefix,
  runsNotShownText,
} from '../chart-options/workout-pace.options';

/** "Workout pace by type": pick a workout type, one rep-pace point per run, the 30-day change below (feature 010). */
@Component({
  selector: 'app-workout-pace-comparison-chart',
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h2>Workout pace by type</h2>
      @if (comparison().types.length === 0) {
        <p class="empty">No workouts (e.g. 5x6min) in the last 5 months</p>
      } @else {
        <label class="type-picker">
          Workout type
          <select (change)="onSelect($event)">
            @for (type of comparison().types; track type.typeKey) {
              <option [value]="type.typeKey" [selected]="type.typeKey === selected()?.typeKey">
                {{ type.label }} ({{ type.runCount }} {{ type.runCount === 1 ? 'run' : 'runs' }})
              </option>
            }
          </select>
        </label>
        @if (selected(); as type) {
          @if (type.points.length > 0) {
            <app-chart [options]="options()" [label]="'Average rep pace per run: ' + type.label" />
          } @else {
            <p class="empty">No runs with a rep pace for this workout type yet</p>
          }
          @if (summaryPrefix(); as prefix) {
            <p class="summary">
              {{ prefix
              }}<span class="change" [class]="'tone-' + changeTone()">{{ changeText() }}</span>
            </p>
          } @else {
            <p class="summary empty">{{ notEnoughText() }}</p>
          }
          @if (notShownText(); as note) {
            <p class="note muted">{{ note }}</p>
          }
        }
      }
    </section>
  `,
  styles: `
    .type-picker {
      display: inline-flex;
      gap: 0.5rem;
      align-items: center;
      margin-bottom: 0.5rem;
    }

    .muted {
      color: var(--color-text-muted);
    }
  `,
})
export class WorkoutPaceComparisonChart {
  readonly comparison = input.required<WorkoutPaceComparison>();
  readonly selectedTypeKey = input<string | null>(null);
  readonly typeSelected = output<string>();

  /** The picked type, or the first one (most recent run) by default; null only when there are no types. */
  protected readonly selected = computed<PaceTypeComparison | null>(() => {
    const types = this.comparison().types;
    return (
      types.find((type) => type.typeKey === this.selectedTypeKey()) ??
      (types.length > 0 ? types[0] : null)
    );
  });

  protected readonly options = computed(() => {
    const type = this.selected();
    return type ? buildWorkoutPaceTrendOptions(type) : {};
  });
  protected readonly summaryPrefix = computed(() => {
    const type = this.selected();
    return type ? paceSummaryPrefix(type) : null;
  });
  protected readonly changeText = computed(() => {
    const type = this.selected();
    return type ? paceChangeText(type) : '';
  });
  protected readonly changeTone = computed<Tone>(() => {
    const type = this.selected();
    return type ? paceChangeTone(type) : 'neutral';
  });
  protected readonly notEnoughText = computed(() => {
    const type = this.selected();
    return type ? notEnoughPaceText(type) : '';
  });
  protected readonly notShownText = computed(() => {
    const type = this.selected();
    return type ? runsNotShownText(type.runsWithoutPace) : null;
  });

  protected onSelect(event: Event): void {
    this.typeSelected.emit((event.target as HTMLSelectElement).value);
  }
}
