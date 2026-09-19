/**
 * MJ Brain EventBus
 * PRD Section 32 & 36: Decoupled, strongly-typed asynchronous event infrastructure
 * for inter-agent telemetry, auditing, diagnostics, and UI synchronization.
 */

import { BrainEvent, BrainEventType, AgentType } from '../brainTypes';

type EventListener = (event: BrainEvent) => void;

class EventBus {
  private listeners: Map<BrainEventType | '*', Set<EventListener>> = new Map();
  private history: BrainEvent[] = [];
  private readonly maxHistorySize = 250;

  /**
   * Subscribe to specific event type or wildcard '*'
   */
  public subscribe(type: BrainEventType | '*', listener: EventListener): () => void {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, new Set());
    }
    this.listeners.get(type)!.add(listener);

    return () => {
      this.listeners.get(type)?.delete(listener);
    };
  }

  /**
   * Publish structured brain event
   */
  public emit(
    type: BrainEventType,
    message: string,
    details?: {
      taskId?: string;
      agentType?: AgentType;
      payload?: Record<string, unknown>;
    }
  ): BrainEvent {
    const event: BrainEvent = {
      id: `evt_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      type,
      message,
      taskId: details?.taskId,
      agentType: details?.agentType,
      timestamp: Date.now(),
      payload: details?.payload,
    };

    // Store in history buffer
    this.history.push(event);
    if (this.history.length > this.maxHistorySize) {
      this.history.shift();
    }

    // Notify specific type listeners
    const specific = this.listeners.get(type);
    if (specific) {
      specific.forEach((fn) => {
        try {
          fn(event);
        } catch (e) {
          console.error('[EventBus Listener Error]', e);
        }
      });
    }

    // Notify wildcard listeners
    const wildcard = this.listeners.get('*');
    if (wildcard) {
      wildcard.forEach((fn) => {
        try {
          fn(event);
        } catch (e) {
          console.error('[EventBus Wildcard Listener Error]', e);
        }
      });
    }

    return event;
  }

  /**
   * Retrieve operational history
   */
  public getHistory(limit?: number): BrainEvent[] {
    if (limit && limit > 0) {
      return this.history.slice(-limit);
    }
    return [...this.history];
  }

  /**
   * Clear history (for testing or reset)
   */
  public clear(): void {
    this.history = [];
  }
}

export const eventBus = new EventBus();
