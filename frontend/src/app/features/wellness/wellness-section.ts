import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { isWellnessResponse } from '../../core/api/wellness.models';
import { WellnessService } from '../../core/api/wellness.service';
import { PersonStore } from '../../core/person/person.store';
import { sectionLoader } from '../../core/sync/section-loader';
import { WellnessChart } from './wellness-chart/wellness-chart';
import { WellnessLatest } from './wellness-latest/wellness-latest';
import {
  WellnessState,
  emptyText,
  latestTiles,
  staleText,
  wellnessStateFromError,
} from './wellness.view';

/** The "Resting HR & HRV" card under "Training load" (feature 015): its own request, states, tiles and chart. */
@Component({
  selector: 'app-wellness-section',
  imports: [WellnessChart, WellnessLatest],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './wellness-section.html',
  styleUrl: './wellness-section.scss',
})
export class WellnessSection implements OnInit {
  private readonly service = inject(WellnessService);

  private readonly person = inject(PersonStore).person;

  protected readonly state = signal<WellnessState>({ kind: 'loading' });
  /** Marina may have no Garmin at all, so the card goes instead of asking for a key (019 R11). */
  protected readonly hidden = computed(
    () => this.state().kind === 'notConfigured' && this.person() === 'marina',
  );
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly empty = computed(() => this.data()?.daysWithData === 0);
  protected readonly emptyLine = computed(() => {
    const data = this.data();
    return data ? emptyText(data.source) : '';
  });
  protected readonly staleLine = computed(() => {
    const data = this.data();
    return data ? staleText(data.source) : null;
  });
  protected readonly tiles = computed(() => {
    const data = this.data();
    return data ? latestTiles(data) : [];
  });

  private readonly load = sectionLoader<WellnessState>(this.state, {
    fetch: () => this.service.getWellness(),
    loaded: (data) => (isWellnessResponse(data) ? { kind: 'loaded', data } : null),
    failed: wellnessStateFromError,
  });

  ngOnInit(): void {
    this.load();
  }

  protected retry(): void {
    this.load();
  }
}
