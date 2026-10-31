import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { WeeklyDistance } from '../../../core/api/running-dashboard.models';
import { ChartComponent } from '../../../shared/chart/chart';
import { buildWeeklyDistanceOptions } from '../chart-options/weekly-distance.options';

@Component({
  selector: 'app-weekly-distance-chart',
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h2>Weekly distance</h2>
      @if (hasRuns()) {
        <app-chart [options]="options()" label="Running kilometres per week, last 9 weeks" />
      } @else {
        <p class="empty">No runs in the last 9 weeks</p>
      }
    </section>
  `,
})
export class WeeklyDistanceChart {
  readonly weekly = input.required<WeeklyDistance>();
  protected readonly hasRuns = computed(() =>
    this.weekly().weeks.some((week) => week.runCount > 0),
  );
  protected readonly options = computed(() => buildWeeklyDistanceOptions(this.weekly().weeks));
}
