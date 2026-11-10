import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { isLongRunDriftResponse } from '../../core/api/long-run-drift.models';
import { LongRunDriftService } from '../../core/api/long-run-drift.service';
import { sectionLoader } from '../../core/sync/section-loader';
import { LongRunDriftChart } from './long-run-drift-chart/long-run-drift-chart';
import {
  LongRunDriftState,
  emptyKind,
  excludedText,
  headerTexts,
  legendText,
  pendingText,
} from './long-run-drift.view';

/** The "Long-run HR drift" card under "Heart rate by run type": its own request, states and chart. */
@Component({
  selector: 'app-long-run-drift-section',
  imports: [LongRunDriftChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './long-run-drift-section.html',
  styleUrl: './long-run-drift-section.scss',
})
export class LongRunDriftSection implements OnInit {
  private readonly service = inject(LongRunDriftService);

  protected readonly state = signal<LongRunDriftState>({ kind: 'loading' });
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly empty = computed(() => {
    const data = this.data();
    return data ? emptyKind(data) : null;
  });
  protected readonly header = computed(() => {
    const data = this.data();
    return data ? headerTexts(data) : [];
  });
  protected readonly excluded = computed(() => {
    const data = this.data();
    return data ? excludedText(data.excluded) : null;
  });
  protected readonly pending = computed(() => {
    const data = this.data();
    return data ? pendingText(data.pendingCount) : null;
  });
  protected readonly legend = computed(() => {
    const data = this.data();
    return data ? legendText(data.thresholds) : '';
  });

  private readonly load = sectionLoader<LongRunDriftState>(this.state, {
    fetch: () => this.service.getLongRunDrift(),
    loaded: (data) => (isLongRunDriftResponse(data) ? { kind: 'loaded', data } : null),
  });

  ngOnInit(): void {
    this.load();
  }

  protected retry(): void {
    this.load();
  }
}
