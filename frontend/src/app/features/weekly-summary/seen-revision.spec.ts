import { lastWeekMonday, readSeenRevision, writeSeenRevision } from './seen-revision';

describe('seen-revision', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('R11 - stores and reads the revision', () => {
    expect(readSeenRevision()).toBeNull();
    writeSeenRevision('abc');
    expect(readSeenRevision()).toBe('abc');
    // 019 R15: Ricardo keeps the old key, Marina has her own.
    expect(localStorage.getItem('sd.weeklySummary.seenRevision')).toBe('abc');
    expect(readSeenRevision('marina')).toBeNull();
    writeSeenRevision('xyz', 'marina');
    expect(localStorage.getItem('sd.weeklySummary.seenRevision.marina')).toBe('xyz');
    expect(readSeenRevision()).toBe('abc');
  });

  it('R11 - survives storage errors', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('disabled');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('disabled');
    });
    expect(readSeenRevision()).toBeNull();
    expect(() => writeSeenRevision('abc')).not.toThrow();
  });

  it('R11 - last week Monday from any weekday', () => {
    expect(lastWeekMonday(new Date(2026, 10, 8))).toBe('2026-10-26'); // Sunday
    expect(lastWeekMonday(new Date(2026, 10, 2))).toBe('2026-10-26'); // Monday
    expect(lastWeekMonday(new Date(2026, 0, 3))).toBe('2025-12-22'); // across the year
  });
});
