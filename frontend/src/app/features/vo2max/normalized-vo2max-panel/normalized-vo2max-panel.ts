import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { formatOneDecimal } from '../../../shared/format/numbers';
import { NormalizedVo2maxChart } from '../normalized-vo2max-chart/normalized-vo2max-chart';
import { RacePredictionTiles } from '../race-prediction-tiles/race-prediction-tiles';
import {
  NORMALIZED_NOTE,
  NormalizedVo2maxState,
  basisCaption,
  emptyKind,
  normalizedFootnoteText,
  runs,
} from '../vo2max.view';

/** The "Normalized Vo2" tab's content (feature 016 R10/R12); the section owns loading and retry. */
@Component({
  selector: 'app-normalized-vo2max-panel',
  imports: [NormalizedVo2maxChart, RacePredictionTiles],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './normalized-vo2max-panel.html',
  styleUrl: '../vo2max.scss',
})
export class NormalizedVo2maxPanel {
  readonly state = input.required<NormalizedVo2maxState>();
  readonly retry = output<void>();

  protected readonly note = NORMALIZED_NOTE;
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly empty = computed(() => {
    const data = this.data();
    return data ? emptyKind(data) : null;
  });
  protected readonly current = computed(() => {
    const current = this.data()?.current;
    return current
      ? { value: formatOneDecimal(current.vo2max), caption: basisCaption(current) }
      : null;
  });
  protected readonly footnote = computed(() => {
    const data = this.data();
    return data ? normalizedFootnoteText(data) : [];
  });
  protected readonly pendingText = computed(() => {
    const data = this.data();
    return data ? `Split data is still importing: ${runs(data.runsPendingDetail)} to go.` : '';
  });
  protected readonly invalidMessage = computed(() => {
    const state = this.state();
    return state.kind === 'invalidConfig' ? state.message : '';
  });
}
