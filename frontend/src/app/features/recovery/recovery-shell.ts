import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { PersonStore } from '../../core/person/person.store';
import { RecoveryPage } from './recovery-page';

/** Keyed on the person, like `DashboardShell` (019), so a flip never shows the previous person's graphs. */
@Component({
  selector: 'app-recovery-shell',
  imports: [RecoveryPage],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (person of [store.person()]; track person) {
      <app-recovery-page />
    }
  `,
})
export class RecoveryShell {
  protected readonly store = inject(PersonStore);
}
