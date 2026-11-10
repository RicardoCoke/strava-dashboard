import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { isRaceDayFormResponse } from '../../core/api/race-day-form.models';
import { RaceDayFormService } from '../../core/api/race-day-form.service';
import { sectionLoader } from '../../core/sync/section-loader';
import { RaceDayFormChart } from './race-day-form-chart/race-day-form-chart';
import {
  BAND_CAPTION,
  NOT_ENOUGH_RUNS_TEXT,
  RaceDayFormState,
  notConfiguredText,
  raceDayFormStateFromError,
  subtNoteText,
  tileCaption,
} from './race-day-form.view';

/** The "Race-day form" card under "Training load": its own request, states, tile and chart (feature 011). */
@Component({
  selector: 'app-race-day-form-section',
  imports: [RaceDayFormChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './race-day-form-section.html',
  styleUrl: './race-day-form.scss',
})
export class RaceDayFormSection implements OnInit {
  private readonly service = inject(RaceDayFormService);

  protected readonly notEnoughRunsText = NOT_ENOUGH_RUNS_TEXT;
  protected readonly bandCaption = BAND_CAPTION;
  protected readonly state = signal<RaceDayFormState>({ kind: 'loading' });
  protected readonly data = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.data : null;
  });
  protected readonly caption = computed(() => {
    const raceDay = this.data()?.raceDay;
    return raceDay ? tileCaption(raceDay) : '';
  });
  protected readonly subtNote = computed(() => {
    const data = this.data();
    return data ? subtNoteText(data) : null;
  });
  protected readonly notConfiguredMessage = computed(() => {
    const state = this.state();
    return state.kind === 'notConfigured' ? notConfiguredText(state.missing) : '';
  });
  protected readonly invalidMessage = computed(() => {
    const state = this.state();
    return state.kind === 'invalidConfig' ? state.message : '';
  });

  private readonly load = sectionLoader<RaceDayFormState>(this.state, {
    fetch: () => this.service.getRaceDayForm(),
    loaded: (data) => (isRaceDayFormResponse(data) ? { kind: 'loaded', data } : null),
    failed: raceDayFormStateFromError,
  });

  ngOnInit(): void {
    this.load();
  }

  protected retry(): void {
    this.load();
  }
}
