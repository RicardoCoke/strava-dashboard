import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { BuildProgress } from '../../../core/api/training-plan.models';

@Component({
  selector: 'app-build-progress',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="label">{{ progress().label }}</p>
    <div
      class="track"
      role="progressbar"
      aria-label="Build progress"
      aria-valuemin="0"
      aria-valuemax="100"
      [attr.aria-valuenow]="progress().percentComplete"
      [attr.aria-valuetext]="progress().label"
    >
      <div class="fill" [style.width.%]="progress().percentComplete"></div>
    </div>
  `,
  styles: `
    :host {
      display: block;
      margin-bottom: 1.25rem;
    }

    .label {
      margin: 0 0 0.5rem;
      font-size: 1.25rem;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      color: var(--color-text);
    }

    .track {
      height: 0.6rem;
      border-radius: 999px;
      background: var(--color-surface-raised);
      overflow: hidden;
    }

    .fill {
      height: 100%;
      border-radius: 999px;
      background: var(--color-accent);
    }
  `,
})
export class BuildProgressBar {
  readonly progress = input.required<BuildProgress>();
}
