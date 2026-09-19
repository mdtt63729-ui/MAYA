/**
 * TaskGraph
 * PRD Sections 7 & 8: Directed Dependency Graph for multi-step task execution.
 * Detects circular dependencies and ensures valid execution topology.
 */

import { BrainTask } from '../brainTypes';

export class TaskGraph {
  private tasks: Map<string, BrainTask> = new Map();
  private adjacencyList: Map<string, Set<string>> = new Map(); // taskId -> set of dependent taskIds

  constructor(initialTasks: BrainTask[] = []) {
    initialTasks.forEach((t) => this.addTask(t));
  }

  public addTask(task: BrainTask): void {
    this.tasks.set(task.id, task);
    if (!this.adjacencyList.has(task.id)) {
      this.adjacencyList.set(task.id, new Set());
    }

    // Register reverse dependencies
    task.dependencies.forEach((depId) => {
      if (!this.adjacencyList.has(depId)) {
        this.adjacencyList.set(depId, new Set());
      }
      this.adjacencyList.get(depId)!.add(task.id);
    });
  }

  public getTask(id: string): BrainTask | undefined {
    return this.tasks.get(id);
  }

  public getAllTasks(): BrainTask[] {
    return Array.from(this.tasks.values());
  }

  public getDependents(taskId: string): BrainTask[] {
    const dependentIds = this.adjacencyList.get(taskId) || new Set();
    return Array.from(dependentIds)
      .map((id) => this.tasks.get(id))
      .filter((t): t is BrainTask => t !== undefined);
  }

  /**
   * Cycle Detection using Tarjan's / DFS Three-Color Algorithm (PRD Section 8)
   */
  public hasCycle(): boolean {
    const visited: Map<string, 'UNVISITED' | 'VISITING' | 'VISITED'> = new Map();
    this.tasks.forEach((_, id) => visited.set(id, 'UNVISITED'));

    const dfs = (id: string): boolean => {
      visited.set(id, 'VISITING');
      const dependents = this.adjacencyList.get(id) || new Set();

      for (const nextId of dependents) {
        const state = visited.get(nextId);
        if (state === 'VISITING') {
          return true; // Cycle detected
        }
        if (state === 'UNVISITED') {
          if (dfs(nextId)) return true;
        }
      }

      visited.set(id, 'VISITED');
      return false;
    };

    for (const id of this.tasks.keys()) {
      if (visited.get(id) === 'UNVISITED') {
        if (dfs(id)) return true;
      }
    }

    return false;
  }
}
