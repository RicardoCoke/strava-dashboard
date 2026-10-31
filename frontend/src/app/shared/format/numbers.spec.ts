import { formatWeekdayDayMonth } from './dates';
import { formatTwoDecimals } from './numbers';

describe('plan formatters', () => {
  it('004 - formatWeekdayDayMonth', () => {
    expect(formatWeekdayDayMonth('2026-08-18', 'TUESDAY')).toBe('Tue 18 Aug');
    expect(formatWeekdayDayMonth('2026-12-06', 'SUNDAY')).toBe('Sun 6 Dec');
  });

  it('004 - formatTwoDecimals', () => {
    expect(formatTwoDecimals(62.4)).toBe('62.40');
    expect(formatTwoDecimals(79.25)).toBe('79.25');
    expect(formatTwoDecimals(0)).toBe('0.00');
  });
});
