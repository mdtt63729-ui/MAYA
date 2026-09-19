/**
 * CheckpointStore
 * PRD Sections 20-21 & 54: Atomic state checkpoints for crash recovery,
 * task resumption, and execution rewind without repeating completed steps.
 */

import { TaskCheckpoint } from '../core/brainTypes';

const STORAGE_KEY = 'mj_brain_checkpoints_v1';

class CheckpointStore {
  private checkpoints: Map<string, TaskCheckpoint> = new Map();
  private isLoaded = false;

  constructor() {
    this.loadFromStorage();
  }

  private loadFromStorage(): void {
    if (this.isLoaded) return;
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed: TaskCheckpoint[] = JSON.parse(raw);
        parsed.forEach((c) => this.checkpoints.set(c.checkpointId, c));
      }
      this.isLoaded = true;
    } catch (e) {
      console.warn('[CheckpointStore] Failed to load checkpoints from storage:', e);
    }
  }

  private persist(): void {
    try {
      const arr = Array.from(this.checkpoints.values());
      localStorage.setItem(STORAGE_KEY, JSON.stringify(arr));
    } catch (e) {
      console.warn('[CheckpointStore] Failed to persist checkpoints:', e);
    }
  }

  public save(checkpoint: TaskCheckpoint): TaskCheckpoint {
    this.loadFromStorage();
    this.checkpoints.set(checkpoint.checkpointId, { ...checkpoint });
    this.persist();
    return checkpoint;
  }

  public get(checkpointId: string): TaskCheckpoint | undefined {
    this.loadFromStorage();
    const cp = this.checkpoints.get(checkpointId);
    return cp ? { ...cp } : undefined;
  }

  public getLatestForTask(taskId: string): TaskCheckpoint | undefined {
    this.loadFromStorage();
    const taskCheckpoints = Array.from(this.checkpoints.values())
      .filter((c) => c.taskId === taskId)
      .sort((a, b) => b.timestamp - a.timestamp);

    return taskCheckpoints[0] ? { ...taskCheckpoints[0] } : undefined;
  }

  public delete(checkpointId: string): boolean {
    this.loadFromStorage();
    const ok = this.checkpoints.delete(checkpointId);
    if (ok) this.persist();
    return ok;
  }

  public purgeForTask(taskId: string): void {
    this.loadFromStorage();
    let changed = false;
    for (const [id, cp] of this.checkpoints.entries()) {
      if (cp.taskId === taskId) {
        this.checkpoints.delete(id);
        changed = true;
      }
    }
    if (changed) this.persist();
  }
}

export const checkpointStore = new CheckpointStore();
