/** 3.4 → "+3.4 km", −4.2 → "−4.2 km" (U+2212 minus), 0 or −0 → "±0.0 km". */
export function formatKmDifference(km: number): string {
  if (km > 0) {
    return `+${km.toFixed(1)} km`;
  }
  if (km < 0) {
    return `−${Math.abs(km).toFixed(1)} km`;
  }
  return '±0.0 km';
}
