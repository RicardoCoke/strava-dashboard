import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { isTrainingLoadResponse } from '../../core/api/training-load.models';
import { TrainingLoadService } from '../../core/api/training-load.service';
import { sectionLoader } from '../../core/sync/section-loader';
import { formatShortDate } from '../../shared/format/dates';
import { formatOneDecimal, formatSignedOneDecimal } from '../../shared/format/numbers';
import { RampWarning } from './ramp-warning/ramp-warning';
import { TrainingLoadChart } from './training-load-chart/training-load-chart';
import {
  LoadState,
  emptyText,
  footnoteText,
  isEmpty,
  loadStateFromError,
} from './training-load.view';

/** The "Training load" card under "Marathon build": its own request, states and chart. */
@Component({
  selector: 'app-training-load-section',
  imports: [RampWarning, TrainingLoadChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './training-load-section.html',
  styleUrl: './training-load.scss',
})
export class TrainingLoadSection implements OnInit {
  private readonly service = inject(TrainingLoadService);

  protected readonly state = signal<LoadState>({ kind: 'loading' });
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly empty = computed(() => {
    const data = this.data();
    return data !== null && isEmpty(data);
  });
  protected readonly emptyLines = computed(() => {
    const data = this.data();
    return data ? emptyText(data) : [];
  });
  protected readonly footnote = computed(() => {
    const data = this.data();
    return data ? footnoteText(data) : [];
  });
  protected readonly latest = computed(() => {
    const latest = this.data()?.latest;
    return latest
      ? {
          values: [
            { label: 'Fitness', value: formatOneDecimal(latest.fitness) },
            { label: 'Fatigue', value: formatOneDecimal(latest.fatigue) },
            { label: 'Form', value: formatSignedOneDecimal(latest.form) },
          ],
          caption: `on ${formatShortDate(latest.date)}`,
        }
      : null;
  });
  protected readonly invalidMessage = computed(() => {
    const state = this.state();
    return state.kind === 'invalidConfig' ? state.message : '';
  });

  private readonly load = sectionLoader<LoadState>(this.state, {
    fetch: () => this.service.getTrainingLoad(),
    loaded: (data) => (isTrainingLoadResponse(data) ? { kind: 'loaded', data } : null),
    failed: loadStateFromError,
  });

  ngOnInit(): void {
    this.load();
  }

  protected retry(): void {
    this.load();
  }
}
