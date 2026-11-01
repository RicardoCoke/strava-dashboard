import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { WellnessResponse } from '../../../core/api/wellness.models';
import { ChartComponent } from '../../../shared/chart/chart';
import { WELLNESS_CHART_LABEL, buildWellnessOptions } from '../wellness.options';

@Component({
  selector: 'app-wellness-chart',
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-chart [options]="options()" [label]="label" />`,
})
export class WellnessChart {
  readonly wellness = input.required<WellnessResponse>();
  protected readonly label = WELLNESS_CHART_LABEL;
  protected readonly options = computed(() => buildWellnessOptions(this.wellness()));
}
