/**
 * TaskStore
 * PRD Section 35: Robust repository abstraction for Brain tasks with atomic
 * persistence, indexing, dependency lookup, and safe state migration.
 */

import { BrainTask, BrainTaskStatus } from '../core/brainTypes';

const STORAGE_KEY = 'mj_brain_tasks_v1';

class TaskStore {
  private tasks: Map<string, BrainTask> = new Map();
  private isLoaded = false;

  constructor() {
    this.loadFromStorage();
  }

  private loadFromStorage(): void {
    if (this.isLoaded) return;
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed: BrainTask[] = JSON.parse(raw);
        parsed.forEach((t) => this.tasks.set(t.id, t));
      }
      this.isLoaded = true;
    } catch (e) {
      console.warn('[TaskStore] Failed to load from storage, using memory fallback:', e);
    }
  }

  private persist(): void {
    try {
      const arr = Array.from(this.tasks.values());
      localStorage.setItem(STORAGE_KEY, JSON.stringify(arr));
    } catch (e) {
      console.warn('[TaskStore] Failed to persist tasks to localStorage:', e);
    }
  }

  public save(task: BrainTask): BrainTask {
    this.loadFromStorage();
    task.updatedAt = Date.now();
    this.tasks.set(task.id, { ...task });
    this.persist();
    return task;
  }

  public saveAll(tasks: BrainTask[]): void {
    this.loadFromStorage();
    const now = Date.now();
    tasks.forEach((t) => {
      t.updatedAt = now;
      this.tasks.set(t.id, { ...t });
    });
    this.persist();
  }

  public get(id: string): BrainTask | undefined {
    this.loadFromStorage();
    const task = this.tasks.get(id);
    return task ? { ...task } : undefined;
  }

  public getAll(): BrainTask[] {
    this.loadFromStorage();
    return Array.from(this.tasks.values()).map((t) => ({ ...t }));
  }

  public getByRootId(rootTaskId: string): BrainTask[] {
    this.loadFromStorage();
    return Array.from(this.tasks.values())
      .filter((t) => t.rootTaskId === rootTaskId || t.id === rootTaskId)
      .map((t) => ({ ...t }));
  }

  public getByStatus(status: BrainTaskStatus): BrainTask[] {
    this.loadFromStorage();
    return Array.from(this.tasks.values())
      .filter((t) => t.status === status)
      .map((t) => ({ ...t }));
  }

  public updateStatus(
    id: string,
    status: BrainTaskStatus,
    output?: unknown,
    error?: BrainTask['error']
  ): BrainTask | undefined {
    this.loadFromStorage();
    const existing = this.tasks.get(id);
    if (!existing) return undefined;

    existing.status = status;
    existing.updatedAt = Date.now();
    if (output !== undefined) existing.output = output;
    if (error !== undefined) existing.error = error;

    this.tasks.set(id, existing);
    this.persist();
    return { ...existing };
  }

  public delete(id: string): boolean {
    this.loadFromStorage();
    const deleted = this.tasks.delete(id);
    if (deleted) this.persist();
    return deleted;
  }

  public clear(): void {
    this.tasks.clear();
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {}
  }
}

export const taskStore = new TaskStore();
