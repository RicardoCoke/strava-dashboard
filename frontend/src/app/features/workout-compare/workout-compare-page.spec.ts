import { Component } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { WorkoutCompareResponse } from '../../core/api/workout-compare.models';
import { personInterceptor } from '../../core/interceptors/person.interceptor';
import { PersonStore } from '../../core/person/person.store';
import { SyncStore } from '../../core/sync/sync.store';
import { ChartComponent } from '../../shared/chart/chart';
import { stubCharts } from '../../testing/dashboard-fixtures';
import { SyncStoreStub } from '../../testing/sync-store-stub';
import { COMPARE_RUNS, PIECES, comparison } from '../../testing/workout-compare-fixtures';
import { WeeklySummaryBell } from '../weekly-summary/weekly-summary-bell';
import { WorkoutComparePage } from './workout-compare-page';
import { WorkoutCompareShell } from './workout-compare-shell';
import { DRIFT_FOOTNOTE, PIECES_CAVEAT } from './workout-compare.view';

@Component({ selector: 'app-weekly-summary-bell', template: '' })
class BellStub {}

const RUNS = '/api/workout-compare/runs';
const COMPARE = '/api/workout-compare';

describe('WorkoutComparePage', () => {
  let http: HttpTestingController;
  let sync: SyncStoreStub;

  beforeEach(() => {
    localStorage.clear();
    sync = new SyncStoreStub();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([personInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    TestBed.overrideComponent(WorkoutComparePage, {
      remove: { imports: [WeeklySummaryBell] },
      add: { imports: [BellStub] },
    });
    TestBed.overrideComponent(WorkoutComparePage, {
      set: { providers: [{ provide: SyncStore, useValue: sync }] },
    });
    stubCharts();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  describe('page', () => {
    let fixture: ComponentFixture<WorkoutComparePage>;

    beforeEach(() => {
      fixture = TestBed.createComponent(WorkoutComparePage);
      fixture.detectChanges();
    });

    const el = () => fixture.nativeElement as HTMLElement;
    const text = (root: Element | null = el()) =>
      root?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
    const filterButtons = () =>
      Array.from(el().querySelectorAll<HTMLButtonElement>('[aria-label="Run type"] button'));
    const axisButtons = () =>
      Array.from(el().querySelectorAll<HTMLButtonElement>('[aria-label="Chart x-axis"] button'));
    const boxes = () => Array.from(el().querySelectorAll<HTMLInputElement>('.row input'));
    const compareArea = () => el().querySelector('.compare');
    const texts = (root: Element | null, selector: string) =>
      Array.from(root?.querySelectorAll(selector) ?? []).map((node) => text(node));

    function runsRequest(type = 'all'): TestRequest {
      const request = http.expectOne((r) => r.url === RUNS);
      expect(request.request.params.get('type')).toBe(type);
      return request;
    }

    function respondRuns(type = 'all', body: object = { runs: COMPARE_RUNS }, status = 200): void {
      runsRequest(type).flush(body, { status, statusText: 'x' });
      fixture.detectChanges();
    }

    function check(index: number): void {
      boxes()[index].click();
      fixture.detectChanges();
    }

    function compareRequest(): TestRequest {
      return http.expectOne((r) => r.url === COMPARE);
    }

    function loadComparison(body: WorkoutCompareResponse = comparison()): void {
      respondRuns();
      check(0);
      check(1);
      compareRequest().flush(body);
      fixture.detectChanges();
    }

    it('R2 - getRuns and compare call the endpoints with params', () => {
      const list = runsRequest();
      expect(list.request.method).toBe('GET');
      expect(list.request.urlWithParams).toBe('/api/workout-compare/runs?type=all');
      list.flush({ runs: COMPARE_RUNS });
      fixture.detectChanges();
      check(0);
      check(1);
      const request = compareRequest();
      expect(request.request.method).toBe('GET');
      expect(request.request.urlWithParams).toBe('/api/workout-compare?a=2&b=1');
      request.flush(comparison());
    });

    it('R1 - header, filter, list and compare area in order', () => {
      respondRuns();
      const header = el().querySelector('header');
      expect(
        Array.from(
          header?.querySelectorAll(
            'app-nav-menu, h1, app-person-switch, app-weekly-summary-bell',
          ) ?? [],
        ).map((n) => n.tagName.toLowerCase()),
      ).toEqual(['app-nav-menu', 'h1', 'app-person-switch', 'app-weekly-summary-bell']);
      expect(text(el().querySelector('h1'))).toBe('Compare');
      const order = Array.from(
        el().querySelectorAll('header, [aria-label="Run type"], .run-list, .compare'),
      );
      expect(order.map((n) => n.getAttribute('aria-label') ?? n.tagName.toLowerCase())).toEqual([
        'header',
        'Run type',
        'Runs',
        'Comparison',
      ]);
      expect(sync.started).toBe(true);
    });

    it('R3 - filter defaults to all and clears selection on change', () => {
      expect(filterButtons().map((b) => [text(b), b.getAttribute('aria-pressed')])).toEqual([
        ['All', 'true'],
        ['Workouts', 'false'],
        ['Long', 'false'],
        ['Easy', 'false'],
      ]);
      respondRuns();
      check(0);
      check(1);
      const pending = compareRequest();

      filterButtons()[1].click();
      fixture.detectChanges();
      expect(pending.cancelled).toBe(true);
      expect(text(el().querySelector('.run-list [role="status"]'))).toBe('Loading runs…');
      expect(text(compareArea())).toBe('Pick two runs to compare.');
      const workouts = runsRequest('workouts');

      filterButtons()[2].click();
      fixture.detectChanges();
      expect(workouts.cancelled).toBe(true);
      respondRuns('long', { runs: [COMPARE_RUNS[1]] });
      expect(filterButtons()[2].getAttribute('aria-pressed')).toBe('true');
      expect(boxes().length).toBe(1);
      expect(boxes()[0].checked).toBe(false);
    });

    it('R3 - row text, name and treadmill tag', () => {
      respondRuns();
      const rows = Array.from(el().querySelectorAll('.row'));
      expect(text(rows[0].querySelector('.row-body > span'))).toBe(
        'Tue 24 Nov · 8x3min · 10.1 km · 5:06 /km',
      );
      expect(text(rows[0].querySelector('.row-name'))).toBe('8x3min hills');
      expect(rows[0].querySelector('.tag')).toBeNull();
      expect(text(rows[2].querySelector('.tag'))).toBe('Treadmill');
      expect(rows[2].querySelector('.row-name')).toBeNull();
    });

    it('R3 - third pick replaces the first and two picks request a comparison', () => {
      respondRuns();
      check(0);
      expect(text(compareArea())).toBe('Pick one more run to compare.');
      http.expectNone((r) => r.url === COMPARE);

      check(1);
      const first = compareRequest();
      expect(first.request.urlWithParams).toBe('/api/workout-compare?a=2&b=1');

      check(2);
      expect(first.cancelled).toBe(true);
      expect(boxes().map((box) => box.checked)).toEqual([false, true, true]);
      expect(el().querySelector('.row.selected')).toBe(el().querySelectorAll('.row')[1]);
      compareRequest().flush(comparison());
    });

    it('R3 - list loading, error with retry, empty and normal', () => {
      expect(text(el().querySelector('.run-list'))).toBe('Loading runs…');
      respondRuns('all', {}, 500);
      expect(texts(el(), '.run-list [role="alert"] p, .run-list .retry')).toEqual([
        "Couldn't load runs.",
        'Retry',
      ]);

      (el().querySelector('.run-list .retry') as HTMLButtonElement).click();
      fixture.detectChanges();
      respondRuns('all', { unexpected: true });
      expect(el().querySelector('.run-list [role="alert"]')).not.toBeNull();

      (el().querySelector('.run-list .retry') as HTMLButtonElement).click();
      fixture.detectChanges();
      respondRuns('all', { runs: [] });
      expect(text(el().querySelector('.run-list'))).toBe('No runs yet.');

      filterButtons()[3].click();
      fixture.detectChanges();
      respondRuns('easy', { runs: [] });
      expect(text(el().querySelector('.run-list'))).toBe('No easy runs yet.');
    });

    it('R1 - reloads list quietly when dataVersion changes', () => {
      respondRuns();
      check(0);

      sync.finish();
      fixture.detectChanges();
      expect(boxes().length).toBe(3);
      runsRequest().flush({ runs: [...COMPARE_RUNS, { ...COMPARE_RUNS[0], id: 9 }] });
      fixture.detectChanges();
      expect(boxes().length).toBe(4);
      expect(boxes()[0].checked).toBe(true);

      sync.finish();
      fixture.detectChanges();
      runsRequest().flush({}, { status: 500, statusText: 'x' });
      fixture.detectChanges();
      expect(boxes().length).toBe(4);
      expect(text(compareArea())).toBe('Pick one more run to compare.');
    });

    it('R4 - comparison loading, error with retry and normal', () => {
      respondRuns();
      check(0);
      check(1);
      expect(text(compareArea())).toBe('Loading comparison…');
      compareRequest().flush({}, { status: 404, statusText: 'x' });
      fixture.detectChanges();
      expect(texts(compareArea(), '[role="alert"] p, .retry')).toEqual([
        "Couldn't load the comparison.",
        'Retry',
      ]);

      (compareArea()?.querySelector('.retry') as HTMLButtonElement).click();
      fixture.detectChanges();
      const retry = compareRequest();
      expect(retry.request.urlWithParams).toBe('/api/workout-compare?a=2&b=1');
      retry.flush(
        comparison({
          resolution: 'splits',
          coarse: { reason: 'treadmill', runId: 2, retryAfter: null },
        }),
      );
      fixture.detectChanges();
      expect(text(compareArea()?.querySelector('.coarse') ?? null)).toBe(
        'Showing per-km splits, which are coarser than second-by-second data: the Later · 24 Nov run is a treadmill run.',
      );
      expect(el().querySelector('.chart-stub')).not.toBeNull();
    });

    it('R7 - x axis switches between distance and time without a request', () => {
      loadComparison();
      expect(axisButtons().map((b) => [text(b), b.getAttribute('aria-pressed')])).toEqual([
        ['Distance', 'true'],
        ['Time', 'false'],
      ]);
      const chart = () => el().querySelector('.chart-stub')?.getAttribute('aria-label');
      expect(chart()).toBe('Pace and heart rate of two runs by distance');

      axisButtons()[1].click();
      fixture.detectChanges();
      expect(chart()).toBe('Pace and heart rate of two runs by time');
      expect(axisButtons()[1].getAttribute('aria-pressed')).toBe('true');
      http.expectNone(() => true);
    });

    it('R7 - a legend click survives an axis switch', () => {
      loadComparison();
      const chart = () =>
        fixture.debugElement.query(By.directive(ChartComponent))
          .componentInstance as ChartComponent;
      const selected = () =>
        (chart().options() as { legend: { selected: object } }).legend.selected;

      chart().legendChange.emit({ 'Earlier · 10 Nov': false, 'Later · 24 Nov': true });
      fixture.detectChanges();
      axisButtons()[1].click();
      fixture.detectChanges();

      expect(selected()).toEqual({ 'Earlier · 10 Nov': false, 'Later · 24 Nov': true });
    });

    it('R7 - missing points notes and caption', () => {
      const data = comparison();
      loadComparison(comparison({ runs: [{ ...data.runs[0], points: [] }, data.runs[1]] }));
      const card = () => el().querySelector('.chart-card');
      expect(texts(card(), '.note')).toEqual([
        'No per-km data for the Earlier · 10 Nov run.',
        'Solid line: pace · dashed line: heart rate',
      ]);
      expect(el().querySelector('.chart-stub')).not.toBeNull();

      filterButtons()[0].click();
      fixture.detectChanges();
      respondRuns();
      check(0);
      check(1);
      compareRequest().flush(
        comparison({
          runs: [
            { ...data.runs[0], points: [] },
            { ...data.runs[1], points: [] },
          ],
        }),
      );
      fixture.detectChanges();
      expect(text(card())).toBe('No pace data for these runs.');
      expect(el().querySelector('.chart-stub')).toBeNull();
      expect(el().querySelector('.stats-card')).not.toBeNull();
    });

    it('R8 - stats table formats and drift footnote', () => {
      loadComparison();
      const card = el().querySelector('.stats-card');
      const headers = Array.from(card?.querySelectorAll('thead th') ?? []).map((th) => text(th));
      expect(headers).toEqual([
        'Stat',
        'Earlier · 10 Nov5x6min threshold',
        'Later · 24 Nov8x3min hills',
      ]);
      expect(card?.querySelectorAll('tbody tr').length).toBe(7);
      expect(texts(card, 'tbody tr:last-child > *')).toEqual(['HR drift', '—', '+3.8%']);
      expect(text(card)).toContain(DRIFT_FOOTNOTE);
    });

    it('R12 - pieces caveat shown only in pieces mode', () => {
      loadComparison();
      const card = () => el().querySelector('.reps-card');
      expect(card()?.querySelectorAll('tbody tr').length).toBe(4);
      expect(texts(card(), 'tbody tr:first-child > *')).toEqual([
        'All',
        '4:12 /km · 166 bpm',
        '4:04 /km · 163 bpm',
        '8 s/km faster · −3.0 bpm',
      ]);
      expect(text(card())).toContain('5x6min as 3-min pieces');
      expect(text(card())).toContain(PIECES_CAVEAT);

      const reselect = (data: WorkoutCompareResponse) => {
        check(1);
        check(1);
        compareRequest().flush(data);
        fixture.detectChanges();
      };
      reselect(
        comparison({ reps: { ...PIECES, mode: 'reps', pieceMinutes: null, piecesRunId: null } }),
      );
      expect(card()).not.toBeNull();
      expect(text(card())).not.toContain(PIECES_CAVEAT);

      reselect(
        comparison({
          reps: {
            ...PIECES,
            mode: 'unavailable',
            unavailableReason: 'noDetailedData',
            average: null,
            pairs: [],
          },
        }),
      );
      expect(texts(card(), 'p, li')).toEqual([
        "Reps can't be compared without detailed data for both runs.",
        'Whole-session averages:',
        'Earlier · 10 Nov: 5:11 /km · 149 bpm',
        'Later · 24 Nov: 5:06 /km · 147 bpm',
      ]);
      expect(card()?.querySelector('table')).toBeNull();

      reselect(comparison({ reps: null }));
      expect(card()).toBeNull();
    });
  });

  it('R1 - flip recreates compare page with new person', () => {
    const fixture = TestBed.createComponent(WorkoutCompareShell);
    fixture.detectChanges();
    http.expectOne((r) => r.url === RUNS).flush({ runs: COMPARE_RUNS });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    (el.querySelectorAll('.row input')[0] as HTMLInputElement).click();
    (el.querySelectorAll('.row input')[1] as HTMLInputElement).click();
    fixture.detectChanges();
    const pending = http.expectOne((r) => r.url === COMPARE);

    TestBed.inject(PersonStore).select('marina');
    fixture.detectChanges();

    expect(pending.cancelled).toBe(true);
    expect(el.querySelectorAll('.row').length).toBe(0);
    expect(el.querySelector('.run-list [role="status"]')).not.toBeNull();
    const request = http.expectOne((r) => r.url === RUNS);
    expect(request.request.params.get('type')).toBe('all');
    expect(request.request.headers.get('X-Person')).toBe('marina');
    request.flush({ runs: [] });
  });
});
