/**
 * DependencyResolver
 * PRD Sections 8, 9 & 10: Resolves execution order, topological sort,
 * and parallel execution waves for tasks whose dependencies are met.
 */

import { BrainTask } from '../brainTypes';
import { TaskGraph } from './TaskGraph';

export class DependencyResolver {
  /**
   * Return all tasks that are currently READY to run
   * (i.e. status is 'READY' or 'QUEUED' and all dependencies have completed successfully)
   */
  public static getReadyTasks(tasks: BrainTask[]): BrainTask[] {
    const completedIds = new Set(
      tasks.filter((t) => t.status === 'COMPLETED').map((t) => t.id)
    );

    return tasks.filter((t) => {
      if (t.status !== 'QUEUED' && t.status !== 'READY') return false;

      // Check if all prerequisites are satisfied
      const allDepsMet = t.dependencies.every((depId) => completedIds.has(depId));
      return allDepsMet;
    });
  }

  /**
   * Decompose the task graph into parallel execution waves
   * Wave 0: Tasks with no dependencies
   * Wave 1: Tasks depending only on Wave 0, etc.
   */
  public static computeExecutionWaves(graph: TaskGraph): BrainTask[][] {
    if (graph.hasCycle()) {
      throw new Error('Cannot compute execution waves: Dependency graph contains a cycle.');
    }

    const allTasks = graph.getAllTasks();
    const resolvedIds = new Set<string>();
    const waves: BrainTask[][] = [];

    const remaining = new Set(allTasks.map((t) => t.id));

    while (remaining.size > 0) {
      const currentWave: BrainTask[] = [];

      for (const id of remaining) {
        const task = graph.getTask(id)!;
        const canRun = task.dependencies.every((d) => resolvedIds.has(d));
        if (canRun) {
          currentWave.push(task);
        }
      }

      if (currentWave.length === 0) {
        // Deadlock or missing dependency
        break;
      }

      currentWave.forEach((t) => {
        resolvedIds.add(t.id);
        remaining.delete(t.id);
      });

      waves.push(currentWave);
    }

    return waves;
  }
}
