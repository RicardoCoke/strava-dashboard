import { trainingPlan } from '../../testing/training-plan-fixtures';
import { isTrainingPlanResponse } from './plan-view';

describe('isTrainingPlanResponse', () => {
  it('010 R10 - plan shape check accepts body without notes', () => {
    const plan = trainingPlan();
    expect('notes' in plan).toBe(false);
    expect(isTrainingPlanResponse(plan)).toBe(true);
    expect(isTrainingPlanResponse({ ...plan, weeks: 'x' })).toBe(false);
  });
});
