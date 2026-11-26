import {
  CompareRun,
  CompareRunItem,
  RepComparison,
  WorkoutCompareResponse,
} from '../core/api/workout-compare.models';

// All runs here are invented.

export function compareRunItem(overrides: Partial<CompareRunItem> = {}): CompareRunItem {
  return {
    id: 2,
    date: '2026-11-24',
    name: '8x3min hills',
    typeKey: '8x3min',
    distanceKm: 10.1,
    averagePaceSecondsPerKm: 306,
    treadmill: false,
    ...overrides,
  };
}

export const COMPARE_RUNS: CompareRunItem[] = [
  compareRunItem(),
  compareRunItem({
    id: 1,
    date: '2026-11-10',
    name: '5x6min threshold',
    typeKey: '5x6min',
    distanceKm: 11.2,
  }),
  compareRunItem({
    id: 3,
    date: '2026-11-08',
    name: '',
    typeKey: 'easy',
    treadmill: true,
    averagePaceSecondsPerKm: null,
  }),
];

function run(overrides: Partial<CompareRun>): CompareRun {
  return {
    id: 1,
    date: '2026-11-10',
    name: '5x6min threshold',
    typeKey: '5x6min',
    treadmill: false,
    stats: {
      distanceKm: 11.2,
      movingTimeSeconds: 3480,
      averagePaceSecondsPerKm: 311,
      averageHeartRateBpm: 149,
      elevationGainMeters: 38,
      efficiencyFactor: 1.296,
      hrDriftPct: null,
    },
    points: [
      { distanceKm: 0.027, timeSeconds: 10, paceSecondsPerKm: 370, heartRateBpm: 101 },
      { distanceKm: 0.055, timeSeconds: 20, paceSecondsPerKm: 357, heartRateBpm: null },
    ],
    ...overrides,
  };
}

export const PIECES: RepComparison = {
  mode: 'pieces',
  unavailableReason: null,
  unavailableRunId: null,
  pieceMinutes: 3,
  piecesRunId: 1,
  average: {
    earlier: { paceSecondsPerKm: 252, heartRateBpm: 166 },
    later: { paceSecondsPerKm: 244, heartRateBpm: 163 },
    paceDiffSecondsPerKm: -8,
    heartRateDiffBpm: -3,
  },
  pairs: [
    {
      earlier: { paceSecondsPerKm: 255, heartRateBpm: 160 },
      later: { paceSecondsPerKm: 246, heartRateBpm: 158 },
      paceDiffSecondsPerKm: -9,
      heartRateDiffBpm: -2,
    },
    {
      earlier: { paceSecondsPerKm: 251, heartRateBpm: 168 },
      later: { paceSecondsPerKm: 243, heartRateBpm: null },
      paceDiffSecondsPerKm: -8,
      heartRateDiffBpm: null,
    },
    {
      earlier: { paceSecondsPerKm: 250, heartRateBpm: 171 },
      later: null,
      paceDiffSecondsPerKm: null,
      heartRateDiffBpm: null,
    },
  ],
};

/** The spec's example: an earlier 5x6min cut into 3-minute pieces against a later 8x3min. */
export function comparison(
  overrides: Partial<WorkoutCompareResponse> = {},
): WorkoutCompareResponse {
  return {
    resolution: 'stream',
    coarse: null,
    runs: [
      run({}),
      run({
        id: 2,
        date: '2026-11-24',
        name: '8x3min hills',
        typeKey: '8x3min',
        stats: {
          distanceKm: 10.1,
          movingTimeSeconds: 3090,
          averagePaceSecondsPerKm: 306,
          averageHeartRateBpm: 147,
          elevationGainMeters: 25,
          efficiencyFactor: 1.334,
          hrDriftPct: 3.8,
        },
        points: [
          { distanceKm: 0.029, timeSeconds: 10, paceSecondsPerKm: 345, heartRateBpm: 98 },
          { distanceKm: 0.058, timeSeconds: 20, paceSecondsPerKm: null, heartRateBpm: 103 },
        ],
      }),
    ],
    reps: PIECES,
    ...overrides,
  };
}
