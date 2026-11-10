import { RingsHistoryDay } from '../core/api/rings.models';

// All data here is invented.
export function historyDay(overrides: Partial<RingsHistoryDay> = {}): RingsHistoryDay {
  return {
    date: '2026-11-10',
    sleepPercent: 82,
    recoveryPercent: 75,
    recoveryZone: 'green',
    strain: 12.9,
    ...overrides,
  };
}

export const HISTORY_DAYS: RingsHistoryDay[] = [
  historyDay({
    date: '2026-11-08',
    sleepPercent: 64,
    recoveryPercent: 33,
    recoveryZone: 'red',
    strain: 0,
  }),
  historyDay({
    date: '2026-11-09',
    sleepPercent: null,
    recoveryPercent: null,
    recoveryZone: null,
    strain: null,
  }),
  historyDay({ date: '2026-11-10' }),
];
