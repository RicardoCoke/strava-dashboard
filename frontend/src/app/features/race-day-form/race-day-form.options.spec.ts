import type { LineSeriesOption } from 'echarts';
import { palette } from '../../shared/chart/palette';
import { notEnoughRunsForm, raceDayForm } from '../../testing/race-day-form-fixtures';
import {
  BAND_SERIES,
  FATIGUE_SERIES,
  FITNESS_SERIES,
  FORM_SERIES,
  RACE_DAY_FORM_CHART_LABEL,
  buildRaceDayFormOptions,
  formDayTooltipLines,
} from './race-day-form.options';

type Formatter = (params: unknown) => string;

const series = (data = raceDayForm()) => buildRaceDayFormOptions(data).series as LineSeriesOption[];
const named = (name: string, data = raceDayForm()) => series(data).filter((s) => s.name === name);

describe('race-day form options (011)', () => {
  it('R14 - actual solid planned dashed', () => {
    // lastActualDate 2026-10-30 is index 41.
    for (const name of [FITNESS_SERIES, FATIGUE_SERIES, FORM_SERIES]) {
      const [actual, planned] = named(name);
      expect(actual.lineStyle?.type).toBe('solid');
      expect(planned.lineStyle?.type).toBe('dashed');
      const actualData = actual.data as (number | null)[];
      const plannedData = planned.data as (number | null)[];
      expect(actualData[41]).not.toBeNull();
      expect(actualData[42]).toBeNull();
      expect(plannedData[40]).toBeNull();
      // The two share index 41, so the line has no gap.
      expect(plannedData[41]).toBe(actualData[41]);
      expect(plannedData[78]).not.toBeNull();
    }
    expect(named(FITNESS_SERIES)[0].lineStyle?.color).toBe(palette.fitness);
    expect(named(FATIGUE_SERIES)[0].lineStyle?.color).toBe(palette.fatigue);
    expect(named(FORM_SERIES)[0].lineStyle?.color).toBe(palette.form);
  });

  it('R14 - race-day markLine', () => {
    const markLine = named(FITNESS_SERIES)[0].markLine as {
      data: { xAxis: string }[];
      label: { formatter: string };
      lineStyle: { type: string; color: string };
    };
    expect(markLine.data).toEqual([{ xAxis: '6 Dec' }]);
    expect(markLine.label.formatter).toBe('Race day');
    expect(markLine.lineStyle).toMatchObject({ type: 'solid', color: palette.accent });
    expect(named(FITNESS_SERIES, notEnoughRunsForm())[0].markLine).toBeUndefined();
  });

  it('R14 - stacked ready band', () => {
    const [low, band] = series().slice(0, 2);
    expect(low).toMatchObject({ stack: 'ready', silent: true, lineStyle: { width: 0 } });
    expect(low.areaStyle).toBeUndefined();
    expect(band).toMatchObject({
      name: BAND_SERIES,
      stack: 'ready',
      areaStyle: { color: palette.readyBand },
    });
    const days = raceDayForm().days;
    expect((band.data as number[])[0]).toBeCloseTo(days[0].readyHigh - days[0].readyLow);
    expect(band.z).toBeLessThan(named(FITNESS_SERIES)[0].z ?? 0);
  });

  it('R14 - legend', () => {
    expect(buildRaceDayFormOptions(raceDayForm()).legend).toMatchObject({
      data: [FITNESS_SERIES, FATIGUE_SERIES, FORM_SERIES, BAND_SERIES],
    });
  });

  it('R14 - tooltip lines', () => {
    const planned = {
      date: '2026-11-07',
      kind: 'planned' as const,
      load: 65,
      fitness: 58.3,
      fatigue: 40.1,
      form: 18.2,
      readyLow: 2.9,
      readyHigh: 14.6,
    };
    expect(formDayTooltipLines(planned)).toEqual([
      'Sat 7 Nov · planned',
      'Fitness 58.3',
      'Fatigue 40.1',
      'Form +18.2',
      'Load 65.0',
      'Ready band 2.9–14.6',
    ]);
    expect(formDayTooltipLines({ ...planned, date: '2026-10-31', kind: 'actual' })[0]).toBe(
      'Sat 31 Oct',
    );
    const formatter = (buildRaceDayFormOptions(raceDayForm()).tooltip as { formatter: Formatter })
      .formatter;
    expect(formatter([{ dataIndex: 0 }])).toContain('Sat 19 Sep');
  });

  it('R14 - aria label', () => {
    expect(RACE_DAY_FORM_CHART_LABEL).toBe(
      'Race-day form: fitness, fatigue and form from 6 weeks ago to race day; planned days dashed',
    );
  });

  it('R14 - no planned series when not enough runs', () => {
    for (const name of [FITNESS_SERIES, FATIGUE_SERIES, FORM_SERIES]) {
      expect(named(name, notEnoughRunsForm())).toHaveLength(1);
    }
  });
});
