import { Component } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RingsHistoryResponse } from '../../core/api/rings.models';
import { personInterceptor } from '../../core/interceptors/person.interceptor';
import { PersonStore } from '../../core/person/person.store';
import { SyncStore } from '../../core/sync/sync.store';
import { stubCharts } from '../../testing/dashboard-fixtures';
import { HISTORY_DAYS, historyDay } from '../../testing/rings-history-fixtures';
import { SyncStoreStub } from '../../testing/sync-store-stub';
import { WeeklySummaryBell } from '../weekly-summary/weekly-summary-bell';
import { RecoveryPage } from './recovery-page';
import { RecoveryShell } from './recovery-shell';

@Component({ selector: 'app-weekly-summary-bell', template: '' })
class BellStub {}

const HISTORY = '/api/rings/history';
const body: RingsHistoryResponse = { days: HISTORY_DAYS };

describe('RecoveryPage', () => {
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
    TestBed.overrideComponent(RecoveryPage, {
      remove: { imports: [WeeklySummaryBell] },
      add: { imports: [BellStub] },
    });
    TestBed.overrideComponent(RecoveryPage, {
      set: { providers: [{ provide: SyncStore, useValue: sync }] },
    });
    stubCharts();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    // A test here selects marina; don't leak that into spec files that run later in this worker.
    localStorage.clear();
  });

  describe('page', () => {
    let fixture: ComponentFixture<RecoveryPage>;

    beforeEach(() => {
      fixture = TestBed.createComponent(RecoveryPage);
      fixture.detectChanges();
    });

    const el = () => fixture.nativeElement as HTMLElement;
    const text = () => el().textContent?.replace(/\s+/g, ' ') ?? '';
    const buttons = () => Array.from(el().querySelectorAll<HTMLButtonElement>('.range button'));

    function respond(days: number, response: object = body, status = 200): void {
      const request = http.expectOne((r) => r.url === HISTORY);
      expect(request.request.params.get('days')).toBe(String(days));
      request.flush(response, { status, statusText: 'x' });
      fixture.detectChanges();
    }

    it('R5 - getHistory calls GET /api/rings/history with days', () => {
      const request = http.expectOne((r) => r.url === HISTORY);
      expect(request.request.method).toBe('GET');
      expect(request.request.urlWithParams).toBe('/api/rings/history?days=30');
      request.flush(body);
    });

    it('R1 - header has menu first and keeps switch and bell', () => {
      const header = el().querySelector('header.page-header');
      expect(header?.querySelector('.header-start')?.firstElementChild?.tagName).toBe(
        'APP-NAV-MENU',
      );
      expect(header?.querySelector('h1')?.textContent).toBe('Recovery');
      expect(header?.querySelector('.header-actions app-person-switch')).not.toBeNull();
      expect(header?.querySelector('.header-actions app-weekly-summary-bell')).not.toBeNull();
      respond(30);
    });

    it('R6 - three cards in order under header and picker', () => {
      respond(30);
      const titles = Array.from(el().querySelectorAll('.card h2')).map((h) => h.textContent);
      expect(titles).toEqual(['Sleep', 'Recovery', 'Strain']);
      const labels = Array.from(el().querySelectorAll('.chart-stub')).map((c) =>
        c.getAttribute('aria-label'),
      );
      expect(labels).toEqual([
        'Sleep performance per day, last 3 days',
        'Recovery score per day, last 3 days',
        'Strain per day, last 3 days',
      ]);
    });

    it('R8 - picker defaults to 30 days', () => {
      expect(el().querySelector('[role="group"]')?.getAttribute('aria-label')).toBe('Range');
      expect(buttons().map((b) => [b.textContent?.trim(), b.getAttribute('aria-pressed')])).toEqual(
        [
          ['7 days', 'false'],
          ['30 days', 'true'],
          ['90 days', 'false'],
        ],
      );
      respond(30);
    });

    it('R8 - range change reloads and latest wins', () => {
      buttons()[0].click();
      fixture.detectChanges();
      buttons()[2].click();
      fixture.detectChanges();
      const requests = http.match((r) => r.url === HISTORY);
      expect(requests.map((r) => r.request.params.get('days'))).toEqual(['30', '7', '90']);
      expect(requests.slice(0, 2).every((r) => r.cancelled)).toBe(true);
      requests[2].flush({ days: [historyDay()] });
      fixture.detectChanges();
      expect(buttons()[2].getAttribute('aria-pressed')).toBe('true');
      expect(el().querySelectorAll('.chart-stub').length).toBe(3);
    });

    it('R8 - range is not stored', () => {
      const before = { ...localStorage };
      buttons()[0].click();
      fixture.detectChanges();
      http
        .match((r) => r.url === HISTORY)
        .at(-1)
        ?.flush(body);
      expect({ ...localStorage }).toEqual(before);
      expect(sessionStorage.length).toBe(0);
    });

    it('R9 - shows loading', () => {
      expect(el().querySelector('[role="status"]')?.textContent).toContain(
        'Loading recovery history…',
      );
      respond(30);
    });

    it('R9 - shows error with retry', () => {
      respond(30, { error: 'internal_error', message: 'x' }, 500);
      expect(text()).toContain("Couldn't load recovery history.");
      (el().querySelector('.retry') as HTMLButtonElement).click();
      fixture.detectChanges();
      respond(30, { unexpected: true });
      expect(text()).toContain("Couldn't load recovery history.");
    });

    it('R9 - empty graph shows no data text', () => {
      respond(30, { days: [historyDay({ strain: null }), historyDay({ strain: null })] });
      expect(text()).toContain('No strain data in this period');
      expect(text()).not.toContain('No sleep data');
      expect(el().querySelectorAll('.chart-stub').length).toBe(2);
    });

    it('R9 - renders three charts', () => {
      respond(30);
      expect(el().querySelectorAll('.chart-stub').length).toBe(3);
      expect(sync.started).toBe(true);
    });

    it('R9 - failed sync reload during a range change shows the error, not a stuck spinner', () => {
      respond(30);
      buttons()[0].click();
      fixture.detectChanges();
      sync.finish();
      fixture.detectChanges();
      const latest = http.match((r) => r.url === HISTORY).at(-1);
      latest?.flush({}, { status: 500, statusText: 'x' });
      fixture.detectChanges();
      expect(text()).toContain("Couldn't load recovery history.");
    });

    it('R9 - reloads quietly when dataVersion changes', () => {
      respond(30);
      sync.finish();
      fixture.detectChanges();
      expect(el().querySelectorAll('.chart-stub').length).toBe(3);
      respond(30, {}, 500);
      expect(el().querySelectorAll('.chart-stub').length).toBe(3);
    });
  });

  it('R10 - flip recreates recovery page with new person', () => {
    const fixture = TestBed.createComponent(RecoveryShell);
    fixture.detectChanges();
    http.expectOne((r) => r.url === HISTORY).flush(body);
    fixture.detectChanges();
    (fixture.nativeElement.querySelectorAll('.range button')[0] as HTMLButtonElement).click();
    fixture.detectChanges();
    const pending = http.expectOne((r) => r.params.get('days') === '7');

    TestBed.inject(PersonStore).select('marina');
    fixture.detectChanges();

    expect(pending.cancelled).toBe(true);
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.chart-stub').length).toBe(0);
    expect(el.querySelector('[role="status"]')).not.toBeNull();
    const request = http.expectOne((r) => r.url === HISTORY);
    expect(request.request.params.get('days')).toBe('30');
    expect(request.request.headers.get('X-Person')).toBe('marina');
    request.flush(body);
  });
});
