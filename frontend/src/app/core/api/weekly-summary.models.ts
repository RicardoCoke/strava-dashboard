// The JSON contract of GET /api/weekly-summaries (docs/features/017-weekly-summary/specs.md).
// Mirrors backend/src/main/kotlin/app/stravadashboard/api/WeeklySummaryDtos.kt.

export type NoteMetric =
  | 'distance'
  | 'time'
  | 'longestRun'
  | 'pace'
  | 'trainingLoad'
  | 'hrDrift'
  | 'efficiency'
  | 'restingHr'
  | 'hrv';
export type NoteTone = 'worse' | 'better' | 'neutral';

export interface SummaryNote {
  metric: NoteMetric;
  tone: NoteTone;
  text: string;
}

export interface WeeklySummary {
  weekStart: string;
  weekEnd: string;
  inProgress: boolean;
  runCount: number;
  revision: string;
  notes: SummaryNote[];
}

export interface WeeklySummariesResponse {
  summaries: WeeklySummary[];
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

const TONES: readonly unknown[] = ['worse', 'better', 'neutral'];

function isNote(value: unknown): value is SummaryNote {
  return (
    isObject(value) &&
    typeof value['metric'] === 'string' &&
    TONES.includes(value['tone']) &&
    typeof value['text'] === 'string'
  );
}

function isSummary(value: unknown): value is WeeklySummary {
  return (
    isObject(value) &&
    typeof value['weekStart'] === 'string' &&
    typeof value['weekEnd'] === 'string' &&
    typeof value['inProgress'] === 'boolean' &&
    typeof value['runCount'] === 'number' &&
    typeof value['revision'] === 'string' &&
    Array.isArray(value['notes']) &&
    value['notes'].every(isNote)
  );
}

export function isWeeklySummariesResponse(value: unknown): value is WeeklySummariesResponse {
  return (
    isObject(value) && Array.isArray(value['summaries']) && value['summaries'].every(isSummary)
  );
}
