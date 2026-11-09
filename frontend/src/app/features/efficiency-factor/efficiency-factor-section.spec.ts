import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EfficiencyFactorResponse } from '../../core/api/efficiency-factor.models';
import { SyncStore } from '../../core/sync/sync.store';
import { stubCharts } from '../../testing/dashboard-fixtures';
import { efficiencyFactor } from '../../testing/efficiency-factor-fixtures';
import { SyncStoreStub } from '../../testing/sync-store-stub';
import { EfficiencyFactorSection } from './efficiency-factor-section';

describe('EfficiencyFactorSection', () => {
  let fixture: ComponentFixture<EfficiencyFactorSection>;
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
    fixture = TestBed.createComponent(EfficiencyFactorSection);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';

  function respond(data: EfficiencyFactorResponse | object): void {
    http.expectOne('/api/efficiency-factor').flush(data);
    fixture.detectChanges();
  }

  it('014 R11 - shows loading state', () => {
    expect(element().querySelector('h2')?.textContent).toBe('Efficiency factor');
    const status = element().querySelector('[role="status"]');
    expect(status?.textContent).toContain('Loading efficiency factor…');
    expect(status?.querySelector('app-ring-spinner')).not.toBeNull();
  });

  it('014 R11 - shows error state with retry', () => {
    http
      .expectOne('/api/efficiency-factor')
      .flush({ error: 'internal_error', message: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      "Couldn't load the efficiency factor.",
    );

    element().querySelector<HTMLButtonElement>('button.retry')?.click();
    fixture.detectChanges();
    expect(text()).toContain('Loading efficiency factor…');
  });

  it('014 R11 - unexpected body shows error', () => {
    respond({ ...efficiencyFactor(), points: [{ date: '2026-10-29' }] });
    expect(element().querySelector('[role="alert"]')).not.toBeNull();
  });

  it('014 R11 - shows empty and still-importing states', () => {
    respond(efficiencyFactor({ points: [], current: null, change: null }));
    expect(text()).toContain('No easy or long runs with heart rate in the last 6 months');
    expect(element().querySelector('app-efficiency-factor-chart')).toBeNull();
    expect(element().querySelector('.ef-tile')).toBeNull();

    sync.finish();
    fixture.detectChanges();
    respond(efficiencyFactor({ points: [], current: null, change: null, runsPendingDetail: 3 }));
    expect(text()).toContain(
      "Moving time is still importing for 3 runs. The chart will appear when it's done.",
    );
  });

  it('014 R11 - normal state shows tile, chart and footnote', () => {
    respond(efficiencyFactor());
    expect(element().querySelector('.ef-tile')).not.toBeNull();
    expect(element().querySelector('app-efficiency-factor-chart')).not.toBeNull();
    expect(element().querySelector('.footnote')?.textContent).toContain(
      'EF = average speed (m/min) ÷ average heart rate.',
    );
  });

  it('014 R10 - tile shows current EF with unit and caption', () => {
    respond(efficiencyFactor());
    expect(element().querySelector('.ef-value')?.textContent).toBe('1.65');
    expect(element().querySelector('.ef-unit')?.textContent).toBe('m/min/beat');
    expect(element().querySelector('.ef-caption')?.textContent).toBe(
      '4-week average of 6 runs to 29 Oct',
    );
    const change = element().querySelector('.ef-change');
    expect(change?.textContent).toBe('+4.2% since 3 May');
    expect(change?.classList).toContain('tone-better');
  });

  it('014 R10 - notes replace current and change when data is short', () => {
    respond(efficiencyFactor({ change: null }));
    expect(element().querySelector('.ef-value')?.textContent).toBe('1.65');
    expect(text()).toContain('Not enough data yet for a change since the start of the window');
    expect(element().querySelector('.ef-change')).toBeNull();

    sync.finish();
    fixture.detectChanges();
    respond(efficiencyFactor({ current: null, change: null }));
    expect(element().querySelector('.ef-value')).toBeNull();
    expect(text()).toContain(
      'Not enough recent runs for a current value (needs 3 in the last 4 weeks)',
    );
    expect(text()).not.toContain('Not enough data yet for a change');
  });

  it('014 R8 - a sync during the first load still shows a failure', () => {
    sync.finish();
    fixture.detectChanges();
    const [first, reload] = http.match('/api/efficiency-factor');
    expect(first.cancelled).toBe(true);
    reload.flush({ error: 'internal_error', message: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      "Couldn't load the efficiency factor.",
    );
  });

  it('014 R8 - reloads quietly after a sync', () => {
    respond(efficiencyFactor());
    sync.finish();
    fixture.detectChanges();
    const reload = http.expectOne('/api/efficiency-factor');
    expect(element().querySelector('.ef-value')?.textContent).toBe('1.65');
    reload.flush({ error: 'internal_error', message: 'x' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(element().querySelector('[role="alert"]')).toBeNull();
    expect(element().querySelector('.ef-value')?.textContent).toBe('1.65');

    sync.finish();
    fixture.detectChanges();
    respond(efficiencyFactor({ current: { efficiency: 1.7, date: '2026-10-31', runCount: 7 } }));
    expect(element().querySelector('.ef-value')?.textContent).toBe('1.70');
  });
});
