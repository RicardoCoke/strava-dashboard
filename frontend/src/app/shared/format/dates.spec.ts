import { formatDayMonth, formatShortDate } from './dates';

describe('date formatters', () => {
  it('005 R11 - formatShortDate gives weekday, day and month', () => {
    expect(formatShortDate('2026-10-31')).toBe('Sat 31 Oct');
    expect(formatShortDate('2026-04-30')).toBe('Thu 30 Apr');
    expect(formatDayMonth('2026-04-30')).toBe('30 Apr');
  });

  it('005 R11 - formatShortDate keeps the day at a UTC midnight edge', () => {
    // Local parsing of "2026-11-01" would land on Sat 31 Oct in zones west of UTC; this must stay Sunday.
    expect(formatShortDate('2026-11-01')).toBe('Sun 1 Nov');
    expect(formatShortDate('2026-12-06')).toBe('Sun 6 Dec');
  });
});
