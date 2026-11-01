import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { WellnessTile } from '../wellness.view';

/** The two "latest" tiles; a flag shows as text and in the warning colour. */
@Component({
  selector: 'app-wellness-latest',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tiles">
      @for (tile of tiles(); track tile.label) {
        <div class="tile" [class.flagged]="tile.flag !== null">
          <span class="tile-label">{{ tile.label }}</span>
          @if (tile.value; as value) {
            <span class="tile-value">{{ value }}</span>
            @if (tile.flag; as flag) {
              <span class="tile-flag">{{ flag }}</span>
            }
            @if (tile.delta; as delta) {
              <span class="tile-delta">{{ delta }}</span>
            }
            <span class="tile-caption">{{ tile.caption }}</span>
          } @else {
            <span class="tile-none">No reading</span>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .tiles {
      display: grid;
      grid-template-columns: 1fr;
      gap: 0.75rem;
      margin-bottom: 1rem;
    }

    @media (min-width: 768px) {
      .tiles {
        grid-template-columns: 1fr 1fr;
      }
    }

    .tile {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
      padding: 0.75rem 1rem;
      border: 1px solid var(--color-border);
      border-radius: 8px;
      background: var(--color-surface-raised);
    }

    .tile.flagged {
      border-color: var(--color-worse);
    }

    .tile-label {
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--color-text-muted);
    }

    .tile-value {
      font-size: 1.5rem;
      font-weight: 700;
      line-height: 1.2;
      font-variant-numeric: tabular-nums;
      color: var(--color-text);
    }

    .tile-flag {
      font-size: 0.85rem;
      font-weight: 600;
      color: var(--color-worse);
    }

    .tile-delta,
    .tile-caption,
    .tile-none {
      font-size: 0.85rem;
      color: var(--color-text-muted);
    }
  `,
})
export class WellnessLatest {
  readonly tiles = input.required<WellnessTile[]>();
}
