import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { BuildCalendar } from '../build-calendar/build-calendar';
import { TargetPaces } from '../target-paces/target-paces';
import { TrainingPlanStore } from '../training-plan.store';

/**
 * The "Build calendar" card at the bottom of the dashboard: target paces and the calendar.
 * It renders only once the plan has loaded; the top card shows the loading and error states.
 */
@Component({
  selector: 'app-build-calendar-section',
  imports: [TargetPaces, BuildCalendar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.data(); as data) {
      <section class="card plan-calendar" aria-labelledby="plan-calendar-heading">
        <h2 id="plan-calendar-heading">Build calendar</h2>
        <app-target-paces [paces]="data.targetPaces" />
        <app-build-calendar [weeks]="data.weeks" [progress]="data.progress" />
      </section>
    }
  `,
  styles: `
    :host {
      display: block;
      margin-top: 1rem;
    }
  `,
})
export class BuildCalendarSection implements OnInit {
  protected readonly store = inject(TrainingPlanStore);

  ngOnInit(): void {
    this.store.ensureLoaded();
  }
}
