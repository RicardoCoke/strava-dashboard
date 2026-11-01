/**
 * The dark theme colours for ECharts (which draws on a canvas and can't read CSS variables).
 * Same values as the --color-* tokens in src/styles.scss.
 */
export const palette = {
  background: '#0b0d0f',
  surface: '#17191c',
  surfaceRaised: '#22262a',
  border: '#2a2e33',
  text: '#f2f4f5',
  textMuted: '#9aa3ad',
  accent: '#00f19f',
  secondary: '#6f8299',
  worse: '#ff6b4a',
  treadmill: '#a78bfa',
  // Long runs in the efficiency factor chart (feature 014).
  longRun: '#f5b942',
  // The "watch" drift zone (feature 013).
  caution: '#ffc857',
  grid: 'rgba(255, 255, 255, 0.06)',
  // Training load lines (feature 005), reusing the values above.
  fitness: '#00f19f',
  fatigue: '#a78bfa',
  form: '#f2f4f5',
  // The race-day form ready band (feature 011): the accent at 12 %.
  readyBand: 'rgba(0, 241, 159, 0.12)',
  // Resting HR and HRV lines (feature 015), reusing the values above.
  restingHr: '#a78bfa',
  hrv: '#00f19f',
} as const;
