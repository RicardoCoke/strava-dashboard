import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DriftPoint, DriftThresholds } from '../../../core/api/long-run-drift.models';
import { ChartComponent } from '../../../shared/chart/chart';
import { LONG_RUN_DRIFT_CHART_LABEL, buildLongRunDriftOptions } from '../long-run-drift.options';

@Component({
  selector: 'app-long-run-drift-chart',
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-chart [options]="options()" [label]="label" />`,
})
export class LongRunDriftChart {
  readonly points = input.required<DriftPoint[]>();
  readonly thresholds = input.required<DriftThresholds>();
  protected readonly label = LONG_RUN_DRIFT_CHART_LABEL;
  protected readonly options = computed(() =>
    buildLongRunDriftOptions(this.points(), this.thresholds()),
  );
}
