import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { LoadRamp } from '../../../core/api/training-load.models';
import { rampWarningTexts } from '../training-load.view';

/** The weekly ramp callouts: numbers and flags only, shown only for weeks with a warning. */
@Component({
  selector: 'app-ramp-warning',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (text of texts(); track text) {
      <p class="ramp-callout" role="status">{{ text }}</p>
    }
  `,
  styles: `
    .ramp-callout {
      margin: 0 0 0.75rem;
      padding: 0.6rem 0.9rem;
      border: 1px solid var(--color-worse);
      border-radius: 8px;
      background: var(--color-surface);
      color: var(--color-text);
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class RampWarning {
  readonly ramp = input.required<LoadRamp>();
  protected readonly texts = computed(() => rampWarningTexts(this.ramp()));
}
