import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NormalizedWeekVo2max } from '../../../core/api/normalized-vo2max.models';
import { ChartComponent } from '../../../shared/chart/chart';
import {
  NORMALIZED_VO2MAX_CHART_LABEL,
  buildNormalizedVo2maxOptions,
} from '../normalized-vo2max.options';

@Component({
  selector: 'app-normalized-vo2max-chart',
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-chart [options]="options()" [label]="label" />`,
})
export class NormalizedVo2maxChart {
  readonly weeks = input.required<NormalizedWeekVo2max[]>();
  protected readonly label = NORMALIZED_VO2MAX_CHART_LABEL;
  protected readonly options = computed(() => buildNormalizedVo2maxOptions(this.weeks()));
}
