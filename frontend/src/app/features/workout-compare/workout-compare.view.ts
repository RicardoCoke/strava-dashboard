import { formatDate } from '@angular/common';
import {
  CoarseReason,
  CompareFilter,
  CompareRunItem,
  RepCell,
  RepRow,
  WorkoutCompareResponse,
} from '../../core/api/workout-compare.models';
import { formatDayMonth, formatShortDate } from '../../shared/format/dates';
import { formatRaceTime } from '../../shared/format/duration';
import { formatBpmChange } from '../../shared/format/heart-rate';
import {
  formatOneDecimal,
  formatSignedOneDecimal,
  formatTwoDecimals,
} from '../../shared/format/numbers';
import { formatPace, formatPaceChange } from '../../shared/format/pace';

// Pure text for the Compare page (feature 025). Run names are never put into chart HTML; the page interpolates them.

export type RunRole = 'earlier' | 'later';

export const PIECES_CAVEAT =
  'Pieces are cut from the longer reps. Longer reps are usually run a little slower, and heart rate keeps rising ' +
  'through each rep, so pieces tend to look slower with a higher heart rate. This shows whether you held short-rep ' +
  "pace inside the longer reps, not purely whether you're fitter.";

export const DRIFT_FOOTNOTE =
  'HR drift needs at least 60 minutes of detailed pace and heart-rate data.';

const NONE = '—';

/** "Earlier · 10 Nov". */
export function runLabel(role: RunRole, date: string): string {
  return `${role === 'earlier' ? 'Earlier' : 'Later'} · ${formatDayMonth(date)}`;
}

export function runLabels(data: WorkoutCompareResponse): [string, string] {
  return [runLabel('earlier', data.runs[0].date), runLabel('later', data.runs[1].date)];
}

function labelOf(data: WorkoutCompareResponse, id: number | null): string {
  const [earlier, later] = runLabels(data);
  return id === data.runs[0].id ? earlier : later;
}

export function typeLabel(typeKey: string): string {
  return typeKey === 'easy' ? 'Easy' : typeKey === 'long' ? 'Long' : typeKey;
}

function paceText(secondsPerKm: number | null): string {
  return `${secondsPerKm === null ? NONE : formatPace(secondsPerKm)} /km`;
}

/** "Tue 24 Nov · 8x3min · 10.1 km · 5:06 /km". */
export function listRowText(run: CompareRunItem): string {
  const distance = `${formatOneDecimal(run.distanceKm)} km`;
  return `${formatShortDate(run.date)} · ${typeLabel(run.typeKey)} · ${distance} · ${paceText(run.averagePaceSecondsPerKm)}`;
}

const EMPTY_LIST: Record<CompareFilter, string> = {
  all: 'No runs yet.',
  workouts: 'No workouts yet.',
  long: 'No long runs yet.',
  easy: 'No easy runs yet.',
};

export function emptyListText(filter: CompareFilter): string {
  return EMPTY_LIST[filter];
}

export function promptText(selected: number): string {
  return selected === 0 ? 'Pick two runs to compare.' : 'Pick one more run to compare.';
}

const COARSE_TEXT: Record<CoarseReason, (label: string, time: string) => string> = {
  beforeGarmin: (label) => `the ${label} run was synced from Strava, which kept no detailed data.`,
  treadmill: (label) => `the ${label} run is a treadmill run.`,
  noDetailFile: (label) => `Garmin Connect has no detailed data for the ${label} run.`,
  callLimit: (_, time) => `the Garmin Connect call limit is used up. Try again ${time}.`,
  rateLimited: (_, time) => `Garmin Connect is limiting requests. Try again ${time}.`,
  garminUnavailable: () => "couldn't reach Garmin Connect.",
  notConfigured: () => "Garmin Connect isn't set up for this person.",
};

/** Why both runs are drawn from per-km splits, or null when the chart has second-by-second data. */
export function coarseNote(data: WorkoutCompareResponse): string | null {
  const coarse = data.coarse;
  if (coarse === null) {
    return null;
  }
  const time = coarse.retryAfter
    ? `after ${formatDate(coarse.retryAfter, 'HH:mm', 'en-US')}`
    : 'later';
  const reason = COARSE_TEXT[coarse.reason](labelOf(data, coarse.runId), time);
  return `Showing per-km splits, which are coarser than second-by-second data: ${reason}`;
}

/** Both runs without points: the chart is replaced by a note. */
export function hasNoPoints(data: WorkoutCompareResponse): boolean {
  return data.runs.every((run) => run.points.length === 0);
}

/** A note when exactly one run has no points; the chart still shows the other. */
export function missingPointsNote(data: WorkoutCompareResponse): string | null {
  const missing = data.runs.filter((run) => run.points.length === 0);
  return missing.length === 1
    ? `No per-km data for the ${labelOf(data, missing[0].id)} run.`
    : null;
}

