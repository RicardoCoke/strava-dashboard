import { CUSTOM_ELEMENTS_SCHEMA, Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { EfficiencyFactorSection } from '../features/efficiency-factor/efficiency-factor-section';
import { LongRunDriftSection } from '../features/long-run-drift/long-run-drift-section';
import { MarathonPaceSection } from '../features/marathon-pace/marathon-pace-section/marathon-pace-section';
import { RaceDayFormSection } from '../features/race-day-form/race-day-form-section';
import { RingsSection } from '../features/rings/rings-section';
import { RunningDashboardPage } from '../features/running-dashboard/running-dashboard-page';
import { TrainingLoadSection } from '../features/training-load/training-load-section';
import { Vo2maxSection } from '../features/vo2max/vo2max-section';
import { WeeklySummaryBell } from '../features/weekly-summary/weekly-summary-bell';
import { WellnessSection } from '../features/wellness/wellness-section';

/**
 * Takes a card off the dashboard page, so page specs don't see its request. Its tag stays in the template as an
 * unknown, inert element.
 */
function stubSection(section: Type<unknown>): void {
  TestBed.overrideComponent(RunningDashboardPage, {
    remove: { imports: [section] },
    add: { schemas: [CUSTOM_ELEMENTS_SCHEMA] },
  });
}

export const stubEfficiencyFactorSection = (): void => stubSection(EfficiencyFactorSection);
export const stubLongRunDriftSection = (): void => stubSection(LongRunDriftSection);
export const stubMarathonPaceSection = (): void => stubSection(MarathonPaceSection);
export const stubRaceDayFormSection = (): void => stubSection(RaceDayFormSection);
export const stubRingsSection = (): void => stubSection(RingsSection);
export const stubTrainingLoadSection = (): void => stubSection(TrainingLoadSection);
export const stubVo2maxSection = (): void => stubSection(Vo2maxSection);
export const stubWeeklySummaryBell = (): void => stubSection(WeeklySummaryBell);
export const stubWellnessSection = (): void => stubSection(WellnessSection);
