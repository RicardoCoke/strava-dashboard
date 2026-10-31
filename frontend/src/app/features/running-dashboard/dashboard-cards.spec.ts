import type { EChartsOption, LineSeriesOption } from 'echarts';
import { By } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { ChartComponent } from '../../shared/chart/chart';
import { palette } from '../../shared/chart/palette';
import { hrType, stubCharts, week } from '../../testing/dashboard-fixtures';
import { HeartRateTrendChart } from './heart-rate-trend-chart/heart-rate-trend-chart';
import { WeeklyDistanceChart } from './weekly-distance-chart/weekly-distance-chart';

describe('dashboard cards', () => {
  beforeEach(() => stubCharts());

  it('R8 - weekly card shows empty state when no runs', () => {
    const fixture = TestBed.createComponent(WeeklyDistanceChart);
    fixture.componentRef.setInput('weekly', { weeks: [week({ runCount: 0, distanceKm: 0 })] });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('No runs in the last 9 weeks');
    expect(element.querySelector('app-chart')).toBeNull();
  });

  it('R9 - type selector lists types and emits the picked type', () => {
    const fixture = TestBed.createComponent(HeartRateTrendChart);
    const trend = {
      windowStart: '2026-05-29',
      windowEnd: '2026-10-29',
      minRunsForComparison: 3,
      types: [
        hrType({ runCount: 41 }),
        hrType({ typeKey: '5x6min', label: '5x6min', kind: 'workout', runCount: 6 }),
      ],
    };
    fixture.componentRef.setInput('trend', trend);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const options = Array.from(element.querySelectorAll('option')).map((o) =>
      o.textContent?.trim(),
    );
    expect(options).toEqual(['Easy runs (41 runs)', '5x6min (6 runs)']);
    expect(element.textContent).toContain('Trend: −8.0 bpm over the period (lower = fitter)');

    const emitted: string[] = [];
    fixture.componentInstance.typeSelected.subscribe((key) => emitted.push(key));
    const select = element.querySelector('select') as HTMLSelectElement;
    select.value = '5x6min';
    select.dispatchEvent(new Event('change'));
    expect(emitted).toEqual(['5x6min']);
  });

  it('R12 - HR card shows not enough data message', () => {
    const fixture = TestBed.createComponent(HeartRateTrendChart);
    const notEnough = hrType({
      hasEnoughData: false,
      trend: null,
      points: hrType().points.slice(0, 2),
    });
    fixture.componentRef.setInput('trend', {
      windowStart: '2026-05-29',
      windowEnd: '2026-10-29',
      minRunsForComparison: 3,
      types: [notEnough],
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent?.replace(/\s+/g, ' ')).toContain(
      'Not enough data for a comparison yet: 2 runs with heart rate, at least 3 needed.',
    );
    expect(element.querySelector('app-chart')).toBeNull();
  });

  it('003 R6 - switching type switches the line colouring', () => {
    const fixture = TestBed.createComponent(HeartRateTrendChart);
    const risingPoints = hrType().points.map((point, i) => ({ ...point, rising: i > 0 }));
    fixture.componentRef.setInput('trend', {
      windowStart: '2026-05-29',
      windowEnd: '2026-10-29',
      minRunsForComparison: 3,
      types: [
        hrType({ points: risingPoints }),
        hrType({ typeKey: '5x6min', label: '5x6min', kind: 'workout' }),
      ],
    });
    fixture.detectChanges();
    const lineColours = () => {
      const chart = fixture.debugElement.query(By.directive(ChartComponent))
        .componentInstance as ChartComponent;
      return ((chart.options() as EChartsOption).series as LineSeriesOption[])
        .filter((s) => s.name === 'HR line')
        .map((s) => s.lineStyle?.color);
    };
    expect(lineColours()).toContain(palette.worse);

    fixture.componentRef.setInput('selectedTypeKey', '5x6min');
    fixture.detectChanges();
    expect(lineColours()).toEqual([palette.accent]);
  });
});
