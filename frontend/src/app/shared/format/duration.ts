function pad(value: number): string {
  return value.toString().padStart(2, '0');
}

/** Race time: 1197 → "19:57" under an hour, 11449 → "3:10:49" from an hour up. */
export function formatRaceTime(seconds: number): string {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const rest = seconds % 60;
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(rest)}` : `${minutes}:${pad(rest)}`;
}
