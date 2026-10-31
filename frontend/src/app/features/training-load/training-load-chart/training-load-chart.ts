import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TrainingLoadResponse } from '../../../core/api/training-load.models';
import { ChartComponent } from '../../../shared/chart/chart';
import { TRAINING_LOAD_CHART_LABEL, buildTrainingLoadOptions } from '../training-load.options';

@Component({
  selector: 'app-training-load-chart',
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-chart [options]="options()" [label]="label" />`,
})
export class TrainingLoadChart {
  readonly load = input.required<TrainingLoadResponse>();
  protected readonly label = TRAINING_LOAD_CHART_LABEL;
  protected readonly options = computed(() => buildTrainingLoadOptions(this.load()));
}
