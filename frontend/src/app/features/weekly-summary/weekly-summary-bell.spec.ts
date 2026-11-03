import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WeeklySummary } from '../../core/api/weekly-summary.models';
import { SyncStore } from '../../core/sync/sync.store';
import { formatDayMonth, formatDayMonthYear } from '../../shared/format/dates';
import { SyncStoreStub } from '../../testing/sync-store-stub';
import { lastWeekMonday, readSeenRevision } from './seen-revision';
import { WeeklySummaryBell } from './weekly-summary-bell';

// All data here is invented.
function addDays(iso: string, days: number): string {
  const [y, m, d] = iso.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + days));
  return date.toISOString().slice(0, 10);
}

const LAST_MONDAY = lastWeekMonday(new Date());
const THIS_MONDAY = addDays(LAST_MONDAY, 7);

function summary(weekStart: string, overrides: Partial<WeeklySummary> = {}): WeeklySummary {
  return {
    weekStart,
    weekEnd: addDays(weekStart, 6),
    inProgress: weekStart === THIS_MONDAY,
    runCount: 5,
    revision: `rev-${weekStart}`,
    notes: [
      { metric: 'distance', tone: 'worse', text: 'Volume 62.4 km over 5 runs, watch load' },
      { metric: 'longestRun', tone: 'better', text: 'Longest run of the block: 32.0 km' },
    ],
    ...overrides,
  };
}

