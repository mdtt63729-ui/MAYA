/**
 * EventStore
 * PRD Sections 36 & 63: Structured audit log persistence for telemetry,
 * developer diagnostics, latency tracking, and execution tracing.
 */

import { BrainEvent } from '../core/brainTypes';

const STORAGE_KEY = 'mj_brain_events_log_v1';
const MAX_LOG_SIZE = 500;

class EventStore {
  private events: BrainEvent[] = [];
  private isLoaded = false;

  constructor() {
    this.load();
  }

  private load(): void {
    if (this.isLoaded) return;
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        this.events = JSON.parse(raw);
      }
      this.isLoaded = true;
    } catch {
      this.events = [];
    }
  }

  private persist(): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.events));
    } catch {}
  }

  public record(event: BrainEvent): void {
    this.load();
    this.events.push(event);
    if (this.events.length > MAX_LOG_SIZE) {
      this.events.shift();
    }
    this.persist();
  }

  public getForTask(taskId: string): BrainEvent[] {
    this.load();
    return this.events.filter((e) => e.taskId === taskId);
  }

  public getRecent(limit = 100): BrainEvent[] {
    this.load();
    return this.events.slice(-limit);
  }

  public clear(): void {
    this.events = [];
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {}
  }
}

export const eventStore = new EventStore();
