import { palette } from '../../shared/chart/palette';
import { marathonPace, mpWeek, mpWeeks } from '../../testing/marathon-pace-fixtures';
import { buildMarathonPaceOptions, weekTooltip } from './marathon-pace.options';

interface BarItem {
  value: number;
  itemStyle: { color: string };
  label: { show: boolean; formatter: () => string };
}

describe('buildMarathonPaceOptions', () => {
  const options = buildMarathonPaceOptions(marathonPace());
  const series = (options.series as { data: BarItem[] }[])[0];

  it('R6 - one bar per build week', () => {
    const xAxis = options.xAxis as { data: string[] };
    const yAxis = options.yAxis as { name: string };
    expect(xAxis.data).toEqual(mpWeeks().map((week) => week.label));
    expect(xAxis.data[0]).toBe('17 Aug');
    expect(yAxis.name).toBe('km');
    expect(series.data.map((item) => item.value)).toEqual(mpWeeks().map((week) => week.mpKm));
    expect(series.data.length).toBe(16);
  });

  it('R6 - bar colours and labels', () => {
    expect(series.data[10].itemStyle.color).toBe(palette.accent);
    expect(series.data[1].itemStyle.color).toBe(palette.secondary);
    expect(series.data[1].label.show).toBe(true);
    expect(series.data[1].label.formatter()).toBe('3.2');
    expect(series.data[0].label.show).toBe(true);
    expect(series.data[0].label.formatter()).toBe('0.0');
    expect(series.data[10].label.formatter()).toBe('6.4');
    expect(series.data.slice(11).every((item) => !item.label.show)).toBe(true);
  });

  it('R6 - tooltip text', () => {
    expect(weekTooltip(mpWeek())).toBe('Week 3 · 31 Aug – 6 Sep: 4.2 km at MP');
    expect(weekTooltip(mpWeek({ isCurrentWeek: true }))).toBe(
      'Week 3 · 31 Aug – 6 Sep: 4.2 km at MP (this week)',
    );
    expect(weekTooltip(mpWeek({ isFuture: true, mpKm: 0 }))).toBe(
      'Week 3 · 31 Aug – 6 Sep: 0.0 km at MP (upcoming)',
    );
    const tooltip = options.tooltip as { formatter: (params: { dataIndex: number }) => string };
    expect(tooltip.formatter({ dataIndex: 10 })).toBe(
      'Week 11 · 26 Oct – 1 Nov: 6.4 km at MP (this week)',
    );
  });
});
