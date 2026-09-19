/**
 * ActionValidator
 * PRD Sections 11, 12, 45 & 51: Policy, permission, and security gatekeeper.
 * Ensures strict Reasoning/Execution separation, guards against arbitrary execution,
 * verifies risk levels, and prevents duplicate actions via idempotency keys.
 */

import { ActionRequest } from '../brainTypes';
import { toolRegistry } from './ToolRegistry';

export interface ValidationResult {
  valid: boolean;
  reason?: string;
}

class ActionValidator {
  private executedIdempotencyKeys: Set<string> = new Set();

  public validate(action: ActionRequest): ValidationResult {
    // 1. Structural schema validation
    if (!action.actionId || !action.taskId || !action.type) {
      return { valid: false, reason: 'Missing mandatory action fields (actionId, taskId, or type).' };
    }

    // 2. Unregistered action type check
    if (!toolRegistry.has(action.type)) {
      return {
        valid: false,
        reason: `Unregistered action type: "${action.type}". Only registered safe tools are permitted.`,
      };
    }

    // 3. Prevent arbitrary JS/Shell execution injection
    const inputStr = JSON.stringify(action.input);
    if (/eval\(|new Function|child_process|<script/i.test(inputStr)) {
      return {
        valid: false,
        reason: 'Security violation: Attempted injection of arbitrary executable script.',
      };
    }

    // 4. Duplicate action protection (PRD Section 51)
    if (action.idempotencyKey) {
      if (this.executedIdempotencyKeys.has(action.idempotencyKey)) {
        return {
          valid: false,
          reason: `Duplicate action detected with idempotency key "${action.idempotencyKey}". Execution prevented.`,
        };
      }
    }

    // 5. Tool definition risk alignment
    const tool = toolRegistry.get(action.type);
    if (tool && tool.riskLevel === 'critical' && action.riskLevel !== 'critical') {
      return {
        valid: false,
        reason: 'Risk escalation: Tool is categorized as critical but action specified lower risk.',
      };
    }

    return { valid: true };
  }

  public markExecuted(idempotencyKey?: string): void {
    if (idempotencyKey) {
      this.executedIdempotencyKeys.add(idempotencyKey);
    }
  }

  public clearIdempotencyCache(): void {
    this.executedIdempotencyKeys.clear();
  }
}

export const actionValidator = new ActionValidator();
