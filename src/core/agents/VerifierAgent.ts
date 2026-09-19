/**
 * VerifierAgent
 * PRD Sections 13-15 & 52: Self-verification agent validating task outputs
 * against completion criteria, structural validity, and internal consistency.
 */

import { BrainAgent } from './AgentRegistry';
import { BrainTask, AgentResult, VerificationResult } from '../brainTypes';
import { verificationEngine } from '../verification/VerificationEngine';

export class VerifierAgent implements BrainAgent {
  public readonly type = 'verifier';
  public readonly name = 'Verifier Agent';
  public readonly description = 'Validates execution outcomes against strict completion criteria.';

  public async execute(task: BrainTask): Promise<AgentResult<VerificationResult>> {
    const result = await verificationEngine.verify(task, 2);

    const isSuccess = result.status === 'VERIFIED' || result.status === 'PARTIALLY_VERIFIED';

    return {
      success: isSuccess,
      data: result,
      confidence: result.score >= 0.8 ? 'HIGH' : result.score >= 0.5 ? 'MEDIUM' : 'LOW',
      evidence: result.checksPassed,
      warnings: result.checksFailed,
      nextSuggestedAction: isSuccess ? 'Proceed with task completion.' : 'Trigger recovery or re-plan.',
    };
  }
}

export const verifierAgent = new VerifierAgent();
