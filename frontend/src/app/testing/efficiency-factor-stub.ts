import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { EfficiencyFactorSection } from '../features/efficiency-factor/efficiency-factor-section';
import { RunningDashboardPage } from '../features/running-dashboard/running-dashboard-page';

@Component({ selector: 'app-efficiency-factor-section', template: '' })
class EfficiencyFactorSectionStub {}

/** Swaps the efficiency factor card for an inert element, so page specs don't see its request. */
export function stubEfficiencyFactorSection(): void {
  TestBed.overrideComponent(RunningDashboardPage, {
    remove: { imports: [EfficiencyFactorSection] },
    add: { imports: [EfficiencyFactorSectionStub] },
  });
}
