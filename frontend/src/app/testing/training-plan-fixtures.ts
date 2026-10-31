import {
  BuildProgress,
  DayOfWeek,
  PlanDay,
  PlanWeek,
  TrainingPlanResponse,
} from '../core/api/training-plan.models';

// Invented training-plan data for frontend tests.

const DAYS: DayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

export function planDay(overrides: Partial<PlanDay> = {}): PlanDay {
  return {
    date: '2026-08-17',
    dayOfWeek: 'MONDAY',
    session: "50' easy",
    note: null,
    totalMinutes: 50,
    easyMinutes: 50,
    subtMinutes: 0,
    distanceKm: 8.5,
    isRest: false,
    race: null,
    isToday: false,
    isPast: false,
    ...overrides,
  };
}

/** Week [index] (1-based) of a build starting Monday 17 Aug 2026. */
export function planWeek(index: number, overrides: Partial<PlanWeek> = {}): PlanWeek {
  const startDate = addDays('2026-08-17', (index - 1) * 7);
  return {
    index,
    label: index === 16 ? 'RACE WEEK' : `WEEK ${index}`,
    startDate,
    endDate: addDays(startDate, 6),
    distanceKm: 62.4,
    easyPercent: 79.25,
    subtPercent: 20.75,
    isCurrentWeek: false,
    days: DAYS.map((dayOfWeek, d) => planDay({ date: addDays(startDate, d), dayOfWeek })),
    ...overrides,
  };
}

export function progress(overrides: Partial<BuildProgress> = {}): BuildProgress {
  return {
    phase: 'inProgress',
    percentComplete: 66,
    daysPassed: 75,
    totalDays: 112,
    currentWeekIndex: 11,
    weekCount: 16,
    daysToGo: 36,
    daysUntilStart: null,
    label: '66% · week 11 of 16 · 36 days to go',
    ...overrides,
  };
}

/** 16 weeks, today = Sat 31 Oct 2026 (week 11), a 5K race in week 1 and the marathon on 6 Dec. */
export function trainingPlan(overrides: Partial<TrainingPlanResponse> = {}): TrainingPlanResponse {
  const weeks = Array.from({ length: 16 }, (_, i) => planWeek(i + 1));
  weeks[0].days[1] = { ...weeks[0].days[1], session: "20' WU/CD + 4x8' subT", note: 'hilly route' };
  weeks[0].days[2] = {
    ...weeks[0].days[2],
    session: 'Rest',
    isRest: true,
    totalMinutes: 0,
    easyMinutes: 0,
    distanceKm: 0,
  };
  weeks[0].days[6] = {
    ...weeks[0].days[6],
    session: "20' WU/CD + 5K RACE",
    race: { label: '5K race', distanceKm: 5, isMarathon: false },
  };
  weeks[10] = { ...weeks[10], isCurrentWeek: true };
  weeks[10].days[5] = { ...weeks[10].days[5], isToday: true };
  // Every day before today (Sat of week 11) has passed.
  weeks.forEach((week, w) =>
    week.days.forEach((day, d) => {
      week.days[d] = { ...day, isPast: w < 10 || (w === 10 && d < 5) };
    }),
  );
  weeks[15].days[6] = {
    ...weeks[15].days[6],
    session: 'RACE 42,2K',
    totalMinutes: 170,
    distanceKm: 42.2,
    race: { label: 'Marathon · 42.2 km', distanceKm: 42.2, isMarathon: true },
  };
  return {
    startDate: '2026-08-17',
    endDate: '2026-12-06',
    progress: progress(),
    targetPaces: [
      {
        key: 'easy',
        label: 'Easy',
        targetSecondsPerKm: 338,
        minSecondsPerKm: null,
        maxSecondsPerKm: null,
      },
      {
        key: '15k',
        label: '15K',
        targetSecondsPerKm: 245,
        minSecondsPerKm: 240,
        maxSecondsPerKm: 250,
      },
      {
        key: '30k',
        label: '30K',
        targetSecondsPerKm: null,
        minSecondsPerKm: 255,
        maxSecondsPerKm: 262,
      },
    ],
    weeks,
    ...overrides,
  };
}
