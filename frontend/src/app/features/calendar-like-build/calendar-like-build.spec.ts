import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { trainingPlan } from '../../testing/training-plan-fixtures';
import { CalendarLikeBuild } from './calendar-like-build';
import { TrainingPlanStore } from './training-plan.store';

describe('CalendarLikeBuild', () => {
  let fixture: ComponentFixture<CalendarLikeBuild>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TrainingPlanStore],
    });
    fixture = TestBed.createComponent(CalendarLikeBuild);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent?.replace(/\s+/g, ' ') ?? '';

  function fail(status: number, body: object | null): void {
    http.expectOne('/api/training-plan').flush(body, { status, statusText: 'Error' });
    fixture.detectChanges();
  }

  it('004 R13 - loading', () => {
    expect(element().querySelector('h2')?.textContent).toBe('Marathon build');
    expect(element().querySelector('[role="status"]')?.textContent).toContain('Loading your plan…');
    expect(element().querySelector('[role="progressbar"]')).toBeNull();
    expect(element().querySelector('app-build-calendar')).toBeNull();
  });

  it('004 R13 - not configured empty state', () => {
    fail(404, {
      error: 'training_plan_not_configured',
      message: 'No training plan is configured.',
    });
    expect(text()).toContain('No training plan set up yet.');
    expect(text()).toContain(
      'Set TRAINING_PLAN_CSV in .env to the path of your plan CSV, then reload.',
    );
    expect(element().querySelector('button')).toBeNull();
    expect(element().querySelector('[role="alert"]')).toBeNull();
  });

  it('004 R13 - unreadable error with retry', () => {
    fail(503, {
      error: 'training_plan_unavailable',
      message: 'The training plan file could not be read.',
    });
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      "Couldn't read your training plan file.",
    );

    element().querySelector<HTMLButtonElement>('button.retry')?.click();
    fixture.detectChanges();
    expect(text()).toContain('Loading your plan…');
    http.expectOne('/api/training-plan').flush(trainingPlan());
    fixture.detectChanges();
    expect(element().querySelector('[role="progressbar"]')).not.toBeNull();
  });

  it('004 R13 - invalid shows backend message', () => {
    fail(422, { error: 'training_plan_invalid', message: 'WEEK 3: VOLUME row missing' });
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      'Your training plan file has a problem: WEEK 3: VOLUME row missing',
    );
    expect(element().querySelector('button.retry')).not.toBeNull();
  });

  it('004 R13 - other error', () => {
    fail(500, { error: 'internal_error', message: 'Something went wrong.' });
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      "Couldn't load your training plan.",
    );
    expect(element().querySelector('button.retry')).not.toBeNull();
  });

  it('004 R13 - network error is other error', () => {
    http.expectOne('/api/training-plan').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      "Couldn't load your training plan.",
    );
    expect(element().querySelector('button.retry')).not.toBeNull();
  });

  it('004 R13 - unexpected body is other error', () => {
    http.expectOne('/api/training-plan').flush({});
    fixture.detectChanges();
    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      "Couldn't load your training plan.",
    );
    expect(element().querySelector('[role="progressbar"]')).toBeNull();
  });

  it('004 R9 - 401 shows no plan error text', () => {
    fail(401, { error: 'not_connected', message: 'Connect your Strava account.' });
    expect(element().querySelector('[role="alert"]')).toBeNull();
    expect(text()).not.toContain("Couldn't");
  });

  it('004 R13 - normal state shows only heading and bar', () => {
    http.expectOne('/api/training-plan').flush(trainingPlan());
    fixture.detectChanges();
    const order = Array.from(
      element().querySelectorAll('h2, app-build-progress, app-target-paces, app-build-calendar'),
    ).map((el) => el.tagName.toLowerCase());
    expect(order).toEqual(['h2', 'app-build-progress']);
  });
});
