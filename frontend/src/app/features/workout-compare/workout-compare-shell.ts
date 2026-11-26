import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { PersonStore } from '../../core/person/person.store';
import { WorkoutComparePage } from './workout-compare-page';

/** Keyed on the person, like `RecoveryShell` (023), so a flip never shows the previous person's runs. */
@Component({
  selector: 'app-workout-compare-shell',
  imports: [WorkoutComparePage],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (person of [store.person()]; track person) {
      <app-workout-compare-page />
    }
  `,
})
export class WorkoutCompareShell {
  protected readonly store = inject(PersonStore);
}
