import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TargetPace } from '../../../core/api/training-plan.models';
import { paceRows } from '../plan-view';

@Component({
  selector: 'app-target-paces',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (rows().length > 0) {
      <div class="panel paces">
        <h3 class="title">Target paces</h3>
        <ul class="pace-list">
          @for (row of rows(); track row.key) {
            <li class="pace" [attr.data-pace]="row.key">
              <span class="pace-label">{{ row.label }}</span>
              <span class="pace-value">{{ row.value }}</span>
              @if (row.target) {
                <span class="pace-target">{{ row.target }}</span>
              }
            </li>
          }
        </ul>
      </div>
    }
    @if (notes().length > 0) {
      <div class="panel notes">
        <h3 class="title">Plan notes</h3>
        <ul class="note-list">
          @for (note of notes(); track $index) {
            <li>{{ note }}</li>
          }
        </ul>
      </div>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 1rem;
      margin-bottom: 1.25rem;
    }

    .title {
      margin: 0 0 0.5rem;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--color-text-muted);
    }

    .pace-list,
    .note-list {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .pace-list {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
    }

    .pace {
      display: flex;
      flex-direction: column;
      min-width: 7rem;
      padding: 0.6rem 0.8rem;
      border-radius: 8px;
      background: var(--color-surface-raised);
    }

    .pace-label {
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--color-text-muted);
    }

    .pace-value {
      font-size: 1.05rem;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      color: var(--color-text);
    }

    .pace-target {
      font-size: 0.8rem;
      color: var(--color-text-muted);
    }

    .note-list li {
      padding: 0.15rem 0;
      font-size: 0.9rem;
      color: var(--color-text);
    }
  `,
})
export class TargetPaces {
  readonly paces = input.required<TargetPace[]>();
  readonly notes = input.required<string[]>();
  protected readonly rows = computed(() => paceRows(this.paces()));
}
