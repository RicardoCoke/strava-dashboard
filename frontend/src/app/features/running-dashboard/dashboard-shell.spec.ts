import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { personInterceptor } from '../../core/interceptors/person.interceptor';
import { Person, PersonStore } from '../../core/person/person.store';
import { stubCharts } from '../../testing/dashboard-fixtures';
import { lastWeekMonday } from '../weekly-summary/seen-revision';
import { DashboardShell } from './dashboard-shell';

describe('DashboardShell', () => {
  let fixture: ComponentFixture<DashboardShell>;
  let http: HttpTestingController;

  const el = () => fixture.nativeElement as HTMLElement;
  const page = () => el().querySelector('app-running-dashboard-page');

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([personInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    stubCharts();
    fixture = TestBed.createComponent(DashboardShell);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => localStorage.clear());

  it('R13 - switch left of bell', () => {
    const actions = el().querySelector('.header-actions');
    expect(Array.from(actions?.children ?? []).map((child) => child.tagName.toLowerCase())).toEqual(
      ['app-person-switch', 'app-weekly-summary-bell'],
    );
  });

  it('R14 - flip recreates page and cancels requests', () => {
    const first = page();
    const ricardoRequest = http.expectOne('/api/dashboard/running');
    expect(ricardoRequest.request.headers.get('X-Person')).toBe('ricardo');

    TestBed.inject(PersonStore).select('marina');
    fixture.detectChanges();

    expect(page()).not.toBe(first);
    expect(ricardoRequest.cancelled).toBe(true);
    const marinaRequests = http.match('/api/dashboard/running').filter((r) => !r.cancelled);
    expect(marinaRequests.length).toBe(1);
    expect(marinaRequests[0].request.headers.get('X-Person')).toBe('marina');
  });

  it('R14 - no old data while loading', () => {
    http.expectOne('/api/dashboard/running').flush({
      weeklyDistance: { weeks: [] },
      heartRateTrend: { types: [] },
      workoutPaceComparison: { types: [] },
    });
    fixture.detectChanges();
    expect(el().textContent).toContain('No runs in the last 5 months');

    TestBed.inject(PersonStore).select('marina');
    fixture.detectChanges();

    expect(el().textContent).not.toContain('No runs in the last 5 months');
    expect(el().textContent).toContain('Loading your runs…');
  });

  const emptyDashboard = {
    weeklyDistance: { weeks: [] },
    heartRateTrend: { types: [] },
    workoutPaceComparison: { types: [] },
  };

  function flip(person: Person): void {
    TestBed.inject(PersonStore).select(person);
    fixture.detectChanges();
  }

  /** The one live request to [url] for the page now on screen. */
  function live(url: string): TestRequest {
    const [request] = http.match(url).filter((r) => !r.cancelled);
    return request;
  }

  it('R14 - no old data on error', () => {
    live('/api/dashboard/running').flush(emptyDashboard);
    fixture.detectChanges();

    flip('marina');
    live('/api/dashboard/running').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(el().textContent).toContain("Couldn't load your dashboard.");
    expect(el().textContent).not.toContain('No runs in the last 5 months');
  });

  it('R14 - flip has no navigation', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate');
    const navigateByUrl = vi.spyOn(router, 'navigateByUrl');

    flip('marina');

    expect(navigate).not.toHaveBeenCalled();
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('R14 - flip stops sync timer', () => {
    vi.useFakeTimers();
    try {
      // Ricardo's status says a sync is due, so his page schedules a POST.
      live('/api/sync').flush({
        status: 'ok',
        lastSyncedAt: null,
        retryAfter: null,
        runsPendingDetail: 0,
        nextSyncInSeconds: 60,
      });

      flip('marina');
      vi.advanceTimersByTime(120_000);

      expect(http.match((r) => r.method === 'POST' && r.url === '/api/sync').length).toBe(0);
      const status = http.match('/api/sync').filter((r) => !r.cancelled);
      expect(status.map((r) => r.request.headers.get('X-Person'))).toEqual(['marina']);
    } finally {
      vi.useRealTimers();
    }
  });

  const summaries = {
    summaries: [
      {
        weekStart: lastWeekMonday(new Date()),
        weekEnd: lastWeekMonday(new Date()),
        inProgress: false,
        runCount: 5,
        revision: 'rev-1',
        notes: [],
      },
    ],
  };
  const bell = () => el().querySelector<HTMLButtonElement>('.bell');
  const badge = () => el().querySelector('.bell .badge, app-weekly-summary-bell .badge');

  it('R15 - bell follows person, badges are separate and panel closes', () => {
    live('/api/weekly-summaries').flush(summaries);
    fixture.detectChanges();
    expect(badge()).not.toBeNull();

    // Ricardo opens his summary: his badge clears and the panel is open.
    bell()?.click();
    fixture.detectChanges();
    expect(badge()).toBeNull();
    expect(el().querySelector('[role="dialog"]')).not.toBeNull();

    flip('marina');
    expect(el().querySelector('[role="dialog"]')).toBeNull();
    const request = live('/api/weekly-summaries');
    expect(request.request.headers.get('X-Person')).toBe('marina');
    request.flush(summaries);
    fixture.detectChanges();
    expect(badge()).not.toBeNull();

    flip('ricardo');
    live('/api/weekly-summaries').flush(summaries);
    fixture.detectChanges();
    expect(badge()).toBeNull();
  });
});