describe('WeeklySummaryBell', () => {
  let fixture: ComponentFixture<WeeklySummaryBell>;
  let http: HttpTestingController;
  let sync: SyncStoreStub;

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const bell = (): HTMLButtonElement => el().querySelector('.bell') as HTMLButtonElement;
  const badge = () => el().querySelector('.badge');
  const panel = () => el().querySelector('[role="dialog"]');
  const button = (label: string) =>
    el().querySelector(`[aria-label="${label}"]`) as HTMLButtonElement;
  const flush = (summaries: WeeklySummary[]) => {
    http.expectOne('/api/weekly-summaries').flush({ summaries });
    fixture.detectChanges();
  };
  const click = (target: HTMLElement) => {
    target.click();
    fixture.detectChanges();
  };

  beforeEach(() => {
    localStorage.clear();
    sync = new SyncStoreStub();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SyncStore, useValue: sync },
      ],
    });
    fixture = TestBed.createComponent(WeeklySummaryBell);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('R11 - badge when revision unseen, never above one', () => {
    flush([summary(THIS_MONDAY), summary(LAST_MONDAY), summary(addDays(LAST_MONDAY, -7))]);
    expect(badge()?.textContent?.trim()).toBe('1');
    expect(bell().getAttribute('aria-label')).toBe('Weekly summary, 1 new');
  });

  it('R11 - opening clears badge and stores the revision', () => {
    flush([summary(THIS_MONDAY)]);
    click(bell());
    expect(panel()).not.toBeNull();
    expect(badge()).toBeNull();
    expect(readSeenRevision()).toBe(`rev-${THIS_MONDAY}`);
    expect(bell().getAttribute('aria-label')).toBe('Weekly summary');
  });

  it('R11 - badge returns after sync change, but not while open', () => {
    flush([summary(THIS_MONDAY)]);
    click(bell());
    click(bell());
    sync.finish();
    fixture.detectChanges();
    flush([summary(THIS_MONDAY, { revision: 'rev-6-runs', runCount: 6 })]);
    expect(badge()).not.toBeNull();

    click(bell());
    sync.finish();
    fixture.detectChanges();
    flush([summary(THIS_MONDAY, { revision: 'rev-7-runs', runCount: 7 })]);
    expect(badge()).toBeNull();
    expect(readSeenRevision()).toBe('rev-7-runs');
  });

  it('R11 - older weeks never badge', () => {
    flush([summary(addDays(LAST_MONDAY, -7))]);
    expect(badge()).toBeNull();
  });

  it('R11 - no badge while loading or after an error', () => {
    expect(badge()).toBeNull();
    http
      .expectOne('/api/weekly-summaries')
      .flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(badge()).toBeNull();
  });

  it('R11 - no badge when empty', () => {
    flush([]);
    expect(badge()).toBeNull();
    click(bell());
    expect(panel()?.textContent).toContain('No week with 5 or more runs yet.');
    expect(button('Older week')).toBeNull();
  });

  it('R11 - survives storage errors', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('disabled');
    });
    flush([summary(THIS_MONDAY)]);
    expect(() => click(bell())).not.toThrow();
    expect(panel()).not.toBeNull();
  });

  it('R12 - panel is a fixed overlay outside the page flow', async () => {
    flush([summary(THIS_MONDAY)]);
    click(bell());
    await fixture.whenStable();
    // Rendered inside the bell's own host, never next to the dashboard cards.
    expect(el().contains(panel())).toBe(true);
    expect(getComputedStyle(panel() as Element).position).toBe('fixed');
  });

  it('R12 - focus moves to the heading and back to the bell on Escape', async () => {
    flush([summary(THIS_MONDAY)]);
    click(bell());
    await fixture.whenStable();
    expect(document.activeElement?.id).toBe('weekly-summary-heading');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(bell());
  });

  it('R12 - dialog aria, closes with cross and Escape', () => {
    flush([summary(THIS_MONDAY)]);
    click(bell());
    expect(bell().getAttribute('aria-expanded')).toBe('true');
    expect(bell().getAttribute('aria-controls')).toBe(panel()?.id ?? 'missing');
    expect(panel()?.getAttribute('aria-labelledby')).toBe('weekly-summary-heading');

    click(button('Close weekly summary'));
    expect(panel()).toBeNull();
    expect(document.activeElement).toBe(bell());

    click(bell());
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(panel()).toBeNull();
  });

  it('R13 - opens on newest week, arrows step and heading shows the week', () => {
    const older = addDays(LAST_MONDAY, -7);
    flush([summary(THIS_MONDAY), summary(older)]);
    click(bell());
    expect(button('Newer week').disabled).toBe(true);
    expect(panel()?.textContent).toContain('In progress');

    click(button('Older week'));
    expect(el().querySelector('h2')?.textContent?.trim()).toBe(
      `${formatDayMonth(older)} – ${formatDayMonthYear(addDays(older, 6))}`,
    );
    expect(button('Older week').disabled).toBe(true);
    expect(panel()?.textContent).not.toContain('In progress');

    click(bell());
    click(bell());
    expect(button('Newer week').disabled).toBe(true);
  });

  it('R13 - keeps week on reload', () => {
    const older = addDays(LAST_MONDAY, -7);
    flush([summary(THIS_MONDAY), summary(older)]);
    click(bell());
    click(button('Older week'));
    sync.finish();
    fixture.detectChanges();
    flush([summary(THIS_MONDAY, { revision: 'new' }), summary(older)]);
    expect(button('Older week').disabled).toBe(true);
  });

  it('R14 - loading, error and retry', () => {
    click(bell());
    expect(panel()?.textContent).toContain('Loading weekly summary…');
    http.expectOne('/api/weekly-summaries').flush({ nope: true });
    fixture.detectChanges();
    expect(panel()?.textContent).toContain("Couldn't load your weekly summary.");
    click(el().querySelector('.retry') as HTMLElement);
    flush([summary(THIS_MONDAY)]);
    expect(panel()?.querySelectorAll('li').length).toBe(2);
  });

  it('R14 - notes with tone classes in order', () => {
    flush([summary(THIS_MONDAY)]);
    click(bell());
    const items = Array.from(panel()?.querySelectorAll('li') ?? []);
    expect(items.map((li) => li.className)).toEqual(['note tone-worse', 'note tone-better']);
    expect(items[0].textContent?.trim()).toBe('Volume 62.4 km over 5 runs, watch load');
  });

  it('R14 - quiet reload keeps content when it fails', () => {
    flush([summary(THIS_MONDAY)]);
    click(bell());
    sync.finish();
    fixture.detectChanges();
    http
      .expectOne('/api/weekly-summaries')
      .flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(panel()?.querySelectorAll('li').length).toBe(2);
  });
});
