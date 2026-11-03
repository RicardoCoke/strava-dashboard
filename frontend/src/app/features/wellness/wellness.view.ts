import { HttpErrorResponse } from '@angular/common/http';
import { WellnessResponse, WellnessSource } from '../../core/api/wellness.models';
import { formatShortDate } from '../../shared/format/dates';

export type WellnessState =
  | { kind: 'loading' }
  | { kind: 'notConfigured' }
  | { kind: 'error' }
  | { kind: 'loaded'; data: WellnessResponse };

/** One "latest" tile: label, value text, delta label, date caption, and the flag text when off baseline. */
export interface WellnessTile {
  label: string;
  value: string | null;
  delta: string | null;
  caption: string | null;
  flag: string | null;
}

function stringField(body: unknown, key: string): string | null {
  if (typeof body !== 'object' || body === null) {
    return null;
  }
  const value = (body as Record<string, unknown>)[key];
  return typeof value === 'string' ? value : null;
}

/** The card state for a failed request, or null for a 401 (the interceptor redirects to /connect). */
export function wellnessStateFromError(error: unknown): WellnessState | null {
  if (!(error instanceof HttpErrorResponse)) {
    return { kind: 'error' };
  }
  if (error.status === 401) {
    return null;
  }
  if (error.status === 404 && stringField(error.error, 'error') === 'wellness_not_configured') {
    return { kind: 'notConfigured' };
  }
  return { kind: 'error' };
}

/** The single line shown when the window has no data, by how the last fetch went. */
export function emptyText(source: WellnessSource): string {
  switch (source.status) {
    case 'pending':
      return 'Waiting for the first sync from Garmin Connect.';
    case 'ok':
      return 'No resting HR or HRV in the last 12 weeks.';
    case 'keyRejected':
      return 'Garmin Connect rejected the API key. Check GARMIN_API_KEY, then restart the backend.';
    case 'rateLimited':
    case 'unavailable':
      return "Couldn't reach Garmin Connect. It will retry at the next sync.";
  }
}

/** The muted line above saved data when the last fetch failed; null when it went fine. */
export function staleText(source: WellnessSource): string | null {
  switch (source.status) {
    case 'ok':
    case 'pending':
      return null;
    case 'keyRejected':
      return 'Showing saved data: Garmin Connect rejected the API key.';
    case 'rateLimited':
    case 'unavailable':
      return "Showing saved data: Garmin Connect couldn't be reached at the last sync.";
  }
}

export function latestTiles(data: WellnessResponse): WellnessTile[] {
  const { restingHr, hrv } = data.latest;
  return [
    {
      label: 'Resting HR',
      value: restingHr ? `${restingHr.bpm} bpm` : null,
      delta: restingHr?.deltaLabel ?? null,
      caption: restingHr ? `on ${formatShortDate(restingHr.date)}` : null,
      flag: restingHr?.flagged ? 'Above baseline' : null,
    },
    {
      label: 'HRV',
      value: hrv ? `${hrv.ms} ms` : null,
      delta: hrv?.deltaLabel ?? null,
      caption: hrv ? `on ${formatShortDate(hrv.date)}` : null,
      flag: hrv?.flagged ? 'Below baseline' : null,
    },
  ];
}
