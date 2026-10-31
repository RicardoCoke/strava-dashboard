import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { BuildProgressBar } from './build-progress/build-progress-bar';
import { TrainingPlanStore } from './training-plan.store';

/** The "Marathon build" card at the top of the dashboard: progress bar, and the plan's loading and error states. */
@Component({
  selector: 'app-calendar-like-build',
  imports: [BuildProgressBar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './calendar-like-build.html',
  styleUrl: './calendar-like-build.scss',
})
export class CalendarLikeBuild implements OnInit {
  protected readonly store = inject(TrainingPlanStore);

  ngOnInit(): void {
    this.store.ensureLoaded();
  }

  protected retry(): void {
    this.store.retry();
  }
}
