import { ChangeDetectionStrategy, Component } from '@angular/core';

/** A glowing accent ring whose arc fills and turns. Decorative: the surrounding text carries the meaning. */
@Component({
  selector: 'app-ring-spinner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg class="ring" viewBox="0 0 64 64" width="64" height="64" aria-hidden="true">
      <circle class="track" cx="32" cy="32" r="26" />
      <circle class="arc" cx="32" cy="32" r="26" />
    </svg>
  `,
  styles: `
    :host {
      display: inline-block;
      width: 64px;
      height: 64px;
    }

    .ring {
      display: block;
    }

    circle {
      fill: none;
      stroke-width: 6;
    }

    .track {
      stroke: var(--color-grid);
    }

    /* Circumference 2π·26 ≈ 163.4; the arc grows from ~10 % to ~75 % of it and back. */
    .arc {
      stroke: var(--color-accent);
      stroke-linecap: round;
      stroke-dasharray: 16 163.4;
      transform-origin: 32px 32px;
      filter: drop-shadow(0 0 4px var(--color-accent));
      animation:
        ring-turn 1.4s linear infinite,
        ring-fill 1.4s ease-in-out infinite;
    }

    @keyframes ring-turn {
      to {
        transform: rotate(360deg);
      }
    }

    @keyframes ring-fill {
      0% {
        stroke-dasharray: 16 163.4;
        stroke-dashoffset: 0;
      }
      50% {
        stroke-dasharray: 122 163.4;
        stroke-dashoffset: -20;
      }
      100% {
        stroke-dasharray: 16 163.4;
        stroke-dashoffset: -147;
      }
    }
  `,
})
export class RingSpinner {}
