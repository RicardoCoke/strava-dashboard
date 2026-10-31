import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { trainingPlan } from '../../testing/training-plan-fixtures';
import { TrainingPlanStore } from './training-plan.store';

describe('TrainingPlanStore', () => {
  let store: TrainingPlanStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), TrainingPlanStore],
    });
    store = TestBed.inject(TrainingPlanStore);
    http = TestBed.inject(HttpTestingController);
  });

  it('004 R16 - ensureLoaded sends one request however often it is called', () => {
    store.ensureLoaded();
    store.ensureLoaded();
    http.expectOne('/api/training-plan').flush(trainingPlan());
    store.ensureLoaded();
    http.expectNone('/api/training-plan');
    expect(store.state().kind).toBe('loaded');
    expect(store.data()?.weeks.length).toBe(16);
  });

  it('004 R16 - retry sets loading and requests again', () => {
    store.ensureLoaded();
    http
      .expectOne('/api/training-plan')
      .flush(
        { error: 'training_plan_unavailable', message: 'x' },
        { status: 503, statusText: 'Error' },
      );
    expect(store.errorText()).toBe("Couldn't read your training plan file.");

    store.retry();
    expect(store.state().kind).toBe('loading');
    http.expectOne('/api/training-plan').flush(trainingPlan());
    expect(store.state().kind).toBe('loaded');
    http.verify();
  });

  it('004 R16 - 401 keeps state', () => {
    store.ensureLoaded();
    http
      .expectOne('/api/training-plan')
      .flush({ error: 'not_connected', message: 'x' }, { status: 401, statusText: 'Unauthorized' });
    expect(store.state().kind).toBe('loading');
    expect(store.errorText()).toBe('');
  });

  it('004 R16 - unexpected body is other error', () => {
    store.ensureLoaded();
    http.expectOne('/api/training-plan').flush({ weeks: 'nope' });
    expect(store.state()).toEqual({ kind: 'error', reason: 'other', message: null });
    expect(store.data()).toBeNull();
  });

  it('004 R16 - pending request is cancelled when the page is destroyed', () => {
    store.ensureLoaded();
    const request = http.expectOne('/api/training-plan');
    TestBed.resetTestingModule();
    expect(request.cancelled).toBe(true);
  });
});
