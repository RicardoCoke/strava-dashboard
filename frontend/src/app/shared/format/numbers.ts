/** 62.4 → "62.40". */
export function formatTwoDecimals(value: number): string {
  return value.toFixed(2);
}

/** 52.14 → "52.1". */
export function formatOneDecimal(value: number): string {
  return value.toFixed(1);
}

/** -9.2 → "-9.2", 3.2 → "+3.2", 0 → "0.0" (never "-0.0" or "+0.0"). */
export function formatSignedOneDecimal(value: number): string {
  const text = formatOneDecimal(Math.abs(value));
  if (Number(text) === 0) {
    return text;
  }
  return value > 0 ? `+${text}` : `-${text}`;
}
