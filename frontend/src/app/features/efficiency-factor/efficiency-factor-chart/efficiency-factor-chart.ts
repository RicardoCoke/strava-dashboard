import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { EfPoint } from '../../../core/api/efficiency-factor.models';
import { ChartComponent } from '../../../shared/chart/chart';
import {
  EFFICIENCY_FACTOR_CHART_LABEL,
  buildEfficiencyFactorOptions,
} from '../efficiency-factor.options';

@Component({
  selector: 'app-efficiency-factor-chart',
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-chart [options]="options()" [label]="label" />`,
})
export class EfficiencyFactorChart {
  readonly points = input.required<EfPoint[]>();
  protected readonly label = EFFICIENCY_FACTOR_CHART_LABEL;
  protected readonly options = computed(() => buildEfficiencyFactorOptions(this.points()));
}