export interface StatsRow {
  label: string;
  earlier: string;
  later: string;
}

function orNone<T>(value: T | null, format: (value: T) => string): string {
  return value === null ? NONE : format(value);
}

const STATS: { label: string; text: (run: WorkoutCompareResponse['runs'][number]) => string }[] = [
  { label: 'Distance', text: (run) => `${formatOneDecimal(run.stats.distanceKm)} km` },
  { label: 'Moving time', text: (run) => orNone(run.stats.movingTimeSeconds, formatRaceTime) },
  { label: 'Avg pace', text: (run) => orNone(run.stats.averagePaceSecondsPerKm, paceText) },
  { label: 'Avg HR', text: (run) => orNone(run.stats.averageHeartRateBpm, (bpm) => `${bpm} bpm`) },
  {
    label: 'Elevation gain',
    text: (run) => orNone(run.stats.elevationGainMeters, (m) => `${m} m`),
  },
  {
    label: 'Efficiency factor',
    text: (run) => orNone(run.stats.efficiencyFactor, formatTwoDecimals),
  },
  {
    label: 'HR drift',
    text: (run) => orNone(run.stats.hrDriftPct, (pct) => `${formatSignedOneDecimal(pct)}%`),
  },
];

export function statsRows(data: WorkoutCompareResponse): StatsRow[] {
  return STATS.map(({ label, text }) => ({
    label,
    earlier: text(data.runs[0]),
    later: text(data.runs[1]),
  }));
}

export function showDriftFootnote(data: WorkoutCompareResponse): boolean {
  return data.runs.some((run) => run.stats.hrDriftPct === null);
}

export interface ColumnHeader {
  label: string;
  detail: string;
}

/** Stats columns: the run label, then its name (plain text, interpolated by the page). */
export function statsHeaders(data: WorkoutCompareResponse): ColumnHeader[] {
  const labels = runLabels(data);
  return data.runs.map((run, i) => ({ label: labels[i], detail: run.name }));
}

/** Rep columns: the run label, then its type, marked when it is the one cut into pieces. */
export function repHeaders(data: WorkoutCompareResponse): ColumnHeader[] {
  const labels = runLabels(data);
  const reps = data.reps;
  return data.runs.map((run, i) => {
    const cut =
      reps?.mode === 'pieces' && reps.piecesRunId === run.id
        ? ` as ${reps.pieceMinutes}-min pieces`
        : '';
    return { label: labels[i], detail: `${run.typeKey}${cut}` };
  });
}

export interface RepTableRow {
  number: string;
  earlier: string;
  later: string;
  difference: string;
}

function cellText(cell: RepCell | null): string {
  if (cell === null) {
    return NONE;
  }
  const heartRate = cell.heartRateBpm === null ? '' : ` · ${cell.heartRateBpm} bpm`;
  return `${formatPace(cell.paceSecondsPerKm)} /km${heartRate}`;
}

function differenceText(row: RepRow): string {
  if (row.paceDiffSecondsPerKm === null) {
    return '';
  }
  const heartRate =
    row.heartRateDiffBpm === null ? '' : ` · ${formatBpmChange(row.heartRateDiffBpm)} bpm`;
  return `${formatPaceChange(row.paceDiffSecondsPerKm)}${heartRate}`;
}

/** "All" first, then rep (or piece) 1…n; empty when the reps can't be compared. */
export function repRows(data: WorkoutCompareResponse): RepTableRow[] {
  const reps = data.reps;
  if (reps === null || reps.average === null) {
    return [];
  }
  const row = (number: string, r: RepRow) => ({
    number,
    earlier: cellText(r.earlier),
    later: cellText(r.later),
    difference: differenceText(r),
  });
  return [row('All', reps.average), ...reps.pairs.map((pair, i) => row(String(i + 1), pair))];
}

export interface RepsNote {
  message: string;
  averages: string[];
}

/** Why the reps can't be compared, with whole-session averages; null unless the mode is `unavailable`. */
export function repsNote(data: WorkoutCompareResponse): RepsNote | null {
  const reps = data.reps;
  if (reps?.mode !== 'unavailable') {
    return null;
  }
  const labels = runLabels(data);
  const missing = data.runs.find((run) => run.id === reps.unavailableRunId);
  const message =
    reps.unavailableReason === 'repsNotFound' && missing
      ? `Couldn't find the ${missing.typeKey} reps in the ${labelOf(data, missing.id)} run.`
      : "Reps can't be compared without detailed data for both runs.";
  const averages = data.runs.map((run, i) => {
    const heartRate = orNone(run.stats.averageHeartRateBpm, (bpm) => `${bpm} bpm`);
    return `${labels[i]}: ${paceText(run.stats.averagePaceSecondsPerKm)} · ${heartRate}`;
  });
  return { message, averages };
}
