import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TrainingPlanService } from '../../core/api/training-plan.service';
import { PlanState, isTrainingPlanResponse, planErrorState, planErrorText } from './plan-view';

/**
 * The training plan for one dashboard page: one request shared by the "Marathon build" card at the top
 * and the "Build calendar" card at the bottom. Provided by the page, so each visit loads it again.
 */
@Injectable()
export class TrainingPlanStore {
  private readonly service = inject(TrainingPlanService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly current = signal<PlanState>({ kind: 'loading' });
  private started = false;

  readonly state = this.current.asReadonly();
  readonly data = computed(() => {
    const state = this.current();
    return state.kind === 'loaded' ? state.data : null;
  });
  readonly errorText = computed(() => {
    const state = this.current();
    return state.kind === 'error' ? planErrorText(state.reason, state.message) : '';
  });

  /** Sends the request the first time it is called; later calls do nothing. */
  ensureLoaded(): void {
    if (!this.started) {
      this.load();
    }
  }

  retry(): void {
    this.load();
  }

  private load(): void {
    this.started = true;
    this.current.set({ kind: 'loading' });
    this.service
      .getTrainingPlan()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data: unknown) =>
          this.current.set(
            isTrainingPlanResponse(data)
              ? { kind: 'loaded', data }
              : { kind: 'error', reason: 'other', message: null },
          ),
        error: (error: unknown) => {
          const next = planErrorState(error);
          if (next) {
            this.current.set(next);
          }
        },
      });
  }
}
