import { Person } from '../../core/person/person.store';

// The revision of the weekly summary last opened in this browser: an opaque hash, no personal data.
const KEY = 'sd.weeklySummary.seenRevision';

// Ricardo keeps the key from before feature 019, so shipping it doesn't relight his badge.
const keyFor = (person: Person): string => (person === 'ricardo' ? KEY : `${KEY}.${person}`);

/** Null when nothing is stored or storage is unavailable, so the summary counts as unseen. */
export function readSeenRevision(person: Person = 'ricardo'): string | null {
  try {
    return localStorage.getItem(keyFor(person));
  } catch {
    return null;
  }
}

export function writeSeenRevision(revision: string, person: Person = 'ricardo'): void {
  try {
    localStorage.setItem(keyFor(person), revision);
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
