import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RunningDashboardPage } from '../features/running-dashboard/running-dashboard-page';
import { TrainingLoadSection } from '../features/training-load/training-load-section';

@Component({ selector: 'app-training-load-section', template: '' })
class TrainingLoadSectionStub {}

/** Swaps the training load card for an inert element, so page specs don't see its request. */
export function stubTrainingLoadSection(): void {
  TestBed.overrideComponent(RunningDashboardPage, {
    remove: { imports: [TrainingLoadSection] },
    add: { imports: [TrainingLoadSectionStub] },
  });
}
