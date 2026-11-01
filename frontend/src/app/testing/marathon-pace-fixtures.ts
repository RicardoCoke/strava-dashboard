import { MarathonPaceResponse, MpSummary, MpWeek } from '../core/api/marathon-pace.models';

// Invented marathon-pace data for frontend tests (today = Sat 2026-10-31, week 11 of the build).

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const WEEK_KM = [0, 3.2, 4.2, 2, 5, 3.6, 0, 4.4, 5.2, 6.8, 6.4, 0, 0, 0, 0, 0];

function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

function label(isoDate: string): string {
  const [, month, day] = isoDate.split('-').map(Number);
  return `${day} ${MONTHS[month - 1]}`;
}

export function mpWeek(overrides: Partial<MpWeek> = {}): MpWeek {
  return {
    weekIndex: 3,
    weekStart: '2026-08-31',
    weekEnd: '2026-09-06',
    label: '31 Aug',
    mpKm: 4.2,
    isCurrentWeek: false,
    isFuture: false,
    ...overrides,
  };
}

/** The 16 build weeks from Mon 17 Aug 2026; week 11 is the current week, 12–16 are upcoming. */
export function mpWeeks(): MpWeek[] {
  return WEEK_KM.map((mpKm, i) => {
    const weekStart = addDays('2026-08-17', i * 7);
    return {
      weekIndex: i + 1,
      weekStart,
      weekEnd: addDays(weekStart, 6),
      label: label(weekStart),
      mpKm,
      isCurrentWeek: i === 10,
      isFuture: i > 10,
    };
  });
}

export function mpSummary(overrides: Partial<MpSummary> = {}): MpSummary {
  return {
    totalMpKm: 41,
    thisWeekStart: '2026-10-26',
    thisWeekEnd: '2026-11-01',
    thisWeekMpKm: 6.4,
    thisWeekTotalKm: 26,
    thisWeekSharePercent: 25,
    ...overrides,
  };
}

export function marathonPace(overrides: Partial<MarathonPaceResponse> = {}): MarathonPaceResponse {
  return {
    goal: {
      paceSecondsPerKm: 255,
      toleranceSecondsPerKm: 10,
      fastestSecondsPerKm: 245,
      slowestSecondsPerKm: 265,
      minLapMeters: 200,
    },
    buildStart: '2026-08-17',
    buildEnd: '2026-12-06',
    weeks: mpWeeks(),
    summary: mpSummary(),
    runsPendingLaps: 0,
    ...overrides,
  };
}
