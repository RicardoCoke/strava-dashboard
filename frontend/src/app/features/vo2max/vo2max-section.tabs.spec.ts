import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NormalizedVo2maxResponse } from '../../core/api/normalized-vo2max.models';
import { SyncStore } from '../../core/sync/sync.store';
import { stubCharts } from '../../testing/dashboard-fixtures';
import { SyncStoreStub } from '../../testing/sync-store-stub';
import {
  emptyNormalizedVo2max,
  normalizedVo2max,
  normalizedVo2maxWeeks,
  vo2max,
} from '../../testing/vo2max-fixtures';
import { Vo2maxSection } from './vo2max-section';

// Feature 016: the "Vo2" / "Normalized Vo2" tabs and the normalized panel.
describe('Vo2maxSection tabs', () => {
  let fixture: ComponentFixture<Vo2maxSection>;
  let http: HttpTestingController;
  let sync: SyncStoreStub;
  const NORMALIZED = '/api/vo2max/normalized';

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
    fixture = TestBed.createComponent(Vo2maxSection);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/vo2max').flush(vo2max());
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const tabs = () => Array.from(element().querySelectorAll<HTMLButtonElement>('[role="tab"]'));
  const panel = () => element().querySelector<HTMLElement>('[role="tabpanel"]');
  const panelText = () => panel()?.textContent?.replace(/\s+/g, ' ') ?? '';
  const all = (selector: string) =>
    Array.from(panel()?.querySelectorAll(selector) ?? []).map((node) =>
      (node.textContent ?? '').replace(/\s+/g, ' ').trim(),
    );

  function open(tab: 'Vo2' | 'Normalized Vo2'): void {
    tabs()
      .find((button) => button.textContent?.trim() === tab)
      ?.click();
    fixture.detectChanges();
  }

  function openNormalized(data: NormalizedVo2maxResponse): void {
    open('Normalized Vo2');
    http.expectOne(NORMALIZED).flush(data);
    fixture.detectChanges();
  }

  function failNormalized(status: number, body: object | null): void {
    open('Normalized Vo2');
    http.expectOne(NORMALIZED).flush(body, { status, statusText: 'Error' });
    fixture.detectChanges();
  }

  it('R8 - Vo2 tab selected by default', () => {
    const tablist = element().querySelector('[role="tablist"]');
    expect(tablist?.getAttribute('aria-label')).toBe('VO2max estimate');
    expect(element().querySelector('section')?.children[1]).toBe(tablist);
    expect(tabs().map((tab) => tab.textContent?.trim())).toEqual(['Vo2', 'Normalized Vo2']);
    expect(tabs().map((tab) => tab.getAttribute('aria-selected'))).toEqual(['true', 'false']);
    expect(panel()?.getAttribute('aria-labelledby')).toBe(tabs()[0].id);
    expect(panel()?.querySelector('app-vo2max-chart')).not.toBeNull();
  });

  it('R8 - switches between tabs', () => {
    openNormalized(normalizedVo2max());
    expect(tabs().map((tab) => tab.getAttribute('aria-selected'))).toEqual(['false', 'true']);
    expect(panel()?.getAttribute('aria-labelledby')).toBe(tabs()[1].id);
    expect(element().querySelector('app-vo2max-chart')).toBeNull();
    expect(element().querySelectorAll('[role="tabpanel"]').length).toBe(1);

    open('Vo2');
    expect(tabs().map((tab) => tab.getAttribute('aria-selected'))).toEqual(['true', 'false']);
    expect(element().querySelector('app-normalized-vo2max-panel')).toBeNull();
    expect(element().querySelector('app-vo2max-chart')).not.toBeNull();
  });

  it('R8 - tabs work with the keyboard', () => {
    const tablist = element().querySelector<HTMLElement>('[role="tablist"]');
    const press = (key: string) => {
      const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
      document.activeElement?.dispatchEvent(event);
      fixture.detectChanges();
      return event;
    };
    tabs()[0].focus();
    expect(tabs().map((tab) => tab.tabIndex)).toEqual([0, -1]);

    expect(press('ArrowRight').defaultPrevented).toBe(true);
    expect(tabs()[1].getAttribute('aria-selected')).toBe('true');
    expect(document.activeElement).toBe(tabs()[1]);
    expect(tabs().map((tab) => tab.tabIndex)).toEqual([-1, 0]);
    http.expectOne(NORMALIZED).flush(normalizedVo2max());
    fixture.detectChanges();

    press('ArrowRight');
    expect(document.activeElement).toBe(tabs()[0]);
    press('End');
    expect(document.activeElement).toBe(tabs()[1]);
    press('Home');
    expect(tabs()[0].getAttribute('aria-selected')).toBe('true');
    expect(press('a').defaultPrevented).toBe(false);
    expect(tablist).not.toBeNull();
  });

  it('R8 - normalized loads on first open only', () => {
    http.expectNone(NORMALIZED);
    openNormalized(normalizedVo2max());
    open('Vo2');
    open('Normalized Vo2');
    http.expectNone(NORMALIZED);
    expect(all('.vo2max-value')).toEqual(['54.2']);
    http.verify();
  });

  it('R8 - quiet refresh reloads loaded tabs only', () => {
    sync.finish();
    fixture.detectChanges();
    http.expectOne('/api/vo2max').flush(vo2max());
    http.expectNone(NORMALIZED);

    openNormalized(normalizedVo2max());
    sync.finish();
    fixture.detectChanges();
    http.expectOne('/api/vo2max').flush(vo2max());
    const reload = http.expectOne(NORMALIZED);
    expect(element().querySelector('[role="status"]')).toBeNull();
    reload.flush({ error: 'internal_error', message: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(element().querySelector('[role="alert"]')).toBeNull();
    expect(all('.vo2max-value')).toEqual(['54.2']);
    http.verify();
  });

  it('R8 - tab choice not persisted', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    openNormalized(normalizedVo2max());
    expect(setItem).not.toHaveBeenCalled();

    const fresh = TestBed.createComponent(Vo2maxSection);
    fresh.detectChanges();
    const selected = fresh.nativeElement.querySelector('[role="tab"][aria-selected="true"]');
    expect(selected?.textContent?.trim()).toBe('Vo2');
    http.match('/api/vo2max');
  });

  it('R8 - tabs use theme tokens', () => {
    const css = Array.from(document.querySelectorAll('style'))
      .map((style) => style.textContent ?? '')
      .filter((text) => text.includes('.vo2max-tab'));
    expect(css.length).toBeGreaterThan(0);
    css.forEach((text) => {
      expect(text).toContain('var(--color-accent)');
      expect(text).not.toMatch(/#[0-9a-f]{3,8}\b|rgba?\(/i);
    });
  });

  it('R10 - normalized note always shown', () => {
    open('Normalized Vo2');
    const note =
      'Hill- and drift-adjusted: grade-adjusted pace, only the first 60 minutes of easy and long runs, and each run counts once.';
    expect(panel()?.querySelector('.vo2max-note')?.textContent).toBe(note);
    http.expectOne(NORMALIZED).flush(emptyNormalizedVo2max());
    fixture.detectChanges();
    expect(panel()?.querySelector('.vo2max-note')?.textContent).toBe(note);
  });

  it('R10 - normalized value caption tiles chart footnote', () => {
    openNormalized(normalizedVo2max());
    expect(all('.vo2max-label')).toEqual(['Normalized VO2max']);
    expect(all('.vo2max-value')).toEqual(['54.2']);
    expect(all('.vo2max-unit')).toEqual(['ml/kg/min']);
    expect(all('.vo2max-caption')).toEqual(['Based on 4 runs in the last 7 days (26 Oct – 1 Nov)']);
    expect(all('.race-label')).toEqual(['5K', '10K', 'Half marathon', 'Marathon']);
    expect(all('.race-note')).toEqual(['Marathon predictions from VO2max tend to be optimistic.']);
    expect(panel()?.querySelector('app-normalized-vo2max-chart')).not.toBeNull();
    expect(all('.footnote p')).toEqual(['Max HR 194 bpm (estimate; set MAX_HR_BPM to change)']);
  });

  it('R10 - footnote with split import pending', () => {
    openNormalized(
      normalizedVo2max({ runsPendingDetail: 1, maxHr: { bpm: 190, source: 'configured' } }),
    );
    expect(all('.footnote p')).toEqual([
      'Max HR 190 bpm (from MAX_HR_BPM)',
      '1 run still importing split data; numbers will update.',
    ]);
  });

  it('R12 - loading', () => {
    open('Normalized Vo2');
    expect(panel()?.querySelector('[role="status"]')?.textContent).toContain(
      'Loading normalized VO2max…',
    );
    expect(panel()?.querySelector('app-normalized-vo2max-chart')).toBeNull();
    http.expectOne(NORMALIZED);
  });

  it('R12 - invalid config without retry', () => {
    const message = 'MAX_HR_BPM is not a whole number between 120 and 230.';
    failNormalized(422, { error: 'vo2max_invalid_config', message });
    expect(panel()?.querySelector('[role="alert"]')?.textContent?.trim()).toBe(
      `VO2max settings have a problem: ${message}`,
    );
    expect(panelText()).toContain('Set MAX_HR_BPM in .env (for example 194), or leave it empty');
    expect(panel()?.querySelector('button')).toBeNull();
    expect(tabs().length).toBe(2);
  });

  it('R12 - error with retry requests normalized', () => {
    failNormalized(500, { error: 'internal_error', message: 'Something went wrong.' });
    expect(panel()?.querySelector('[role="alert"]')?.textContent).toContain(
      "Couldn't load normalized VO2max.",
    );
    panel()?.querySelector<HTMLButtonElement>('button.retry')?.click();
    fixture.detectChanges();
    expect(panelText()).toContain('Loading normalized VO2max…');
    http.expectNone('/api/vo2max');
    http.expectOne(NORMALIZED).flush(normalizedVo2max());
    fixture.detectChanges();
    expect(panel()?.querySelector('app-normalized-vo2max-chart')).not.toBeNull();
  });

  it('R12 - unexpected body shows the error state', () => {
    openNormalized(vo2max() as unknown as NormalizedVo2maxResponse);
    expect(panelText()).toContain("Couldn't load normalized VO2max.");
  });

  it('R12 - 401 shows no error', () => {
    failNormalized(401, { error: 'not_connected', message: 'Connect your Strava account.' });
    expect(panel()?.querySelector('[role="alert"]')).toBeNull();
  });

  it('R12 - empty', () => {
    openNormalized(emptyNormalizedVo2max());
    expect(panelText()).toContain('No qualifying runs in the last 6 months');
    expect(panelText()).toContain('Distance-rep workouts (e.g. 6x1km) are not counted.');
    expect(panel()?.querySelector('app-race-prediction-tiles')).toBeNull();
    expect(panel()?.querySelector('app-normalized-vo2max-chart')).toBeNull();
  });

  it('R12 - empty importing', () => {
    openNormalized(emptyNormalizedVo2max({ runsPendingDetail: 12 }));
    expect(panelText()).toContain('Split data is still importing: 12 runs to go.');
    expect(panel()?.querySelector('app-normalized-vo2max-chart')).toBeNull();
  });

  it('R12 - no current shows chart', () => {
    openNormalized(normalizedVo2max({ current: null, predictions: [] }));
    expect(panelText()).toContain(
      'No qualifying runs in the last 4 weeks, so there is no current estimate or race prediction.',
    );
    expect(panel()?.querySelector('app-race-prediction-tiles')).toBeNull();
    expect(panel()?.querySelector('app-normalized-vo2max-chart')).not.toBeNull();
    expect(panel()?.querySelector('.footnote')).not.toBeNull();
  });

  it('R12 - normal order', () => {
    openNormalized(normalizedVo2max({ weeks: normalizedVo2maxWeeks() }));
    const host = panel()?.querySelector('app-normalized-vo2max-panel');
    const order = Array.from(host?.children ?? []).map(
      (child) => child.tagName.toLowerCase() + (child.className ? `.${child.className}` : ''),
    );
    expect(order).toEqual([
      'p.vo2max-note',
      'p.vo2max-current',
      'p.vo2max-caption',
      'app-race-prediction-tiles',
      'app-normalized-vo2max-chart',
      'div.footnote',
    ]);
  });

  it('R12 - tabs fail independently', () => {
    failNormalized(500, { error: 'internal_error', message: 'x' });
    open('Vo2');
    expect(panel()?.querySelector('app-vo2max-chart')).not.toBeNull();
    expect(element().querySelector('[role="alert"]')).toBeNull();
  });
});
