import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { PersonStore } from '../../core/person/person.store';
import { RunningDashboardPage } from './running-dashboard-page';

/**
 * Keyed on the person, so a flip destroys the whole page with its stores, timers and requests: no data of the previous
 * person can stay on screen (feature 019).
 */
@Component({
  selector: 'app-dashboard-shell',
  imports: [RunningDashboardPage],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (person of [store.person()]; track person) {
      <app-running-dashboard-page />
    }
  `,
})
export class DashboardShell {
  protected readonly store = inject(PersonStore);
}
