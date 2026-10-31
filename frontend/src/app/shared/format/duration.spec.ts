import { formatRaceTime } from './duration';

describe('formatRaceTime', () => {
  it('008 R12 - formatRaceTime under and over one hour', () => {
    expect(formatRaceTime(1197)).toBe('19:57');
    expect(formatRaceTime(3599)).toBe('59:59');
    expect(formatRaceTime(3600)).toBe('1:00:00');
    expect(formatRaceTime(5495)).toBe('1:31:35');
    expect(formatRaceTime(11449)).toBe('3:10:49');
  });
});
