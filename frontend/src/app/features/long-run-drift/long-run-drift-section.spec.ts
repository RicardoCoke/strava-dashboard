import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LongRunDriftResponse } from '../../core/api/long-run-drift.models';
import { SyncStore } from '../../core/sync/sync.store';
import { stubCharts } from '../../testing/dashboard-fixtures';
import { emptyLongRunDrift, longRunDrift } from '../../testing/long-run-drift-fixtures';
import { SyncStoreStub } from '../../testing/sync-store-stub';
import { LongRunDriftSection } from './long-run-drift-section';

describe('LongRunDriftSection', () => {
  let fixture: ComponentFixture<LongRunDriftSection>;
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
    fixture = TestBed.createComponent(LongRunDriftSection);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';

  function respond(data: LongRunDriftResponse | object): void {
    http.expectOne('/api/long-run-drift').flush(data);
    fixture.detectChanges();
  }

  it('R6 - shows loading state', () => {
    expect(element().querySelector('h2')?.textContent).toBe('Long-run HR drift');
    expect(element().querySelector('[role="status"]')?.textContent).toContain(
      'Loading heart-rate drift…',
    );
  });

  it('R6 - shows error with retry', () => {
    http
      .expectOne('/api/long-run-drift')
      .flush({ error: 'internal_error', message: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      "Couldn't load heart-rate drift.",
    );
    element().querySelector<HTMLButtonElement>('button.retry')?.click();
    fixture.detectChanges();
    expect(text()).toContain('Loading heart-rate drift…');
    respond(longRunDrift());
    expect(element().querySelector('app-long-run-drift-chart')).not.toBeNull();
  });

  it('R6 - unexpected body shows error', () => {
    respond({ points: 'nope' });
    expect(element().querySelector('[role="alert"]')).not.toBeNull();
  });

  it('R6 - shows no long runs empty state', () => {
    respond(emptyLongRunDrift());
    expect(text()).toContain('No long runs in the last 5 months');
    expect(element().querySelector('app-long-run-drift-chart')).toBeNull();
    expect(element().querySelector('.drift-note')).toBeNull();
  });

  it('R6 - shows no drift yet with pending and excluded lines', () => {
    respond({
      ...emptyLongRunDrift(),
      pendingCount: 2,
      excluded: { total: 1, treadmill: 1, tooShort: 0, missingData: 0 },
    });
    expect(text()).toContain('No long-run drift yet');
    expect(text()).toContain('2 long runs still syncing');
    expect(text()).toContain('1 long run left out: 1 treadmill.');
    expect(element().querySelector('app-long-run-drift-chart')).toBeNull();
    expect(element().querySelector('.drift-header')).toBeNull();
  });

  it('R6 - header shows latest and average with zone class', () => {
    respond(
      longRunDrift({
        lastFourAverage: { driftPct: 6.1, zone: 'watch', runCount: 4 },
        pendingCount: 1,
        excluded: { total: 2, treadmill: 0, tooShort: 1, missingData: 1 },
      }),
    );
    const stats = Array.from(element().querySelectorAll('.drift-stat'));
    expect(stats.map((stat) => stat.textContent?.trim())).toEqual([
      'Latest: +3.8% · 25 Oct',
      'Avg last 4: +6.1%',
    ]);
    expect(stats[0].classList).toContain('zone-good');
    expect(stats[1].classList).toContain('zone-watch');
    expect(element().querySelector('app-long-run-drift-chart')).not.toBeNull();
    expect(text()).toContain('1 long run still syncing');
    expect(text()).toContain(
      '2 long runs left out: 1 under 60 min usable, 1 missing HR or speed data.',
    );
  });

  it('R6 - shows zone legend', () => {
    respond(longRunDrift());
    expect(text()).toContain('Good < 5% · Watch 5–10% · Poor > 10%');
    expect(text()).toContain('Lower is better. Plain pace, not adjusted for hills or heat.');
  });

  it('R6 - refreshes quietly after sync', () => {
    respond(longRunDrift());
    sync.finish();
    fixture.detectChanges();
    expect(element().querySelector('[role="status"]')).toBeNull();
    expect(element().querySelector('app-long-run-drift-chart')).not.toBeNull();

    http
      .expectOne('/api/long-run-drift')
      .flush({ error: 'internal_error', message: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(element().querySelector('app-long-run-drift-chart')).not.toBeNull();

    sync.finish();
    fixture.detectChanges();
    respond(longRunDrift({ latest: { date: '2026-10-31', driftPct: 2.1, zone: 'good' } }));
    expect(text()).toContain('Latest: +2.1% · 31 Oct');
  });
});
