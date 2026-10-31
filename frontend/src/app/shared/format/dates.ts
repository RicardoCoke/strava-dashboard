const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function parts(isoDate: string): [number, number, number] {
  const [year, month, day] = isoDate.split('-').map(Number);
  return [year, month, day];
}

/** "2026-09-06" → "6 Sep". Works on the date text, so no time zone can shift the day. */
export function formatDayMonth(isoDate: string): string {
  const [, month, day] = parts(isoDate);
  return `${day} ${MONTHS[month - 1]}`;
}

/** "2026-10-31" → "Sat 31 Oct". The weekday is computed in UTC, so the browser's time zone can't shift it. */
export function formatShortDate(isoDate: string): string {
  const [year, month, day] = parts(isoDate);
  const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
  return `${weekday} ${formatDayMonth(isoDate)}`;
}

/** "2026-10-12" → "12 Oct 2026". */
export function formatDayMonthYear(isoDate: string): string {
  const [year] = parts(isoDate);
  return `${formatDayMonth(isoDate)} ${year}`;
}

/** "2027-08-17", "TUESDAY" → "Tue 17 Aug". */
export function formatWeekdayDayMonth(isoDate: string, dayOfWeek: string): string {
  const weekday = dayOfWeek.charAt(0) + dayOfWeek.slice(1, 3).toLowerCase();
  return `${weekday} ${formatDayMonth(isoDate)}`;
}
