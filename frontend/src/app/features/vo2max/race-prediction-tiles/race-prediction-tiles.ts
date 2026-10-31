import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RacePrediction } from '../../../core/api/vo2max.models';
import { formatRaceTime } from '../../../shared/format/duration';

/** Four race-time tiles: 5K, 10K, half marathon and marathon (with its note). */
@Component({
  selector: 'app-race-prediction-tiles',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="race-tiles">
      @for (tile of tiles(); track tile.key) {
        <article class="race-tile" [attr.data-race]="tile.key">
          <h3 class="race-label">{{ tile.label }}</h3>
          <p class="race-time">{{ tile.time }}</p>
          @if (tile.note) {
            <p class="race-note">{{ tile.note }}</p>
          }
        </article>
      }
    </div>
  `,
  styles: `
    .race-tiles {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 0.75rem;
      margin: 0 0 1rem;
    }

    @media (min-width: 720px) {
      .race-tiles {
        grid-template-columns: repeat(4, minmax(0, 1fr));
      }
    }

    .race-tile {
      padding: 0.75rem 0.9rem;
      border: 1px solid var(--color-border);
      border-radius: 8px;
      background: var(--color-surface-raised);
    }

    .race-label {
      margin: 0;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--color-text-muted);
    }

    .race-time {
      margin: 0.25rem 0 0;
      font-size: 1.4rem;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      color: var(--color-text);
    }

    .race-note {
      margin: 0.35rem 0 0;
      font-size: 0.75rem;
      color: var(--color-text-muted);
    }
  `,
})
export class RacePredictionTiles {
  readonly predictions = input.required<RacePrediction[]>();
  protected readonly tiles = computed(() =>
    this.predictions().map((prediction) => ({
      key: prediction.key,
      label: prediction.label,
      time: formatRaceTime(prediction.timeSeconds),
      note: prediction.note,
    })),
  );
}
