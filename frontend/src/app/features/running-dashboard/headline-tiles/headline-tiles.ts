import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RunningDashboardResponse } from '../../../core/api/running-dashboard.models';
import { buildHeadlineTiles } from './headline-tiles.model';

@Component({
  selector: 'app-headline-tiles',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tiles">
      @for (tile of tiles(); track tile.id) {
        <article class="card tile" [attr.data-tile]="tile.id">
          <h2 class="label">{{ tile.label }}</h2>
          <p class="headline">
            <span class="value" [class]="'tone-' + tile.tone">{{ tile.value }}</span>
            @if (tile.unit) {
              <span class="unit">{{ tile.unit }}</span>
            }
          </p>
          <p class="detail">{{ tile.detail }}</p>
        </article>
      }
    </div>
  `,
  styles: `
    .tiles {
      display: grid;
      grid-template-columns: 1fr;
      gap: 1rem;
      margin-bottom: 1rem;
    }

    @media (min-width: 720px) {
      .tiles {
        grid-template-columns: repeat(3, minmax(0, 1fr));
      }
    }

    .label {
      margin: 0;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--color-text-muted);
    }

    .headline {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.4rem;
      margin: 0.5rem 0 0.25rem;
    }

    .value {
      font-size: 2.25rem;
      font-weight: 700;
      line-height: 1.1;
      font-variant-numeric: tabular-nums;
    }

    .unit {
      font-size: 0.95rem;
      font-weight: 600;
      color: var(--color-text-muted);
    }

    .detail {
      margin: 0;
      font-size: 0.85rem;
      color: var(--color-text-muted);
    }
  `,
})
export class HeadlineTiles {
  readonly dashboard = input.required<RunningDashboardResponse>();
  protected readonly tiles = computed(() => buildHeadlineTiles(this.dashboard()));
}
