/**
 * TaskPlanner
 * PRD Sections 5.1, 6 & 7: Decomposes complex user goals into atomic,
 * dependency-mapped tasks with specific criteria and assigned agents.
 */

import { BrainTask, BrainTaskPriority, AgentType } from '../brainTypes';
import { modelRegistry } from '../models/ModelRegistry';

interface PlannedTaskBlueprint {
  title: string;
  description: string;
  assignedAgent: AgentType;
  priority: BrainTaskPriority;
  dependenciesIndices: number[]; // index references to earlier tasks in the plan
  completionCriteria: string[];
}

export class TaskPlanner {
  /**
   * Plan a set of structured subtasks for a root user goal
   */
  public async plan(userGoal: string, rootTaskId: string): Promise<BrainTask[]> {
    const timestamp = Date.now();
    const model = modelRegistry.getActive();

    const planPrompt = `
You are the Planner Agent for the MJ AI Assistant Brain.
Analyze the user request and decompose it into 2 to 4 structured subtasks with clear dependencies and completion criteria.

User Request: "${userGoal}"

Respond with a JSON array of subtasks matching this exact schema:
[
  {
    "title": "Short title",
    "description": "Clear step description",
    "assignedAgent": "researcher" | "coder" | "executor" | "memory",
    "priority": "NORMAL" | "HIGH" | "CRITICAL",
    "dependenciesIndices": [], // indices (0-based) of tasks in this list that must finish first
    "completionCriteria": ["Criterion 1", "Criterion 2"]
  }
]
`;

    try {
      const blueprints = await model.generateJSON<PlannedTaskBlueprint[]>(planPrompt, {
        temperature: 0.2,
      });

      if (Array.isArray(blueprints) && blueprints.length > 0) {
        // Generate actual task IDs
        const generatedTasks: BrainTask[] = blueprints.map((bp, idx) => {
          const taskId = `${rootTaskId}.${String(idx + 1).padStart(2, '0')}`;
          return {
            id: taskId,
            rootTaskId,
            parentTaskId: rootTaskId,
            title: bp.title || `Subtask ${idx + 1}`,
            description: bp.description || '',
            status: 'QUEUED',
            priority: bp.priority || 'NORMAL',
            dependencies: [], // will map after
            assignedAgent: bp.assignedAgent || 'executor',
            input: { userGoal, stepIndex: idx },
            completionCriteria: bp.completionCriteria || ['Step completed successfully'],
            retryCount: 0,
            maxRetries: 3,
            createdAt: timestamp,
            updatedAt: timestamp,
          };
        });

        // Resolve dependencies from indices
        blueprints.forEach((bp, idx) => {
          if (bp.dependenciesIndices && Array.isArray(bp.dependenciesIndices)) {
            generatedTasks[idx].dependencies = bp.dependenciesIndices
              .filter((depIdx) => depIdx >= 0 && depIdx < generatedTasks.length && depIdx !== idx)
              .map((depIdx) => generatedTasks[depIdx].id);
          }
        });

        return generatedTasks;
      }
    } catch (err) {
      console.warn('[TaskPlanner] LLM planning failed, using deterministic decomposition:', err);
    }

    // Deterministic fallback decomposition (PRD Section 46: Offline safe)
    return this.createDeterministicPlan(userGoal, rootTaskId, timestamp);
  }

  private createDeterministicPlan(goal: string, rootTaskId: string, timestamp: number): BrainTask[] {
    const isCoding = /code|function|component|bug|script|api|typescript|python/i.test(goal);
    const isResearch = /research|explain|difference|compare|what is|how to|find/i.test(goal);

    if (isCoding) {
      const task1: BrainTask = {
        id: `${rootTaskId}.01`,
        rootTaskId,
        parentTaskId: rootTaskId,
        title: 'Analyze Requirements & Architecture',
        description: `Analyze coding requirements for: ${goal}`,
        status: 'QUEUED',
        priority: 'HIGH',
        dependencies: [],
        assignedAgent: 'coder',
        input: { goal },
        completionCriteria: ['Technical requirements identified', 'Architecture outlined'],
        retryCount: 0,
        maxRetries: 3,
        createdAt: timestamp,
        updatedAt: timestamp,
      };

      const task2: BrainTask = {
        id: `${rootTaskId}.02`,
        rootTaskId,
        parentTaskId: rootTaskId,
        title: 'Generate Implementation & Tests',
        description: `Generate production-ready code for: ${goal}`,
        status: 'QUEUED',
        priority: 'HIGH',
        dependencies: [task1.id],
        assignedAgent: 'coder',
        input: { goal },
        completionCriteria: ['Working implementation generated', 'Validation checks completed'],
        retryCount: 0,
        maxRetries: 3,
        createdAt: timestamp,
        updatedAt: timestamp,
      };

      return [task1, task2];
    }

    if (isResearch) {
      const task1: BrainTask = {
        id: `${rootTaskId}.01`,
        rootTaskId,
        parentTaskId: rootTaskId,
        title: 'Information Gathering & Facts Research',
        description: `Research relevant information for: ${goal}`,
        status: 'QUEUED',
        priority: 'NORMAL',
        dependencies: [],
        assignedAgent: 'researcher',
        input: { goal },
        completionCriteria: ['Verified facts compiled', 'Objective sources checked'],
        retryCount: 0,
        maxRetries: 3,
        createdAt: timestamp,
        updatedAt: timestamp,
      };

      const task2: BrainTask = {
        id: `${rootTaskId}.02`,
        rootTaskId,
        parentTaskId: rootTaskId,
        title: 'Synthesize & Verify Structured Findings',
        description: `Synthesize research into verified final plan/summary for: ${goal}`,
        status: 'QUEUED',
        priority: 'NORMAL',
        dependencies: [task1.id],
        assignedAgent: 'executor',
        input: { goal },
        completionCriteria: ['Comprehensive findings synthesized', 'Internal consistency verified'],
        retryCount: 0,
        maxRetries: 3,
        createdAt: timestamp,
        updatedAt: timestamp,
      };

      return [task1, task2];
    }

    // Default 2-step structured plan
    const task1: BrainTask = {
      id: `${rootTaskId}.01`,
      rootTaskId,
      parentTaskId: rootTaskId,
      title: 'Analyze Goal & Context',
      description: `Understand requirements and context for: ${goal}`,
      status: 'QUEUED',
      priority: 'NORMAL',
      dependencies: [],
      assignedAgent: 'researcher',
      input: { goal },
      completionCriteria: ['Context and constraints understood'],
      retryCount: 0,
      maxRetries: 3,
      createdAt: timestamp,
      updatedAt: timestamp,
    };

    const task2: BrainTask = {
      id: `${rootTaskId}.02`,
      rootTaskId,
      parentTaskId: rootTaskId,
      title: 'Execute & Formulate Verified Solution',
      description: `Formulate verified output for: ${goal}`,
      status: 'QUEUED',
      priority: 'NORMAL',
      dependencies: [task1.id],
      assignedAgent: 'executor',
      input: { goal },
      completionCriteria: ['Complete solution formulated'],
      retryCount: 0,
      maxRetries: 3,
      createdAt: timestamp,
      updatedAt: timestamp,
    };

    return [task1, task2];
  }
}

export const taskPlanner = new TaskPlanner();
