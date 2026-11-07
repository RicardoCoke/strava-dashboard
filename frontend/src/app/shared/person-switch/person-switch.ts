import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  output,
  viewChild,
} from '@angular/core';
import { PersonStore } from '../../core/person/person.store';

/** The Ricardo / Marina slide (feature 019). */
@Component({
  selector: 'app-person-switch',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="name" [class.selected]="!marina()">Ricardo</span>
    <button
      #control
      type="button"
      role="switch"
      class="track"
      aria-label="Show dashboard for Marina"
      [attr.aria-checked]="marina()"
      (click)="flip()"
    >
      <span class="thumb" [class.on]="marina()"></span>
    </button>
    <span class="name" [class.selected]="marina()">Marina</span>
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
    }
    .name {
      color: var(--color-text-muted);
      font-size: 0.85rem;
    }
    .name.selected {
      color: var(--color-text);
      font-weight: 700;
    }
    .track {
      position: relative;
      width: 2.25rem;
      height: 1.25rem;
      padding: 0;
      border: 1px solid var(--color-border);
      border-radius: 999px;
      background: var(--color-surface);
      cursor: pointer;
    }
    .track:focus-visible {
      outline: 2px solid var(--color-accent);
      outline-offset: 2px;
    }
    .thumb {
      position: absolute;
      top: 0.125rem;
      left: 0.125rem;
      width: 0.875rem;
      height: 0.875rem;
      border-radius: 50%;
      background: var(--color-accent);
    }
    .thumb.on {
      left: auto;
      right: 0.125rem;
    }
  `,
})
export class PersonSwitch {
  private readonly store = inject(PersonStore);
  private readonly control = viewChild.required<ElementRef<HTMLButtonElement>>('control');

  /** After a flip, for hosts that must do more than show the new person. */
  readonly changed = output<void>();

  protected readonly marina = computed(() => this.store.person() === 'marina');

  constructor() {
    afterNextRender(() => {
      if (this.store.takeFocusRequest()) {
        this.control().nativeElement.focus();
      }
    });
  }

  protected flip(): void {
    this.store.select(this.marina() ? 'ricardo' : 'marina');
    this.changed.emit();
  }
}
