import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Vo2maxResponse } from '../../core/api/vo2max.models';
import { stubCharts } from '../../testing/dashboard-fixtures';
import { emptyVo2max, vo2max, vo2maxWeeks } from '../../testing/vo2max-fixtures';
import { Vo2maxSection } from './vo2max-section';

describe('Vo2maxSection', () => {
  let fixture: ComponentFixture<Vo2maxSection>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubCharts();
    fixture = TestBed.createComponent(Vo2maxSection);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';
  const all = (selector: string) =>
    Array.from(element().querySelectorAll(selector)).map((node) =>
      (node.textContent ?? '').replace(/\s+/g, ' ').trim(),
    );

  function respond(data: Vo2maxResponse): void {
    http.expectOne('/api/vo2max').flush(data);
    fixture.detectChanges();
  }

  function fail(status: number, body: object | null): void {
    http.expectOne('/api/vo2max').flush(body, { status, statusText: 'Error' });
    fixture.detectChanges();
  }

  it('R10 - card uses theme tokens and palette', () => {
    respond(vo2max());
    expect(element().querySelector('section')?.classList).toContain('card');
    // Angular adds component styles to <head>; the card's own rules must use --color-* tokens only.
    const cardStyles = Array.from(document.querySelectorAll('style'))
      .map((style) => style.textContent ?? '')
      .filter((css) => css.includes('.vo2max-value') || css.includes('.race-tile'));
    expect(cardStyles.length).toBeGreaterThan(0);
    cardStyles.forEach((css) => {
      expect(css).toContain('var(--color-');
      expect(css).not.toMatch(/#[0-9a-f]{3,8}\b|rgba?\(/i);
    });
  });

  it('R13 - loading', () => {
    expect(element().querySelector('h2')?.textContent).toBe('VO2max');
    expect(element().querySelector('[role="status"]')?.textContent).toContain('Loading VO2max…');
    expect(element().querySelector('app-vo2max-chart')).toBeNull();
  });

  it('R13 - invalid config without retry', () => {
    const message = 'MAX_HR_BPM is not a whole number between 120 and 230.';
    fail(422, { error: 'vo2max_invalid_config', message });
    expect(element().querySelector('[role="alert"]')?.textContent?.trim()).toBe(
      `VO2max settings have a problem: ${message}`,
    );
    expect(text()).toContain('Set MAX_HR_BPM in .env (for example 194), or leave it empty');
    expect(element().querySelector('button')).toBeNull();
  });

  it('R13 - error with retry', () => {
    fail(500, { error: 'internal_error', message: 'Something went wrong.' });
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      "Couldn't load VO2max.",
    );
    element().querySelector<HTMLButtonElement>('button.retry')?.click();
    fixture.detectChanges();
    expect(text()).toContain('Loading VO2max…');
    respond(vo2max());
    expect(element().querySelector('app-vo2max-chart')).not.toBeNull();
  });

  it('R13 - unexpected body shows the error state', () => {
    http.expectOne('/api/vo2max').flush({ weeks: 'nope' });
    fixture.detectChanges();
    expect(text()).toContain("Couldn't load VO2max.");
  });

  it('R10 - 401 shows no error text', () => {
    fail(401, { error: 'not_connected', message: 'Connect your Strava account.' });
    expect(element().querySelector('[role="alert"]')).toBeNull();
  });

  it('R13 - empty', () => {
    respond(emptyVo2max());
    expect(text()).toContain('No qualifying runs in the last 6 months');
    expect(text()).toContain('Distance-rep workouts (e.g. 6x1km) are not counted.');
    expect(element().querySelector('app-race-prediction-tiles')).toBeNull();
    expect(element().querySelector('app-vo2max-chart')).toBeNull();
  });

  it('R13 - empty still importing', () => {
    respond(emptyVo2max({ runsPendingDetail: 12 }));
    expect(text()).toContain('Lap heart-rate data is still importing: 12 runs to go.');
    expect(element().querySelector('app-vo2max-chart')).toBeNull();
  });

  it('R13 - no current estimate shows chart only', () => {
    respond(vo2max({ current: null, predictions: [] }));
    expect(text()).toContain(
      'No qualifying runs in the last 4 weeks, so there is no current estimate or race prediction.',
    );
    expect(element().querySelector('app-race-prediction-tiles')).toBeNull();
    expect(element().querySelector('app-vo2max-chart')).not.toBeNull();
    expect(element().querySelector('.footnote')).not.toBeNull();
  });

  it('R12 - current value and basis caption', () => {
    respond(vo2max());
    expect(all('.vo2max-value')).toEqual(['52.3']);
    expect(all('.vo2max-unit')).toEqual(['ml/kg/min']);
    expect(all('.vo2max-caption')).toEqual([
      'Based on 4 runs in the last 7 days (25 Oct – 31 Oct)',
    ]);
  });

  it('R12 - last 4 weeks caption and singular run', () => {
    respond(
      vo2max({
        current: {
          vo2max: 50,
          basis: 'last28Days',
          periodStart: '2026-10-04',
          periodEnd: '2026-10-31',
          runCount: 1,
        },
      }),
    );
    expect(all('.vo2max-caption')).toEqual(['Based on 1 run in the last 4 weeks (4 Oct – 31 Oct)']);
    expect(all('.vo2max-value')).toEqual(['50.0']);
  });

  it('R12 - four tiles with race times and marathon note only on marathon', () => {
    respond(vo2max());
    expect(all('.race-label')).toEqual(['5K', '10K', 'Half marathon', 'Marathon']);
    expect(all('.race-time')).toEqual(['19:17', '39:59', '1:28:31', '3:04:18']);
    expect(all('.race-note')).toEqual(['Marathon predictions from VO2max tend to be optimistic.']);
    expect(element().querySelector('[data-race="marathon"] .race-note')).not.toBeNull();
  });

  it('R12 - max HR footnote by source and pending note', () => {
    respond(vo2max({ runsPendingDetail: 12 }));
    expect(all('.footnote p')).toEqual([
      'Max HR 194 bpm (estimate; set MAX_HR_BPM to change)',
      '12 runs still importing lap heart rate; numbers will update.',
    ]);
  });

  it('R12 - configured max HR footnote', () => {
    respond(vo2max({ maxHr: { bpm: 190, source: 'configured' } }));
    expect(all('.footnote p')).toEqual(['Max HR 190 bpm (from MAX_HR_BPM)']);
  });

  it('R13 - normal order', () => {
    respond(vo2max({ weeks: vo2maxWeeks() }));
    const order = Array.from(element().querySelector('section')?.children ?? []).map(
      (child) => child.tagName.toLowerCase() + (child.className ? `.${child.className}` : ''),
    );
    expect(order).toEqual([
      'h2',
      'p.vo2max-current',
      'p.vo2max-caption',
      'app-race-prediction-tiles',
      'app-vo2max-chart',
      'div.footnote',
    ]);
  });
});
