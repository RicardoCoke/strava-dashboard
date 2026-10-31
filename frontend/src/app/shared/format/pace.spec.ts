import { PacePipe } from '../pipes/pace.pipe';
import { formatDayMonth, formatDayMonthYear } from './dates';
import { formatPace, formatPaceChange } from './pace';

describe('pace and date formatting', () => {
  it('R11 - formats pace as m ss', () => {
    expect(formatPace(253)).toBe('4:13');
    expect(formatPace(240)).toBe('4:00');
    expect(formatPace(605)).toBe('10:05');
  });

  it('R11 - formats pace change', () => {
    expect(formatPaceChange(-7)).toBe('7 s/km faster');
    expect(formatPaceChange(3)).toBe('3 s/km slower');
    expect(formatPaceChange(0)).toBe('same pace');
  });

  it('pace pipe appends /km', () => {
    expect(new PacePipe().transform(253)).toBe('4:13 /km');
  });

  it('formats ISO dates without time zone shifts', () => {
    expect(formatDayMonth('2026-09-06')).toBe('6 Sep');
    expect(formatDayMonthYear('2026-10-12')).toBe('12 Oct 2026');
  });
});
