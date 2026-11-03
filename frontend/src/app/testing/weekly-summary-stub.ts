import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RunningDashboardPage } from '../features/running-dashboard/running-dashboard-page';
import { WeeklySummaryBell } from '../features/weekly-summary/weekly-summary-bell';

@Component({ selector: 'app-weekly-summary-bell', template: '' })
class WeeklySummaryBellStub {}

/** Swaps the weekly summary bell for an inert element, so page specs don't see its request. */
export function stubWeeklySummaryBell(): void {
  TestBed.overrideComponent(RunningDashboardPage, {
    remove: { imports: [WeeklySummaryBell] },
    add: { imports: [WeeklySummaryBellStub] },
  });
}
