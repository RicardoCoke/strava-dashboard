/** −8 → "−8.0" (U+2212 minus), 3.2 → "+3.2", 0 or −0 → "0.0". */
export function formatBpmChange(change: number): string {
  const sign = change < 0 ? '−' : change > 0 ? '+' : '';
  return `${sign}${Math.abs(change).toFixed(1)}`;
}
