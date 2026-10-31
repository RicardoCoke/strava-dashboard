import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { stubCharts } from '../../testing/dashboard-fixtures';
import { trainingLoad, weekRamp } from '../../testing/training-load-fixtures';
import { TrainingLoadResponse } from '../../core/api/training-load.models';
import { TrainingLoadSection } from './training-load-section';

const ADVICE_WORDS =
  /\b(rest|recover|recovery|should|must|reduce|overtrained|overtraining|taper)\b/i;

describe('TrainingLoadSection', () => {
  let fixture: ComponentFixture<TrainingLoadSection>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    stubCharts();
    fixture = TestBed.createComponent(TrainingLoadSection);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';
  const all = (selector: string) =>
    Array.from(element().querySelectorAll(selector)).map((node) =>
      (node.textContent ?? '').replace(/\s+/g, ' ').trim(),
    );

  function respond(data: TrainingLoadResponse): void {
    http.expectOne('/api/training-load').flush(data);
    fixture.detectChanges();
  }

  function fail(status: number, body: object | null): void {
    http.expectOne('/api/training-load').flush(body, { status, statusText: 'Error' });
    fixture.detectChanges();
  }

  const notConfigured = { error: 'training_load_not_configured', message: 'x' };
  const invalidMessage = 'LTHR_BPM is not a whole number between 100 and 220.';
  const invalid = { error: 'training_load_invalid_config', message: invalidMessage };

  it('R13 - loading', () => {
    expect(element().querySelector('h2')?.textContent).toBe('Training load');
    expect(element().querySelector('[role="status"]')?.textContent).toContain(
      'Loading training load…',
    );
    expect(element().querySelector('app-training-load-chart')).toBeNull();
  });

  it('R13 - not configured hint without retry', () => {
    fail(404, notConfigured);
    expect(text()).toContain('No threshold heart rate set up yet.');
    expect(text()).toContain('Set LTHR_BPM in .env (for example 175), then restart the backend.');
    expect(element().querySelector('button')).toBeNull();
  });

  it('R13 - invalid config message', () => {
    fail(422, invalid);
    expect(element().querySelector('[role="alert"]')?.textContent?.trim()).toBe(
      `Training load settings have a problem: ${invalidMessage}`,
    );
    expect(text()).toContain('then restart the backend.');
    expect(element().querySelector('button')).toBeNull();
  });

  it('R13 - error with retry', () => {
    fail(500, { error: 'internal_error', message: 'Something went wrong.' });
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      "Couldn't load training load.",
    );
    element().querySelector<HTMLButtonElement>('button.retry')?.click();
    fixture.detectChanges();
    expect(text()).toContain('Loading training load…');
    respond(trainingLoad());
    expect(element().querySelector('app-training-load-chart')).not.toBeNull();
  });

  it('R13 - unexpected body shows the error state', () => {
    http.expectOne('/api/training-load').flush({ days: 'nope' });
    fixture.detectChanges();
    expect(text()).toContain("Couldn't load training load.");
  });

  it('R10 - 401 shows no error text', () => {
    fail(401, { error: 'not_connected', message: 'Connect your Strava account.' });
    expect(element().querySelector('[role="alert"]')).toBeNull();
    expect(text()).toContain('Loading training load…');
  });

  it('R13 - empty no heart rate runs', () => {
    respond(trainingLoad({ runsCounted: 0, runsSkippedNoHeartRate: 3 }));
    expect(all('p.empty')).toEqual([
      'No runs with heart rate in the last 6 months',
      '3 runs without heart rate skipped',
    ]);
    expect(element().querySelector('app-training-load-chart')).toBeNull();
    expect(element().querySelector('.latest')).toBeNull();
    expect(element().querySelector('.ramp-callout')).toBeNull();
  });

  it('R13 - empty still importing', () => {
    respond(trainingLoad({ runsCounted: 0, runsPendingHeartRate: 12 }));
    expect(text()).toContain(
      'Heart-rate data is still importing: 12 runs to go. Reload in a while.',
    );
    expect(element().querySelector('app-training-load-chart')).toBeNull();
  });

  it('R11 - shows latest signed values', () => {
    respond(trainingLoad());
    expect(all('.latest-value')).toEqual(['Fitness52.1', 'Fatigue61.3', 'Form-9.2']);
    expect(all('.latest-number')).toEqual(['52.1', '61.3', '-9.2']);
    expect(all('.latest-caption')).toEqual(['on Sat 31 Oct']);
  });

  it('R11 - positive form gets a plus sign', () => {
    respond(
      trainingLoad({ latest: { date: '2026-10-31', fitness: 50, fatigue: 46.8, form: 3.2 } }),
    );
    expect(all('.latest-number')[2]).toBe('+3.2');
  });

  it('R11 - footnote counts and zones', () => {
    respond(trainingLoad());
    expect(all('.footnote p')).toEqual([
      'Zones from LTHR 175 bpm: easy < 153 · subT 153–175 · hard > 175',
      'Based on 96 runs with heart rate · 2 runs without heart rate skipped',
    ]);
  });

  it('R11 - footnote singular and without skipped part', () => {
    respond(trainingLoad({ runsCounted: 1, runsSkippedNoHeartRate: 0 }));
    expect(all('.footnote p')[1]).toBe('Based on 1 run with heart rate');
  });

  it('R11 - pending note', () => {
    respond(trainingLoad({ runsPendingHeartRate: 5 }));
    expect(all('.footnote p')[2]).toBe('5 runs still importing heart rate; numbers will update.');
  });

  it('R12 - this week warning callout', () => {
    respond(
      trainingLoad({ ramp: { ...trainingLoad().ramp, thisWeek: weekRamp({ warning: true }) } }),
    );
    expect(all('.ramp-callout')).toEqual([
      'This week so far: +14% vs last week (load 512.0 vs 449.0)',
    ]);
    expect(element().querySelector('.ramp-callout')?.getAttribute('role')).toBe('status');
  });

  it('R12 - last week warning callout, both in order', () => {
    const base = trainingLoad().ramp;
    respond(
      trainingLoad({
        ramp: {
          thisWeek: { ...base.thisWeek, warning: true },
          lastWeek: { ...base.lastWeek, warning: true },
        },
      }),
    );
    expect(all('.ramp-callout')).toEqual([
      'This week so far: +14% vs last week (load 512.0 vs 449.0)',
      'Last week: +12% vs the week before (load 449.0 vs 401.0)',
    ]);
  });

  it('R12 - no callout without warning', () => {
    respond(trainingLoad());
    expect(element().querySelector('.ramp-callout')).toBeNull();
  });

  it('R13 - normal order', () => {
    const base = trainingLoad().ramp;
    respond(trainingLoad({ ramp: { ...base, thisWeek: { ...base.thisWeek, warning: true } } }));
    const order = Array.from(element().querySelector('section')?.children ?? []).map(
      (child) => child.tagName.toLowerCase() + (child.className ? `.${child.className}` : ''),
    );
    expect(order).toEqual([
      'h2',
      'div.latest',
      'p.latest-caption',
      'app-ramp-warning',
      'app-training-load-chart',
      'div.footnote',
    ]);
  });

  it('R12 - no advice words in any state', () => {
    const base = trainingLoad().ramp;
    const states: (() => void)[] = [
      () => undefined,
      () => fail(404, notConfigured),
      () => fail(422, invalid),
      () => fail(500, {}),
      () => respond(trainingLoad({ runsCounted: 0, runsSkippedNoHeartRate: 3 })),
      () => respond(trainingLoad({ runsCounted: 0, runsPendingHeartRate: 4 })),
      () => respond(trainingLoad({ runsPendingHeartRate: 4 })),
      () =>
        respond(
          trainingLoad({
            ramp: {
              thisWeek: { ...base.thisWeek, warning: true },
              lastWeek: { ...base.lastWeek, warning: true },
            },
          }),
        ),
    ];
    states.forEach((enter, index) => {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [provideHttpClient(), provideHttpClientTesting()],
      });
      stubCharts();
      fixture = TestBed.createComponent(TrainingLoadSection);
      http = TestBed.inject(HttpTestingController);
      fixture.detectChanges();
      enter();
      expect(ADVICE_WORDS.test(text()), `state ${index}: ${text()}`).toBe(false);
    });
  });
});
