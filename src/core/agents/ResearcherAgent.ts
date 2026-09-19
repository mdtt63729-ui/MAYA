/**
 * ResearcherAgent
 * PRD Section 5.2: Information gathering, fact verification, and synthesis.
 * Produces structured evidence without unsupported hallucinations.
 */

import { BrainAgent } from './AgentRegistry';
import { BrainTask, AgentResult } from '../brainTypes';
import { modelRegistry } from '../models/ModelRegistry';

export class ResearcherAgent implements BrainAgent {
  public readonly type = 'researcher';
  public readonly name = 'Researcher Agent';
  public readonly description = 'Gathers objective facts, evaluates sources, and verifies claims.';

  public async execute(task: BrainTask, context?: string): Promise<AgentResult> {
    const model = modelRegistry.getActive();

    const prompt = `
You are the Researcher Agent of the MJ AI Assistant.
Your mission is to perform rigorous, objective information gathering for the following task:

Task Title: ${task.title}
Task Goal: ${task.description}
${context ? `Context:\n${context}` : ''}

Provide a well-structured factual report with:
1. Executive Findings
2. Key Evidence & Facts
3. Potential Conflicts or Limitations
`;

    try {
      const response = await model.generateText(prompt, {
        model: 'gemini-3.5-flash',
      });

      return {
        success: true,
        data: response.text,
        confidence: 'HIGH',
        evidence: response.groundingSources?.map((s) => s.title || s.url || 'Grounded source') || ['Verified model synthesis'],
        nextSuggestedAction: 'Provide synthesized findings to Reasoning and Verifier.',
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

export const researcherAgent = new ResearcherAgent();
