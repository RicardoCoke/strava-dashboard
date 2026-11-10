import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { isEfficiencyFactorResponse } from '../../core/api/efficiency-factor.models';
import { EfficiencyFactorService } from '../../core/api/efficiency-factor.service';
import { sectionLoader } from '../../core/sync/section-loader';
import { formatTwoDecimals } from '../../shared/format/numbers';
import { RingSpinner } from '../../shared/spinner/ring-spinner';
import { EfficiencyFactorChart } from './efficiency-factor-chart/efficiency-factor-chart';
import {
  EfficiencyFactorState,
  NO_CHANGE_NOTE,
  NO_CURRENT_NOTE,
  changeText,
  changeTone,
  currentCaption,
  emptyText,
  footnoteText,
} from './efficiency-factor.view';

/** The "Efficiency factor" card on the running dashboard: its own request, states, tile and chart. */
@Component({
  selector: 'app-efficiency-factor-section',
  imports: [EfficiencyFactorChart, RingSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './efficiency-factor-section.html',
  styleUrl: './efficiency-factor-section.scss',
})
export class EfficiencyFactorSection implements OnInit {
  private readonly service = inject(EfficiencyFactorService);

  protected readonly noCurrentNote = NO_CURRENT_NOTE;
  protected readonly noChangeNote = NO_CHANGE_NOTE;
  protected readonly state = signal<EfficiencyFactorState>({ kind: 'loading' });
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly empty = computed(() => {
    const data = this.data();
    return data ? emptyText(data) : null;
  });
  protected readonly current = computed(() => {
    const current = this.data()?.current;
    return current
      ? { value: formatTwoDecimals(current.efficiency), caption: currentCaption(current) }
      : null;
  });
  protected readonly change = computed(() => {
    const change = this.data()?.change;
    return change ? { text: changeText(change), tone: changeTone(change) } : null;
  });
  protected readonly footnote = computed(() => {
    const data = this.data();
    return data ? footnoteText(data) : [];
  });

  private readonly load = sectionLoader<EfficiencyFactorState>(this.state, {
    fetch: () => this.service.getEfficiencyFactor(),
    loaded: (data) => (isEfficiencyFactorResponse(data) ? { kind: 'loaded', data } : null),
  });

  ngOnInit(): void {
    this.load();
  }

  protected retry(): void {
    this.load();
  }
}
