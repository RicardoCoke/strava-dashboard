import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  computed,
  input,
  output,
  viewChild,
} from '@angular/core';
import { WeeklySummary } from '../../../core/api/weekly-summary.models';
import { formatDayMonth, formatDayMonthYear } from '../../../shared/format/dates';
import { RingSpinner } from '../../../shared/spinner/ring-spinner';

export type PanelState = 'loading' | 'error' | 'loaded';

/** The right-anchored weekly summary overlay: one week's notes, ‹ › between weeks and a ✕. */
@Component({
  selector: 'app-weekly-summary-panel',
  imports: [RingSpinner],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './weekly-summary-panel.html',
  styleUrl: './weekly-summary-panel.scss',
})
export class WeeklySummaryPanel {
  readonly state = input.required<PanelState>();
  readonly summary = input<WeeklySummary | null>(null);
  readonly hasOlder = input(false);
  readonly hasNewer = input(false);
  readonly closed = output();
  readonly older = output();
  readonly newer = output();
  readonly retry = output();

  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  protected readonly weekLabel = computed(() => {
    const summary = this.summary();
    return summary
      ? `${formatDayMonth(summary.weekStart)} – ${formatDayMonthYear(summary.weekEnd)}`
      : 'Weekly summary';
  });

  constructor() {
    afterNextRender(() => this.heading().nativeElement.focus());
  }
}
