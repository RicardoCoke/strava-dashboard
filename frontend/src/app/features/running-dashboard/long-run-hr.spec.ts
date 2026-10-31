import type { EChartsOption, LineSeriesOption } from 'echarts';
import { By } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { HrRunType } from '../../core/api/running-dashboard.models';
import { ChartComponent } from '../../shared/chart/chart';
import { palette } from '../../shared/chart/palette';
import { hrType, longHrType, stubCharts } from '../../testing/dashboard-fixtures';
import { HeartRateTrendChart } from './heart-rate-trend-chart/heart-rate-trend-chart';

// Feature 006: the "Long runs" type on the HR card. All data is invented.
describe('long runs on the HR card', () => {
  beforeEach(() => stubCharts());

  function render(types: HrRunType[], selectedTypeKey: string | null = null) {
    const fixture = TestBed.createComponent(HeartRateTrendChart);
    fixture.componentRef.setInput('trend', {
      windowStart: '2026-05-29',
      windowEnd: '2026-10-29',
      minRunsForComparison: 3,
      types,
    });
    fixture.componentRef.setInput('selectedTypeKey', selectedTypeKey);
    fixture.detectChanges();
    return fixture;
  }

  function text(element: HTMLElement): string {
    return element.textContent?.replace(/\s+/g, ' ') ?? '';
  }

  it('006 R6 - selector lists easy then long then workouts', () => {
    const fixture = render([
      hrType({ runCount: 12 }),
      longHrType({ runCount: 5 }),
      hrType({ typeKey: '5x6min', label: '5x6min', kind: 'workout', runCount: 4 }),
    ]);
    const select = (fixture.nativeElement as HTMLElement).querySelector('select');
    const options = Array.from(select?.querySelectorAll('option') ?? []).map((o) =>
      o.textContent?.trim(),
    );
    expect(options).toEqual(['Easy runs (12 runs)', 'Long runs (5 runs)', '5x6min (4 runs)']);
    expect(select?.value).toBe('easy');
  });

  it('006 R6 - selecting long runs renders its chart', () => {
    const long = longHrType({
      points: longHrType().points.map((point, i) => ({ ...point, rising: i === 2 })),
    });
    const fixture = render([hrType(), long], 'long');
    const chart = fixture.debugElement.query(By.directive(ChartComponent))
      .componentInstance as ChartComponent;
    const series = (chart.options() as EChartsOption).series as LineSeriesOption[];
    const points = series.find((s) => s.name !== 'HR line' && s.lineStyle?.width === 0);
    expect(points?.data).toHaveLength(3);
    expect(series.filter((s) => s.name === 'HR line').map((s) => s.lineStyle?.color)).toContain(
      palette.worse,
    );
    expect(series.some((s) => s.lineStyle?.type === 'dashed')).toBe(true);
    expect(text(fixture.nativeElement as HTMLElement)).toContain(
      'Trend: −8.0 bpm over the period (lower = fitter)',
    );
  });

  it('006 R6 - long run options keep treadmill colour and tooltip', () => {
    const fixture = render([longHrType()]);
    const chart = fixture.debugElement.query(By.directive(ChartComponent))
      .componentInstance as ChartComponent;
    const options = chart.options() as EChartsOption;
    const series = options.series as LineSeriesOption[];
    expect(JSON.stringify(series.map((s) => s.data))).toContain(palette.treadmill);
    expect(options.xAxis).toMatchObject({ type: 'time' });
    expect(options.yAxis).toMatchObject({ name: 'Avg HR (bpm)', scale: true });
    const formatter = (options.tooltip as { formatter: (params: unknown) => string }).formatter;
    expect(formatter({ seriesIndex: 0, dataIndex: 1 })).toBe(
      '1 Jul 2026 · 146.0 bpm · 24.0 km · Treadmill',
    );
  });

  it('006 R6 - long runs with one point show not enough data', () => {
    const fixture = render(
      [longHrType({ hasEnoughData: false, trend: null, points: longHrType().points.slice(0, 1) })],
      'long',
    );
    const element = fixture.nativeElement as HTMLElement;
    expect(text(element)).toContain(
      'Not enough data for a comparison yet: 1 run with heart rate, at least 3 needed.',
    );
    expect(element.querySelector('app-chart')).toBeNull();
  });

  it('006 R6 - long runs with no HR points show not enough data', () => {
    const fixture = render(
      [
        longHrType({
          runCount: 1,
          runsWithoutHeartRate: 1,
          hasEnoughData: false,
          trend: null,
          points: [],
        }),
      ],
      'long',
    );
    const element = fixture.nativeElement as HTMLElement;
    expect(text(element)).toContain(
      'Not enough data for a comparison yet: 0 runs with heart rate, at least 3 needed.',
    );
    expect(element.querySelector('app-chart')).toBeNull();
  });
});
