/**
 * RecoveryEngine
 * PRD Sections 16-19, 50 & 53: Comprehensive fault tolerance engine.
 * Classifies failure modes, calculates exponential backoff with jitter,
 * prevents retry storms, and devises alternative routing or re-planning strategies.
 */

import { FailureType, RecoveryStrategy, BrainTask, TaskError } from '../brainTypes';

export class RecoveryEngine {
  private readonly defaultMaxRetries = 3;
  private readonly baseDelayMs = 800;
  private readonly maxDelayMs = 6000;

  /**
   * Classify an arbitrary caught error into a typed FailureType
   */
  public classifyError(err: unknown): FailureType {
    if (!err) return 'UNKNOWN_ERROR';

    const message = err instanceof Error ? err.message : String(err);
    const lower = message.toLowerCase();

    if (lower.includes('abort') || lower.includes('timeout')) {
      return 'TIMEOUT';
    }
    if (lower.includes('network') || lower.includes('fetch') || lower.includes('failed to fetch') || lower.includes('offline')) {
      return 'NETWORK_ERROR';
    }
    if (lower.includes('permission') || lower.includes('unauthorized') || lower.includes('denied')) {
      return 'PERMISSION_ERROR';
    }
    if (lower.includes('invalid') || lower.includes('missing field') || lower.includes('bad request')) {
      return 'INVALID_INPUT';
    }
    if (lower.includes('model') || lower.includes('gemini') || lower.includes('quota') || lower.includes('rate limit')) {
      return 'MODEL_ERROR';
    }
    if (lower.includes('tool') || lower.includes('unregistered action')) {
      return 'TOOL_ERROR';
    }
    if (lower.includes('dependency') || lower.includes('parent task')) {
      return 'DEPENDENCY_ERROR';
    }
    if (lower.includes('verification') || lower.includes('failed criteria')) {
      return 'VERIFICATION_ERROR';
    }

    return 'UNKNOWN_ERROR';
  }

  /**
   * Determine the most effective recovery strategy
   */
  public determineStrategy(task: BrainTask, errorType: FailureType): RecoveryStrategy {
    const maxRetries = task.maxRetries || this.defaultMaxRetries;

    // If max retries reached, abort or re-plan
    if (task.retryCount >= maxRetries) {
      if (errorType === 'VERIFICATION_ERROR' || errorType === 'INVALID_INPUT') {
        return 'REPLAN';
      }
      return 'ABORT';
    }

    switch (errorType) {
      case 'NETWORK_ERROR':
      case 'TIMEOUT':
        return 'RETRY_BACKOFF';

      case 'MODEL_ERROR':
        // Rate limit or transient glitch -> backoff
        return 'RETRY_BACKOFF';

      case 'VERIFICATION_ERROR':
        // If criteria failed slightly, try re-planning parameters or alternate route
        return task.retryCount === 0 ? 'ALTERNATIVE_ROUTE' : 'REPLAN';

      case 'INVALID_INPUT':
        return 'REPLAN';

      case 'PERMISSION_ERROR':
        return 'REQUEST_CLARIFICATION';

      case 'TOOL_ERROR':
        return 'ALTERNATIVE_ROUTE';

      case 'DEPENDENCY_ERROR':
        return 'REPLAN';

      default:
        return 'RETRY_BACKOFF';
    }
  }

  /**
   * Calculate exponential backoff delay with jitter (PRD Section 19)
   */
  public calculateBackoff(retryCount: number): number {
    const exp = Math.min(retryCount, 4);
    const calculated = this.baseDelayMs * Math.pow(2, exp);
    // Add jitter +/- 20%
    const jitter = (Math.random() * 0.4 - 0.2) * calculated;
    return Math.min(this.maxDelayMs, Math.max(300, calculated + jitter));
  }

  /**
   * Construct structured TaskError
   */
  public createError(err: unknown, strategy?: RecoveryStrategy): TaskError {
    const type = this.classifyError(err);
    const message = err instanceof Error ? err.message : String(err);
    return {
      type,
      message,
      rawError: err,
      timestamp: Date.now(),
      recoveryAttempted: false,
      strategy,
    };
  }
}

export const recoveryEngine = new RecoveryEngine();
