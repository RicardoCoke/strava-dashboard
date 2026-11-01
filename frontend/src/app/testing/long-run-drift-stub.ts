import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LongRunDriftSection } from '../features/long-run-drift/long-run-drift-section';
import { RunningDashboardPage } from '../features/running-dashboard/running-dashboard-page';

@Component({ selector: 'app-long-run-drift-section', template: '' })
class LongRunDriftSectionStub {}

/** Swaps the long-run drift card for an inert element, so page specs don't see its request. */
export function stubLongRunDriftSection(): void {
  TestBed.overrideComponent(RunningDashboardPage, {
    remove: { imports: [LongRunDriftSection] },
    add: { imports: [LongRunDriftSectionStub] },
  });
}
