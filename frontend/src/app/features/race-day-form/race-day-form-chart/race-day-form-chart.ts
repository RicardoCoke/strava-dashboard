import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RaceDayFormResponse } from '../../../core/api/race-day-form.models';
import { ChartComponent } from '../../../shared/chart/chart';
import { RACE_DAY_FORM_CHART_LABEL, buildRaceDayFormOptions } from '../race-day-form.options';

@Component({
  selector: 'app-race-day-form-chart',
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-chart [options]="options()" [label]="label" />`,
})
export class RaceDayFormChart {
  readonly form = input.required<RaceDayFormResponse>();
  protected readonly label = RACE_DAY_FORM_CHART_LABEL;
  protected readonly options = computed(() => buildRaceDayFormOptions(this.form()));
}
