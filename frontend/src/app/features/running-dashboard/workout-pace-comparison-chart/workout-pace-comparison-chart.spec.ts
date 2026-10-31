import type { EChartsOption, ScatterSeriesOption } from 'echarts';
import { By } from '@angular/platform-browser';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  PaceTypeComparison,
  WorkoutPaceComparison,
} from '../../../core/api/running-dashboard.models';
import { ChartComponent } from '../../../shared/chart/chart';
import { paceType, stubCharts } from '../../../testing/dashboard-fixtures';
import { WorkoutPaceComparisonChart } from './workout-pace-comparison-chart';

describe('WorkoutPaceComparisonChart (010)', () => {
  let fixture: ComponentFixture<WorkoutPaceComparisonChart>;

  beforeEach(() => stubCharts());

  const intervals = paceType({
    typeKey: '4x8min',
    label: '4x8min',
    runCount: 3,
    points: [
      { date: '2026-10-06', repPaceSecondsPerKm: 250, treadmill: false },
      { date: '2026-10-27', repPaceSecondsPerKm: 248, treadmill: false },
    ],
    current: { averageRepPaceSecondsPerKm: 249, runCount: 2 },
    previous: null,
    changeSecondsPerKm: null,
    runsWithoutPace: 1,
    hasEnoughData: false,
  });

  function render(types: PaceTypeComparison[], selected: string | null = null): HTMLElement {
    const comparison: WorkoutPaceComparison = {
      windowStart: '2026-05-29',
      windowEnd: '2026-10-29',
      currentWindow: { start: '2026-09-30', end: '2026-10-29' },
      previousWindow: { start: '2026-08-31', end: '2026-09-29' },
      types,
    };
    fixture = TestBed.createComponent(WorkoutPaceComparisonChart);
    fixture.componentRef.setInput('comparison', comparison);
    fixture.componentRef.setInput('selectedTypeKey', selected);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const text = (element: HTMLElement) => element.textContent?.replace(/\s+/g, ' ') ?? '';
  const chartData = () => {
    const options = fixture.debugElement
      .query(By.directive(ChartComponent))
      .componentInstance.options() as EChartsOption;
    return (options.series as ScatterSeriesOption[])
      .slice(1)
      .flatMap((series) => series.data as [string, number][]);
  };

  it('010 R4 - selector lists workout types with run counts', () => {
    const element = render([
      intervals,
      paceType(),
      paceType({ typeKey: '3x10min', label: '3x10min', runCount: 1 }),
    ]);
    const options = Array.from(element.querySelectorAll('option')).map((o) =>
      o.textContent?.trim(),
    );
    expect(options).toEqual(['4x8min (3 runs)', '5x6min (5 runs)', '3x10min (1 run)']);
    expect(element.querySelector('.type-picker')?.textContent).toContain('Workout type');
  });

  it('010 R4 - first type preselected', () => {
    const element = render([intervals, paceType()]);
    expect(element.querySelector<HTMLSelectElement>('select')?.value).toBe('4x8min');
    expect(chartData()).toHaveLength(2);
  });

  it('010 R4 - picking a type emits and switches the chart', () => {
    const element = render([intervals, paceType()]);
    const emitted: string[] = [];
    fixture.componentInstance.typeSelected.subscribe((key) => emitted.push(key));
    const select = element.querySelector<HTMLSelectElement>('select');
    if (select) {
      select.value = '5x6min';
      select.dispatchEvent(new Event('change'));
    }
    expect(emitted).toEqual(['5x6min']);

    fixture.componentRef.setInput('selectedTypeKey', '5x6min');
    fixture.detectChanges();
    expect(chartData()).toHaveLength(5);
  });

  it('010 R5 - single point still draws the chart', () => {
    const element = render([
      paceType({ points: [{ date: '2026-10-14', repPaceSecondsPerKm: 253, treadmill: false }] }),
    ]);
    expect(element.querySelector('app-chart')).not.toBeNull();
    expect(chartData()).toEqual([['2026-10-14', 253]]);
  });

  it('010 R5 - switching type replaces the points', () => {
    render([intervals, paceType()], '5x6min');
    expect(chartData().map(([date]) => date)).not.toContain('2026-10-27');
    fixture.componentRef.setInput('selectedTypeKey', '4x8min');
    fixture.detectChanges();
    expect(chartData()).toEqual([
      ['2026-10-06', 250],
      ['2026-10-27', 248],
    ]);
  });

  it('010 R7 - summary line shows thirty day change with tone', () => {
    const element = render([paceType()]);
    expect(text(element.querySelector('.summary') as HTMLElement).trim()).toBe(
      'Last 30 days vs previous 30 days: 4:20 /km → 4:13 /km · 7 s/km faster',
    );
    expect(element.querySelector('.summary .change')?.className).toContain('tone-better');
  });

  it('010 R7 - summary follows the selected type', () => {
    const element = render([paceType(), intervals], '4x8min');
    expect(text(element)).toContain(
      'Not enough data for a 30-day comparison: no paced runs in the previous 30 days',
    );
    expect(text(element)).not.toContain('4:20 /km → 4:13 /km');
  });

  it('010 R8 - runs without pace note', () => {
    expect(render([intervals]).querySelector('.note')?.textContent?.trim()).toBe(
      '1 run without a rep pace is not shown',
    );
    expect(render([paceType()]).querySelector('.note')).toBeNull();
  });

  it('010 R9 - empty state when no workout types', () => {
    const element = render([]);
    expect(element.querySelector('h2')?.textContent).toBe('Workout pace by type');
    expect(text(element)).toContain('No workouts (e.g. 5x6min) in the last 5 months');
    expect(element.querySelector('select')).toBeNull();
    expect(element.querySelector('app-chart')).toBeNull();
  });

  it('010 R9 - no points state keeps selector and summary', () => {
    const element = render([paceType({ points: [], runsWithoutPace: 5 })]);
    expect(element.querySelector('select')).not.toBeNull();
    expect(element.querySelector('app-chart')).toBeNull();
    expect(text(element)).toContain('No runs with a rep pace for this workout type yet');
    expect(element.querySelector('.summary')).not.toBeNull();
    expect(element.querySelector('.note')?.textContent?.trim()).toBe(
      '5 runs without a rep pace are not shown',
    );
  });

  it('010 R9 - card heading', () => {
    expect(render([paceType()]).querySelector('h2')?.textContent).toBe('Workout pace by type');
  });
});
