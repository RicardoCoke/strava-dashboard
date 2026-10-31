/** 253 → "4:13". */
export function formatPace(secondsPerKm: number): string {
  const total = Math.round(secondsPerKm);
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

/** Negative means faster: −7 → "7 s/km faster", 3 → "3 s/km slower", 0 → "same pace". */
export function formatPaceChange(deltaSecondsPerKm: number): string {
  if (deltaSecondsPerKm === 0) {
    return 'same pace';
  }
  const direction = deltaSecondsPerKm < 0 ? 'faster' : 'slower';
  return `${Math.abs(deltaSecondsPerKm)} s/km ${direction}`;
}
