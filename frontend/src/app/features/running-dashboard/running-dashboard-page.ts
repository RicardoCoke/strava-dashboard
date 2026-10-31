import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RunningDashboardResponse } from '../../core/api/running-dashboard.models';
import { RunningDashboardService } from '../../core/api/running-dashboard.service';
import { RingSpinner } from '../../shared/spinner/ring-spinner';
import { BuildCalendarSection } from '../calendar-like-build/build-calendar-section/build-calendar-section';
import { CalendarLikeBuild } from '../calendar-like-build/calendar-like-build';
import { TrainingPlanStore } from '../calendar-like-build/training-plan.store';
import { TrainingLoadSection } from '../training-load/training-load-section';
import { Vo2maxSection } from '../vo2max/vo2max-section';
import { HeadlineTiles } from './headline-tiles/headline-tiles';
import { HeartRateTrendChart } from './heart-rate-trend-chart/heart-rate-trend-chart';
import { SyncBanner } from './sync-banner/sync-banner';
import { WeeklyDistanceChart } from './weekly-distance-chart/weekly-distance-chart';
import { WorkoutPaceComparisonChart } from './workout-pace-comparison-chart/workout-pace-comparison-chart';

type DashboardState =
  { kind: 'loading' } | { kind: 'error' } | { kind: 'loaded'; data: RunningDashboardResponse };

function hasNoRuns(data: RunningDashboardResponse): boolean {
  return (
    data.weeklyDistance.weeks.every((week) => week.runCount === 0) &&
    data.heartRateTrend.types.length === 0 &&
    data.workoutPaceComparison.types.length === 0
  );
}

@Component({
  selector: 'app-running-dashboard-page',
  imports: [
    CalendarLikeBuild,
    TrainingLoadSection,
    Vo2maxSection,
    BuildCalendarSection,
    RingSpinner,
    SyncBanner,
    HeadlineTiles,
    WeeklyDistanceChart,
    HeartRateTrendChart,
    WorkoutPaceComparisonChart,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [TrainingPlanStore],
  templateUrl: './running-dashboard-page.html',
  styleUrl: './running-dashboard-page.scss',
})
export class RunningDashboardPage implements OnInit {
  private readonly service = inject(RunningDashboardService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<DashboardState>({ kind: 'loading' });
  protected readonly selectedHrTypeKey = signal<string | null>(null);
  protected readonly selectedPaceTypeKey = signal<string | null>(null);
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly empty = computed(() => {
    const data = this.data();
    return data !== null && hasNoRuns(data);
  });

  ngOnInit(): void {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  private load(): void {
    this.state.set({ kind: 'loading' });
    this.service
      .getDashboard()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => this.state.set({ kind: 'loaded', data }),
        error: (error: unknown) => {
          // A 401 is handled by the interceptor, which navigates to /connect.
          if (!(error instanceof HttpErrorResponse && error.status === 401)) {
            this.state.set({ kind: 'error' });
          }
        },
      });
  }
}
