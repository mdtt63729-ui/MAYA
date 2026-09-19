/**
 * PlannerAgent
 * PRD Section 5.1: Analyzes user goals, breaks them down into atomic tasks,
 * resolves dependency constraints, and produces execution graphs.
 */

import { BrainAgent } from './AgentRegistry';
import { BrainTask, AgentResult } from '../brainTypes';
import { taskPlanner } from '../planning/TaskPlanner';

export class PlannerAgent implements BrainAgent {
  public readonly type = 'planner';
  public readonly name = 'Planner Agent';
  public readonly description = 'Decomposes complex requests into directed task dependencies and completion criteria.';

  public async execute(task: BrainTask): Promise<AgentResult> {
    const userGoal = String(task.input.userGoal || task.description || task.title);

    try {
      const subtasks = await taskPlanner.plan(userGoal, task.id);
      return {
        success: true,
        data: subtasks,
        confidence: 'HIGH',
        evidence: [`Decomposed goal into ${subtasks.length} atomic subtasks.`],
        nextSuggestedAction: 'Schedule subtasks into execution waves.',
      };
    } catch (err) {
      return {
        success: false,
        data: [],
        confidence: 'LOW',
        errors: [(err as Error).message],
      };
    }
  }
}

export const plannerAgent = new PlannerAgent();
