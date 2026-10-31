import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';
import { NgxEchartsDirective } from 'ngx-echarts';

/** The single wrapper around ngx-echarts; every chart in the app renders through it. */
@Component({
  selector: 'app-chart',
  imports: [NgxEchartsDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div
    echarts
    class="chart"
    role="img"
    [attr.aria-label]="label()"
    [options]="options()"
  ></div>`,
  styles: `
    .chart {
      width: 100%;
      height: 300px;
    }
  `,
})
export class ChartComponent {
  readonly options = input.required<EChartsCoreOption>();
  readonly label = input.required<string>();
}
