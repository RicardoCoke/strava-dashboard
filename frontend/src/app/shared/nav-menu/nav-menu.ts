import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** The hamburger button and slide-over panel that switch between Performance and Recovery (feature 023). */
@Component({
  selector: 'app-nav-menu',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './nav-menu.html',
  styleUrl: './nav-menu.scss',
  host: { '(document:keydown.escape)': 'close()' },
})
export class NavMenu {
  private readonly injector = inject(Injector);
  private readonly button = viewChild.required<ElementRef<HTMLButtonElement>>('menuButton');
  private readonly firstLink = viewChild<ElementRef<HTMLAnchorElement>>('firstLink');

  protected readonly open = signal(false);

  protected show(): void {
    this.open.set(true);
    afterNextRender(() => this.firstLink()?.nativeElement.focus(), { injector: this.injector });
  }

  /** Keeps Tab inside the modal panel: wraps from its last control to its first and back. */
  protected trapTab(event: Event): void {
    const panel = event.currentTarget as HTMLElement;
    const controls = Array.from(panel.querySelectorAll<HTMLElement>('button, a[href]'));
    const first = controls[0];
    const last = controls[controls.length - 1];
    const shift = (event as KeyboardEvent).shiftKey;
    if (!shift && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    } else if (shift && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    }
  }

  protected close(): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    this.button().nativeElement.focus();
  }
}
