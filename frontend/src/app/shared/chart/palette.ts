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
  grid: 'rgba(255, 255, 255, 0.06)',
} as const;
