import { HISTORY_DAYS, historyDay } from '../../testing/rings-history-fixtures';
import {
  buildRecoveryHistoryOptions,
  buildSleepHistoryOptions,
  buildStrainHistoryOptions,
  historyChartLabel,
  historyTooltipLine,
} from './recovery-history.options';

interface Axis {
  data?: string[];
  min?: number;
  max?: number;
  name?: string;
}
interface Series {
  type: string;
  data: unknown[];
  connectNulls?: boolean;
  showSymbol?: boolean;
  lineStyle?: { color: string };
}

const series = (options: object) => (options as { series: Series[] }).series;
const axis = (options: object, key: 'xAxis' | 'yAxis') => (options as Record<string, Axis>)[key];

describe('recovery history options', () => {
  it('R6 - one category per day and nulls stay gaps', () => {
    const options = buildSleepHistoryOptions(HISTORY_DAYS);
    expect(axis(options, 'xAxis').data).toEqual(['8 Nov', '9 Nov', '10 Nov']);
    const [line] = series(options);
    expect(line.data).toEqual([64, null, 82]);
    expect(line.connectNulls).toBe(false);
    expect(line.showSymbol).toBe(true);
    expect(series(buildRecoveryHistoryOptions(HISTORY_DAYS))[0].data[1]).toBeNull();
    expect(series(buildStrainHistoryOptions(HISTORY_DAYS))[0].data).toEqual([0, null, 12.9]);
  });

  it('R6 - tooltip lines and aria labels', () => {
    expect(historyTooltipLine('sleep', historyDay())).toBe('Sleep 82%');
    expect(historyTooltipLine('recovery', historyDay())).toBe('Recovery 75% (green)');
    expect(historyTooltipLine('strain', historyDay())).toBe('Strain 12.9');
    expect(historyTooltipLine('sleep', HISTORY_DAYS[1])).toBe('Sleep no data');
    expect(historyTooltipLine('recovery', HISTORY_DAYS[1])).toBe('Recovery no data');
    expect(historyTooltipLine('strain', HISTORY_DAYS[1])).toBe('Strain no data');
    expect(historyChartLabel('sleep', 30)).toBe('Sleep performance per day, last 30 days');
    expect(historyChartLabel('recovery', 7)).toBe('Recovery score per day, last 7 days');
    expect(historyChartLabel('strain', 90)).toBe('Strain per day, last 90 days');
  });

  it('R7 - sleep line 0-100', () => {
    const options = buildSleepHistoryOptions(HISTORY_DAYS);
    expect(series(options)[0].type).toBe('line');
    expect(series(options)[0].lineStyle?.color).toBe('#7ba1bb');
    expect(axis(options, 'yAxis')).toEqual(
      expect.objectContaining({ min: 0, max: 100, name: 'Sleep (%)' }),
    );
  });

  it('R7 - recovery bars coloured by zone', () => {
    const zones = [
      [33, 'red'],
      [34, 'yellow'],
      [66, 'yellow'],
      [67, 'green'],
    ] as const;
    const days = zones.map(([percent, zone], i) =>
      historyDay({ date: `2026-11-0${i + 1}`, recoveryPercent: percent, recoveryZone: zone }),
    );
    const options = buildRecoveryHistoryOptions(days);
    expect(series(options)[0].type).toBe('bar');
    expect(series(options)[0].data).toEqual([
      { value: 33, itemStyle: { color: '#ff0026' } },
      { value: 34, itemStyle: { color: '#ffde00' } },
      { value: 66, itemStyle: { color: '#ffde00' } },
      { value: 67, itemStyle: { color: '#16ec06' } },
    ]);
    expect(axis(options, 'yAxis')).toEqual(
      expect.objectContaining({ min: 0, max: 100, name: 'Recovery (%)' }),
    );
  });

  it('R7 - strain line 0-21', () => {
    const options = buildStrainHistoryOptions(HISTORY_DAYS);
    expect(series(options)[0].lineStyle?.color).toBe('#0093e7');
    expect(axis(options, 'yAxis')).toEqual(
      expect.objectContaining({ min: 0, max: 21, name: 'Strain (0–21)' }),
    );
  });

  it('R7 - no averages or legend', () => {
    const builders = [
      buildSleepHistoryOptions,
      buildRecoveryHistoryOptions,
      buildStrainHistoryOptions,
    ];
    for (const build of builders) {
      const options = build(HISTORY_DAYS) as Record<string, unknown>;
      expect(options['legend']).toBeUndefined();
      expect(series(options).length).toBe(1);
    }
  });
});
