import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MarathonPaceResponse } from '../../../core/api/marathon-pace.models';
import { SyncStore } from '../../../core/sync/sync.store';
import { stubCharts } from '../../../testing/dashboard-fixtures';
import { marathonPace, mpSummary } from '../../../testing/marathon-pace-fixtures';
import { SyncStoreStub } from '../../../testing/sync-store-stub';
import { MarathonPaceSection } from './marathon-pace-section';

describe('MarathonPaceSection', () => {
  let fixture: ComponentFixture<MarathonPaceSection>;
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
    fixture = TestBed.createComponent(MarathonPaceSection);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';
  const all = (selector: string) =>
    Array.from(element().querySelectorAll(selector)).map((node) =>
      (node.textContent ?? '').replace(/\s+/g, ' ').trim(),
    );
  const hint = 'Set MARATHON_GOAL_PACE in .env (for example 4:15), then restart the backend.';

  function respond(data: MarathonPaceResponse = marathonPace()): void {
    http.expectOne('/api/marathon-pace').flush(data);
    fixture.detectChanges();
  }

  function fail(status: number, body: object | null): void {
    http.expectOne('/api/marathon-pace').flush(body, { status, statusText: 'Error' });
    fixture.detectChanges();
  }

  it('R7 - loading state', () => {
    expect(element().querySelector('h2')?.textContent).toBe('Marathon pace');
    expect(element().querySelector('[role="status"]')?.textContent).toContain(
      'Loading marathon pace…',
    );
    expect(element().querySelector('app-chart')).toBeNull();
    respond();
  });

  it('R7 - not configured', () => {
    fail(404, { error: 'marathon_pace_not_configured', message: 'x' });
    expect(text()).toContain('Marathon pace not configured.');
    expect(text()).toContain(hint);
    expect(element().querySelector('button')).toBeNull();
    expect(element().querySelector('.summary')).toBeNull();
    expect(element().querySelector('app-chart')).toBeNull();
  });

  it('R7 - invalid config', () => {
    const message = 'MARATHON_GOAL_PACE is not a pace between 2:30 and 8:00 per km (m:ss).';
    fail(422, { error: 'marathon_pace_invalid_config', message });
    expect(element().querySelector('[role="alert"]')?.textContent?.trim()).toBe(
      `Marathon pace settings have a problem: ${message}`,
    );
    expect(text()).toContain(hint);
    expect(element().querySelector('button')).toBeNull();
  });

  it('R7 - error with retry', () => {
    fail(500, { error: 'internal_error', message: 'Something went wrong.' });
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      "Couldn't load marathon pace.",
    );
    element().querySelector<HTMLButtonElement>('button.retry')?.click();
    fixture.detectChanges();
    expect(text()).toContain('Loading marathon pace…');
    respond();
    expect(element().querySelector('app-chart')).not.toBeNull();
  });

  it('R7 - unexpected body shows error', () => {
    http.expectOne('/api/marathon-pace').flush({ weeks: 'nope' });
    fixture.detectChanges();
    expect(text()).toContain("Couldn't load marathon pace.");
  });

  it('R7 - 401 shows no error text', () => {
    fail(401, { error: 'not_connected', message: 'Connect your Strava account.' });
    expect(element().querySelector('[role="alert"]')).toBeNull();
  });

  it('R7 - empty state', () => {
    respond(marathonPace({ summary: mpSummary({ totalMpKm: 0, thisWeekMpKm: 0 }) }));
    expect(all('.subtitle')).toEqual([
      'Goal 4:15 /km · laps from 4:05 to 4:25 /km, 200 m or longer, outdoor runs',
    ]);
    expect(text()).toContain('No marathon-pace km in the build yet.');
    expect(element().querySelector('.summary')).toBeNull();
    expect(element().querySelector('app-chart')).toBeNull();
  });

  it('R7 - normal state', () => {
    respond();
    expect(element().querySelector('.subtitle')).not.toBeNull();
    expect(element().querySelector('.summary')).not.toBeNull();
    expect(element().querySelector('app-chart .chart-stub')?.getAttribute('aria-label')).toBe(
      'Marathon-pace km per week of the build',
    );
  });

  it('R6 - shows subtitle and summary', () => {
    respond();
    expect(all('.subtitle')).toEqual([
      'Goal 4:15 /km · laps from 4:05 to 4:25 /km, 200 m or longer, outdoor runs',
    ]);
    expect(all('.summary-label')).toEqual(['Total at MP', 'This week at MP', 'Share of this week']);
    expect(all('.summary-number')).toEqual(['41.0 km', '6.4 km', '25%']);
    expect(all('.summary-caption')).toEqual(['of 26.0 km']);
  });

  it('R6 - share shows dash with no runs this week', () => {
    respond(
      marathonPace({
        summary: mpSummary({ thisWeekMpKm: 0, thisWeekTotalKm: 0, thisWeekSharePercent: null }),
      }),
    );
    expect(all('.summary-number')[2]).toBe('–');
    expect(all('.summary-caption')).toEqual(['No runs this week']);
  });

  it('R9 - pending laps footnote', () => {
    respond(marathonPace({ runsPendingLaps: 2 }));
    expect(all('.footnote')).toEqual(['2 runs still importing laps; numbers will update.']);
  });

  it('R9 - no footnote when nothing is pending', () => {
    respond();
    expect(element().querySelector('.footnote')).toBeNull();
  });

  it('R10 - reloads quietly after sync', () => {
    respond();
    sync.finish();
    fixture.detectChanges();
    expect(text()).not.toContain('Loading marathon pace…');
    http
      .expectOne('/api/marathon-pace')
      .flush(marathonPace({ summary: mpSummary({ thisWeekMpKm: 8.1 }) }));
    fixture.detectChanges();
    expect(all('.summary-number')[1]).toBe('8.1 km');

    sync.finish();
    fixture.detectChanges();
    http.expectOne('/api/marathon-pace').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(all('.summary-number')[1]).toBe('8.1 km');
    expect(element().querySelector('[role="alert"]')).toBeNull();
  });
});
