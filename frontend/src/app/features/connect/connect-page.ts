import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Fixed texts per error code; the raw query value is never rendered. */
const ERROR_MESSAGES: Record<string, string> = {
  access_denied: 'You declined access on Strava. Connect again to see your dashboard.',
  scope_missing:
    'The dashboard needs to read your activities, including private ones. Please allow that on Strava.',
  state_mismatch: 'The sign-in expired or did not match. Please try again.',
  token_exchange_failed: "Strava didn't complete the sign-in. Please try again.",
};
const GENERIC_ERROR = 'Connecting to Strava failed. Please try again.';
const REAUTH_MESSAGE = 'Your Strava connection expired. Please connect again.';

export function connectMessage(
  error: string | undefined,
  reason: string | undefined,
): string | null {
  if (error) {
    return Object.hasOwn(ERROR_MESSAGES, error) ? ERROR_MESSAGES[error] : GENERIC_ERROR;
  }
  return reason === 'reauth' ? REAUTH_MESSAGE : null;
}

@Component({
  selector: 'app-connect-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card connect-card">
      <h1>Connect with Strava</h1>
      <p class="intro">
        Connect your Strava account to see your weekly distance, heart-rate trend and workout pace.
      </p>
      @if (message(); as text) {
        <p class="message notice" role="alert">{{ text }}</p>
      }
      <a class="connect" href="/auth/strava/login">Connect with Strava</a>
    </section>
  `,
  styles: `
    .connect-card {
      max-width: 520px;
      margin: 3rem auto 0;
      padding: 2rem;
    }
    .intro {
      margin: 0 0 1.5rem;
      color: var(--color-text-muted);
    }
    .connect {
      display: inline-block;
      padding: 0.7rem 1.4rem;
      border-radius: 999px;
      background: var(--color-accent);
      color: var(--color-bg);
      font-weight: 700;
      text-decoration: none;
    }
  `,
})
export class ConnectPage {
  /** Bound from the `?error=` query param. */
  readonly error = input<string>();
  /** Bound from the `?reason=` query param. */
  readonly reason = input<string>();

  protected readonly message = computed(() => connectMessage(this.error(), this.reason()));
}
