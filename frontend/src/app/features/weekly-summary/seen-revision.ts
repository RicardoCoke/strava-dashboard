// The revision of the weekly summary last opened in this browser: an opaque hash, no personal data.
const KEY = 'sd.weeklySummary.seenRevision';

/** Null when nothing is stored or storage is unavailable, so the summary counts as unseen. */
export function readSeenRevision(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function writeSeenRevision(revision: string): void {
  try {
    localStorage.setItem(KEY, revision);
  } catch {
    // Storage disabled: the badge just shows again next time.
  }
}

/** The ISO date of the Monday before [date]'s week, in the browser's local time. */
export function lastWeekMonday(date: Date): string {
  const monday = new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate() - ((date.getDay() + 6) % 7) - 7,
  );
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${monday.getFullYear()}-${pad(monday.getMonth() + 1)}-${pad(monday.getDate())}`;
}
