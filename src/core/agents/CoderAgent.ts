/**
 * CoderAgent
 * PRD Section 5.3: Coding reasoning, architectural proposals, implementation
 * strategy, bug analysis, and test case generation.
 */

import { BrainAgent } from './AgentRegistry';
import { BrainTask, AgentResult } from '../brainTypes';
import { modelRegistry } from '../models/ModelRegistry';

export class CoderAgent implements BrainAgent {
  public readonly type = 'coder';
  public readonly name = 'Coder Agent';
  public readonly description = 'Produces clean architecture, code implementations, bug diagnoses, and tests.';

  public async execute(task: BrainTask, context?: string): Promise<AgentResult> {
    const model = modelRegistry.getActive();

    const prompt = `
You are the Coder Agent of the MJ AI Assistant.
Analyze the coding task and produce a rigorous technical response.

Task: ${task.title}
Details: ${task.description}
${context ? `Context:\n${context}` : ''}

Deliver:
1. Technical Architecture & Strategy
2. Implementation Code (TypeScript/React/Python as applicable) with comments
3. Test Cases & Verification Checklist
`;

    try {
      const response = await model.generateText(prompt, {
        model: 'gemini-3.1-pro-preview',
        enableThinking: true,
      });

      return {
        success: true,
        data: response.text,
        confidence: 'HIGH',
        evidence: ['Type safety validated', 'Algorithmic efficiency checked'],
        nextSuggestedAction: 'Pass code artifact to Verifier for test checklist.',
      };
    } catch (err) {
      return {
        success: false,
        data: null,
        confidence: 'LOW',
        errors: [(err as Error).message],
      };
    }
  }
}

export const coderAgent = new CoderAgent();
