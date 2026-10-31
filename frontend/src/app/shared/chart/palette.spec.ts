import { palette } from './palette';

/** WCAG 2.x relative luminance of a #rrggbb colour. */
function luminance(hex: string): number {
  const channels = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const [r, g, b] = channels.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (light + 0.05) / (dark + 0.05);
}

describe('dark theme palette', () => {
  it('002 R1 - palette exposes the dark theme colours', () => {
    expect(palette).toEqual({
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
      fitness: '#00f19f',
      fatigue: '#a78bfa',
      form: '#f2f4f5',
    });
  });

  it('005 R14 - palette has load colours from existing values', () => {
    expect(palette.fitness).toBe(palette.accent);
    expect(palette.fatigue).toBe(palette.treadmill);
    expect(palette.form).toBe(palette.text);
  });

  it('002 R10 - text colours reach 4.5 to 1 on all surfaces', () => {
    const surfaces = [palette.background, palette.surface, palette.surfaceRaised];
    [palette.text, palette.textMuted, palette.accent, palette.worse].forEach((fg) =>
      surfaces.forEach((bg) =>
        expect(contrast(fg, bg), `${fg} on ${bg}`).toBeGreaterThanOrEqual(4.5),
      ),
    );
    expect(contrast(palette.background, palette.accent)).toBeGreaterThanOrEqual(4.5);
  });

  it('002 R10 - chart colours reach 3 to 1 on surface', () => {
    [palette.secondary, palette.accent, palette.treadmill].forEach((fg) =>
      expect(contrast(fg, palette.surface)).toBeGreaterThanOrEqual(3),
    );
  });
});
