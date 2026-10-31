import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { HeartRateTrend, HrRunType } from '../../../core/api/running-dashboard.models';
import { ChartComponent } from '../../../shared/chart/chart';
import { Tone, toneForChange } from '../../../shared/format/tone';
import { buildHeartRateTrendOptions, hrTrendText } from '../chart-options/heart-rate-trend.options';

@Component({
  selector: 'app-heart-rate-trend-chart',
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      <h2>Heart rate by run type</h2>
      @if (trend().types.length === 0) {
        <p class="empty">No runs in the last 5 months</p>
      } @else {
        <label class="type-picker">
          Run type
          <select (change)="onSelect($event)">
            @for (type of trend().types; track type.typeKey) {
              <option [value]="type.typeKey" [selected]="type.typeKey === selected()?.typeKey">
                {{ type.label }} ({{ type.runCount }} runs)
              </option>
            }
          </select>
        </label>
        @if (selected(); as type) {
          @if (type.hasEnoughData && type.trend) {
            <app-chart
              [options]="options()"
              [label]="'Average heart rate per run: ' + type.label"
            />
            <p class="note" [class]="'tone-' + trendTone()">{{ trendText() }}</p>
          } @else {
            <p class="empty">
              Not enough data for a comparison yet: {{ type.points.length }}
              {{ type.points.length === 1 ? 'run' : 'runs' }} with heart rate, at least
              {{ trend().minRunsForComparison }} needed.
            </p>
          }
        }
      }
    </section>
  `,
})
export class HeartRateTrendChart {
  readonly trend = input.required<HeartRateTrend>();
  readonly selectedTypeKey = input<string | null>(null);
  readonly typeSelected = output<string>();

  /** The picked type, or the first one by default; null only when there are no types. */
  protected readonly selected = computed<HrRunType | null>(() => {
    const types = this.trend().types;
    return (
      types.find((type) => type.typeKey === this.selectedTypeKey()) ??
      (types.length > 0 ? types[0] : null)
    );
  });

  protected readonly options = computed(() => {
    const type = this.selected();
    return type ? buildHeartRateTrendOptions(type) : {};
  });

  protected readonly trendText = computed(() => {
    const trend = this.selected()?.trend;
    return trend ? hrTrendText(trend) : '';
  });

  protected readonly trendTone = computed<Tone>(() => {
    const trend = this.selected()?.trend;
    return trend ? toneForChange(trend.changeBpm) : 'neutral';
  });

  protected onSelect(event: Event): void {
    this.typeSelected.emit((event.target as HTMLSelectElement).value);
  }
}
