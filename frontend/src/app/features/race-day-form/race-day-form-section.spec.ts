import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RaceDayFormResponse } from '../../core/api/race-day-form.models';
import { SyncStore } from '../../core/sync/sync.store';
import { stubCharts } from '../../testing/dashboard-fixtures';
import { notEnoughRunsForm, raceDayForm } from '../../testing/race-day-form-fixtures';
import { SyncStoreStub } from '../../testing/sync-store-stub';
import { RaceDayFormSection } from './race-day-form-section';

describe('RaceDayFormSection (011)', () => {
  let fixture: ComponentFixture<RaceDayFormSection>;
  let http: HttpTestingController;
  let sync: SyncStoreStub;

  beforeEach(() => {
    sync = new SyncStoreStub();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SyncStore, useValue: sync },
      ],
    });
    stubCharts();
    fixture = TestBed.createComponent(RaceDayFormSection);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';

  function respond(data: RaceDayFormResponse): void {
    http.expectOne('/api/race-day-form').flush(data);
    fixture.detectChanges();
  }

  function fail(status: number, body: object | null): void {
    http.expectOne('/api/race-day-form').flush(body, { status, statusText: 'Error' });
    fixture.detectChanges();
  }

  const ADVICE_WORDS = /\b(should|must|recommend|reduce|increase|taper|rest)\b/i;

  it('R16 - loading', () => {
    expect(element().querySelector('h2')?.textContent).toBe('Race-day form');
    expect(element().querySelector('[role="status"]')?.textContent).toContain(
      'Loading race-day projection…',
    );
    expect(element().querySelector('app-race-day-form-chart')).toBeNull();
  });

  it('R16 - not configured names missing setting', () => {
    fail(404, { error: 'race_day_form_missing_lthr', message: 'x' });
    expect(text()).toContain(
      'Race-day form needs a threshold heart rate. Set LTHR_BPM in .env, then restart the backend.',
    );
    expect(element().querySelector('button')).toBeNull();
    expect(element().querySelector('app-race-day-form-chart')).toBeNull();
  });

  it('R16 - invalid config', () => {
    fail(422, {
      error: 'race_day_form_invalid_config',
      message: 'The training plan file has a problem.',
    });
    expect(element().querySelector('[role="alert"]')?.textContent?.trim()).toBe(
      "Race-day form can't be shown: The training plan file has a problem.",
    );
    expect(element().querySelector('button')).toBeNull();
  });

  it('R16 - error with retry', () => {
    fail(503, { error: 'race_day_form_plan_unavailable', message: 'x' });
    expect(text()).toContain("Couldn't load race-day form.");
    element().querySelector<HTMLButtonElement>('button.retry')?.click();
    fixture.detectChanges();
    expect(text()).toContain('Loading race-day projection…');
    respond(raceDayForm());
    expect(element().querySelector('app-race-day-form-chart')).not.toBeNull();
  });

  it('R12 - 401 shows no error', () => {
    fail(401, { error: 'not_connected', message: 'Connect your Strava account.' });
    expect(element().querySelector('[role="alert"]')).toBeNull();
  });

  it('R13 - tile shows race-day label and caption', () => {
    respond(raceDayForm());
    expect(element().querySelector('.race-tile')?.textContent?.trim()).toBe(
      'Form +18 · Fitness 61 (-4 from peak)',
    );
    expect(element().querySelector('.race-caption')?.textContent?.trim()).toBe(
      'Race day · Sun 6 Dec · projected',
    );
  });

  it('R13 - no tile without race day', () => {
    respond(raceDayForm({ raceDay: null }));
    expect(element().querySelector('.race-tile')).toBeNull();
  });

  it('R15 - subT estimate note', () => {
    respond(raceDayForm({ subtEstimated: true }));
    expect(text()).toContain(
      'SubT load is an estimate (2× easy): fewer than 3 SubT runs with heart rate in the last 6 months.',
    );
  });

  it('R15 - not enough runs message', () => {
    respond(notEnoughRunsForm());
    expect(element().querySelector('[role="status"]')?.textContent?.trim()).toBe(
      'Not enough runs with heart rate yet to project the plan.',
    );
    expect(element().querySelector('app-race-day-form-chart')).not.toBeNull();
    expect(text()).toContain('Ready band: form at 5–25% of fitness. A rough guide, not a rule.');
    expect(element().querySelector('.race-tile')).toBeNull();
  });

  it('R16 - normal order', () => {
    respond(raceDayForm({ subtEstimated: true }));
    const order = Array.from(element().querySelector('section')?.children ?? []).map(
      (child) => child.tagName.toLowerCase() + (child.className ? `.${child.className}` : ''),
    );
    expect(order).toEqual([
      'h2',
      'p.race-tile',
      'p.race-caption',
      'p.note',
      'app-race-day-form-chart',
      'p.note band-caption',
    ]);
  });

  it('R12 - quiet reload after sync', () => {
    respond(raceDayForm());
    sync.finish();
    fixture.detectChanges();
    expect(element().querySelector('.race-tile')).not.toBeNull();
    http.expectOne('/api/race-day-form').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(text()).not.toContain("Couldn't load");
    expect(element().querySelector('.race-tile')).not.toBeNull();
  });

  it('R16 - not configured texts for each missing setting', () => {
    const texts = {
      race_day_form_missing_plan:
        'Race-day form needs a training plan. Set TRAINING_PLAN_CSV in .env.',
      race_day_form_missing_lthr:
        'Race-day form needs a threshold heart rate. Set LTHR_BPM in .env, then restart the backend.',
      race_day_form_missing_plan_and_lthr:
        'Race-day form needs a training plan and a threshold heart rate. Set TRAINING_PLAN_CSV and LTHR_BPM in .env, then restart the backend.',
    };
    http.expectOne('/api/race-day-form').flush(null);
    for (const [error, expected] of Object.entries(texts)) {
      fixture = TestBed.createComponent(RaceDayFormSection);
      fixture.detectChanges();
      fail(404, { error, message: 'x' });
      expect(element().querySelector('p.empty')?.textContent?.trim()).toBe(expected);
    }
  });

  it('R16 - no advice words in any state', () => {
    const texts: string[] = [text()];
    http.expectOne('/api/race-day-form').flush(null);
    const states: [number, object | RaceDayFormResponse][] = [
      [404, { error: 'race_day_form_missing_plan', message: 'x' }],
      [404, { error: 'race_day_form_missing_lthr', message: 'x' }],
      [404, { error: 'race_day_form_missing_plan_and_lthr', message: 'x' }],
      [
        422,
        { error: 'race_day_form_invalid_config', message: 'The training plan file has a problem.' },
      ],
      [
        422,
        {
          error: 'race_day_form_invalid_config',
          message: 'LTHR_BPM is not a whole number between 100 and 220.',
        },
      ],
      [503, { error: 'race_day_form_plan_unavailable', message: 'x' }],
      [200, raceDayForm({ subtEstimated: true })],
      [200, notEnoughRunsForm()],
    ];
    for (const [status, body] of states) {
      fixture = TestBed.createComponent(RaceDayFormSection);
      fixture.detectChanges();
      if (status === 200) {
        respond(body as RaceDayFormResponse);
      } else {
        fail(status, body);
      }
      texts.push(text());
    }
    texts.forEach((value) => expect(value).not.toMatch(ADVICE_WORDS));
  });
});
