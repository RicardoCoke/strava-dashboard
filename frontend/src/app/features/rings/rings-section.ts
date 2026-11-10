import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { isRingsResponse } from '../../core/api/rings.models';
import { RingsService } from '../../core/api/rings.service';
import { sectionLoader } from '../../core/sync/section-loader';
import { LOADING_RINGS, RingView, ringViews } from './rings.view';

type RingsState = { kind: 'loading' } | { kind: 'error' } | { kind: 'loaded'; rings: RingView[] };

/** Circumference of the r = 45 ring in the 100 × 100 viewBox. */
const CIRCUMFERENCE = 2 * Math.PI * 45;

/** Whoop-style Sleep, Recovery and Strain rings at the top of the dashboard (feature 020). */
@Component({
  selector: 'app-rings-section',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './rings-section.html',
  styleUrl: './rings-section.scss',
})
export class RingsSection implements OnInit {
  private readonly service = inject(RingsService);

  protected readonly circumference = CIRCUMFERENCE;
  protected readonly state = signal<RingsState>({ kind: 'loading' });
  protected readonly rings = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.rings : LOADING_RINGS;
  });

  private readonly load = sectionLoader<RingsState>(this.state, {
    fetch: () => this.service.getRings(),
    loaded: (body) => (isRingsResponse(body) ? { kind: 'loaded', rings: ringViews(body) } : null),
  });

  ngOnInit(): void {
    this.load();
  }

  protected retry(): void {
    this.load();
  }

  protected dashOffset(fill: number): number {
    return CIRCUMFERENCE * (1 - fill);
  }
}
