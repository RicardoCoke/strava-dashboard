import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RingsResponse } from '../../core/api/rings.models';
import { SyncStore } from '../../core/sync/sync.store';
import { SyncStoreStub } from '../../testing/sync-store-stub';
import { RingsSection } from './rings-section';

// All data here is invented.
const rings: RingsResponse = {
  date: '2026-11-09',
  sleep: { percent: 82, fill: 0.82, deltaLabel: '+5 pts vs 30-day avg' },
  recovery: { percent: 75, zone: 'green', fill: 0.75, deltaLabel: '−3 pts vs 30-day avg' },
  strain: null,
};

describe('RingsSection', () => {
  let fixture: ComponentFixture<RingsSection>;
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
    fixture = TestBed.createComponent(RingsSection);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';

  function respond(body: object, status = 200): void {
    http.expectOne('/api/rings').flush(body, { status, statusText: 'x' });
    fixture.detectChanges();
  }

  it('R7 - getRings calls GET /api/rings', () => {
    const request = http.expectOne('/api/rings');
    expect(request.request.method).toBe('GET');
    request.flush(rings);
  });

  it('R8 - shows loading rings', () => {
    expect(element().querySelector('[role="status"]')?.textContent).toContain('Loading rings…');
    expect(element().querySelectorAll('.track').length).toBe(3);
    expect(element().querySelectorAll('.arc').length).toBe(0);
    respond(rings);
  });

  it('R8 - ring values, arc and lines', () => {
    respond(rings);
    const figures = element().querySelectorAll('figure');
    expect(figures[0].getAttribute('aria-label')).toBe('Sleep 82%, +5 pts vs 30-day avg');
    expect(text()).toContain('82%');
    expect(text()).toContain('−3 pts vs 30-day avg');
    const arcs = element().querySelectorAll('.arc');
    expect(arcs.length).toBe(2);
    expect(arcs[1].classList).toContain('colour-green');
    const circumference = 2 * Math.PI * 45;
    expect(Number(arcs[0].getAttribute('stroke-dashoffset'))).toBeCloseTo(circumference * 0.18, 3);
  });

  it('R8 - empty ring shows dash and no data', () => {
    respond(rings);
    expect(element().querySelectorAll('.value')[2].textContent).toBe('—');
    expect(text()).toContain('No data');
  });

  it('R8 - shows error with retry', () => {
    respond({ error: 'internal_error', message: 'x' }, 500);
    expect(text()).toContain("Couldn't load rings.");
    (element().querySelector('button.retry') as HTMLButtonElement).click();
    respond(rings);
    expect(text()).toContain('82%');
  });

  it('R8 - bad body shows the error state', () => {
    respond({ date: 1 });
    expect(text()).toContain("Couldn't load rings.");
  });

  it('R8 - reloads quietly when dataVersion changes', () => {
    respond(rings);
    sync.dataVersion.update((v) => v + 1);
    fixture.detectChanges();
    respond({ error: 'internal_error', message: 'x' }, 500);
    expect(text()).toContain('82%');
    expect(text()).not.toContain("Couldn't load rings.");
  });
});
