/**
 * VerificationEngine
 * PRD Sections 13-15 & 52: Multi-level self-verification engine.
 * Never assumes execution success automatically. Inspects completion criteria,
 * structural accuracy, and internal consistency.
 */

import { BrainTask, VerificationResult, VerificationLevel, VerificationStatus } from '../brainTypes';
import { VerificationRules, RuleEvaluation } from './VerificationRules';

export class VerificationEngine {
  public async verify(task: BrainTask, targetLevel: VerificationLevel = 2): Promise<VerificationResult> {
    const checksPassed: string[] = [];
    const checksFailed: string[] = [];
    const details: string[] = [];

    // 1. Level 0: Basic Execution Check
    const l0 = VerificationRules.evaluateLevel0(task);
    if (l0.passed) {
      checksPassed.push(l0.checkName);
    } else {
      checksFailed.push(l0.checkName);
      details.push(l0.detail || 'Missing output');
    }

    // If Level 0 failed, immediate failure
    if (!l0.passed) {
      return {
        status: 'FAILED',
        level: 0,
        score: 0,
        checksPassed,
        checksFailed,
        feedback: `Execution produced no valid output. ${details.join('; ')}`,
        timestamp: Date.now(),
      };
    }

    // 2. Level 1: Structural Format
    if (targetLevel >= 1) {
      const l1 = VerificationRules.evaluateLevel1(task);
      if (l1.passed) {
        checksPassed.push(l1.checkName);
      } else {
        checksFailed.push(l1.checkName);
        details.push(l1.detail || 'Structural mismatch');
      }
    }

    // 3. Level 2: Semantic Criteria
    if (targetLevel >= 2) {
      const l2List = VerificationRules.evaluateLevel2(task);
      l2List.forEach((ev) => {
        if (ev.passed) {
          checksPassed.push(ev.checkName);
        } else {
          checksFailed.push(ev.checkName);
          details.push(ev.detail || 'Criterion failed');
        }
      });
    }

    // 4. Level 3: Cross-check & Consistency
    if (targetLevel >= 3) {
      const l3 = VerificationRules.evaluateLevel3(task);
      if (l3.passed) {
        checksPassed.push(l3.checkName);
      } else {
        checksFailed.push(l3.checkName);
        details.push(l3.detail || 'Inconsistency detected');
      }
    }

    // 5. Level 4: Critical Validation
    if (targetLevel >= 4) {
      const l4 = VerificationRules.evaluateLevel4(task);
      if (l4.passed) {
        checksPassed.push(l4.checkName);
      } else {
        checksFailed.push(l4.checkName);
        details.push(l4.detail || 'Safety audit failed');
      }
    }

    // Calculate score
    const totalChecks = checksPassed.length + checksFailed.length;
    const score = totalChecks > 0 ? checksPassed.length / totalChecks : 1.0;

    // Status classification (PRD Section 15)
    let status: VerificationStatus = 'VERIFIED';
    if (score === 1.0) {
      status = 'VERIFIED';
    } else if (score >= 0.7) {
      status = 'PARTIALLY_VERIFIED';
    } else if (score >= 0.4) {
      status = 'UNCERTAIN';
    } else {
      status = 'FAILED';
    }

    return {
      status,
      level: targetLevel,
      score,
      checksPassed,
      checksFailed,
      feedback: details.length > 0 ? details.join('; ') : 'All verification criteria successfully passed.',
      timestamp: Date.now(),
    };
  }
}

export const verificationEngine = new VerificationEngine();
