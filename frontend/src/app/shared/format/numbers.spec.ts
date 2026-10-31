import { formatWeekdayDayMonth } from './dates';
import { formatOneDecimal, formatSignedOneDecimal, formatTwoDecimals } from './numbers';

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

describe('training load formatters', () => {
  it('005 R11 - formatOneDecimal', () => {
    expect(formatOneDecimal(52.14)).toBe('52.1');
    expect(formatOneDecimal(0)).toBe('0.0');
  });

  it('005 R11 - formatSignedOneDecimal', () => {
    expect(formatSignedOneDecimal(-9.2)).toBe('-9.2');
    expect(formatSignedOneDecimal(3.2)).toBe('+3.2');
    expect(formatSignedOneDecimal(0)).toBe('0.0');
    expect(formatSignedOneDecimal(-0.01)).toBe('0.0');
  });
});
