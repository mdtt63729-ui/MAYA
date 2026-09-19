/**
 * VerificationRules
 * PRD Section 13 & 14: Heuristic and semantic rules for levels 0–4 verification.
 */

import { BrainTask, VerificationLevel } from '../brainTypes';

export interface RuleEvaluation {
  passed: boolean;
  checkName: string;
  detail?: string;
}

export class VerificationRules {
  // Level 0: Basic execution completeness
  public static evaluateLevel0(task: BrainTask): RuleEvaluation {
    const hasOutput = task.output !== undefined && task.output !== null;
    const notEmpty = hasOutput && String(task.output).trim().length > 0;
    return {
      passed: hasOutput && notEmpty,
      checkName: 'L0_Basic_Execution_Present',
      detail: hasOutput ? 'Output is non-empty.' : 'Output is null or undefined.',
    };
  }

  // Level 1: Structural completeness
  public static evaluateLevel1(task: BrainTask): RuleEvaluation {
    if (typeof task.expectedOutput === 'object' && task.expectedOutput !== null) {
      if (typeof task.output !== 'object' || task.output === null) {
        return {
          passed: false,
          checkName: 'L1_Structural_Shape_Mismatch',
          detail: 'Expected object output but received primitive.',
        };
      }
      const expectedKeys = Object.keys(task.expectedOutput);
      const actualKeys = Object.keys(task.output as Record<string, unknown>);
      const missingKeys = expectedKeys.filter((k) => !actualKeys.includes(k));

      return {
        passed: missingKeys.length === 0,
        checkName: 'L1_Required_Keys_Check',
        detail: missingKeys.length === 0 ? 'All structural keys matched.' : `Missing keys: ${missingKeys.join(', ')}`,
      };
    }

    return {
      passed: true,
      checkName: 'L1_Structural_Format_Acceptable',
      detail: 'Format passes baseline structure check.',
    };
  }

  // Level 2: Completion criteria satisfaction
  public static evaluateLevel2(task: BrainTask): RuleEvaluation[] {
    const evaluations: RuleEvaluation[] = [];
    const outputStr = typeof task.output === 'string' ? task.output : JSON.stringify(task.output);

    task.completionCriteria.forEach((criterion, idx) => {
      // Basic keyword / intent presence heuristic
      const keywords = criterion
        .toLowerCase()
        .replace(/[^a-z0-9 ]/g, '')
        .split(' ')
        .filter((w) => w.length > 3);

      const matchCount = keywords.filter((w) => outputStr.toLowerCase().includes(w)).length;
      const passed = keywords.length === 0 || matchCount >= Math.min(1, keywords.length);

      evaluations.push({
        passed,
        checkName: `L2_Criterion_${idx + 1}`,
        detail: `Criterion: "${criterion}" - ${passed ? 'Satisfied' : 'Evidence not clearly found in output'}`,
      });
    });

    return evaluations;
  }

  // Level 3: Cross-check consistency & contradiction check
  public static evaluateLevel3(task: BrainTask): RuleEvaluation {
    const outputStr = typeof task.output === 'string' ? task.output : JSON.stringify(task.output);

    // Guard against explicit self-contradiction patterns or error text leaking into success output
    const contradictionPatterns = [
      /however, i cannot/i,
      /as an ai language model, i am unable/i,
      /error occurred while executing/i,
      /failed to find any information/i,
    ];

    const hasContradiction = contradictionPatterns.some((pattern) => pattern.test(outputStr));

    return {
      passed: !hasContradiction,
      checkName: 'L3_Internal_Consistency_Check',
      detail: hasContradiction ? 'Output contains self-contradiction or disclaimer text.' : 'No contradictory tokens detected.',
    };
  }

  // Level 4: Critical safety & hallucination audit
  public static evaluateLevel4(task: BrainTask): RuleEvaluation {
    const outputStr = typeof task.output === 'string' ? task.output : JSON.stringify(task.output);

    // Guard against destructive or hallucinated credentials
    const safetyViolations = [
      /password\s*=\s*['"][^'"]+['"]/i,
      /api_key\s*=\s*['"][^'"]+['"]/i,
      /rm\s+-rf/i,
      /<script[\s\S]*?>/i,
    ];

    const hasSafetyViolation = safetyViolations.some((pattern) => pattern.test(outputStr));

    return {
      passed: !hasSafetyViolation,
      checkName: 'L4_Critical_Safety_Audit',
      detail: hasSafetyViolation ? 'Safety hazard detected in output.' : 'Critical safety audit passed.',
    };
  }
}
