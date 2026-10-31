import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { WeekVo2max } from '../../../core/api/vo2max.models';
import { ChartComponent } from '../../../shared/chart/chart';
import { VO2MAX_CHART_LABEL, buildVo2maxOptions } from '../vo2max.options';

@Component({
  selector: 'app-vo2max-chart',
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-chart [options]="options()" [label]="label" />`,
})
export class Vo2maxChart {
  readonly weeks = input.required<WeekVo2max[]>();
  protected readonly label = VO2MAX_CHART_LABEL;
  protected readonly options = computed(() => buildVo2maxOptions(this.weeks()));
}
