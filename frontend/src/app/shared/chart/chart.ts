import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { EChartsCoreOption } from 'echarts/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import { withTouchZoom } from './touch-zoom';

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
    [options]="chartOptions()"
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

  // `?.`: jsdom (tests) has no matchMedia.
  private readonly touch = globalThis.matchMedia?.('(pointer: coarse)').matches ?? false;
  protected readonly chartOptions = computed(() => withTouchZoom(this.options(), this.touch));
}
