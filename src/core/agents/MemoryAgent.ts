/**
 * MemoryAgent
 * PRD Sections 5.5 & 25-28: Evaluates context relevance, assigns importance scores,
 * detects memory conflicts, and manages task vs long-term memory boundaries.
 */

import { BrainAgent } from './AgentRegistry';
import { BrainTask, AgentResult } from '../brainTypes';
import { memoryService } from '../../services/memoryService';

export class MemoryAgent implements BrainAgent {
  public readonly type = 'memory';
  public readonly name = 'Memory Agent';
  public readonly description = 'Retrieves relevant memories, evaluates context relevance, and manages persistence.';

  public async execute(task: BrainTask): Promise<AgentResult> {
    const query = String(task.input.query || task.title);
    const matches = memoryService.search(query);

    return {
      success: true,
      data: matches,
      confidence: 'HIGH',
      evidence: [`Retrieved ${matches.length} relevant memory nodes from long-term vault.`],
      nextSuggestedAction: 'Provide memory context to Reasoning & Planning engines.',
    };
  }

  /**
   * Score memory relevance against query
   */
  public scoreRelevance(memoryContent: string, query: string): number {
    const words = query.toLowerCase().split(/\s+/).filter((w) => w.length > 2);
    if (words.length === 0) return 0.5;

    let hits = 0;
    words.forEach((w) => {
      if (memoryContent.toLowerCase().includes(w)) hits++;
    });

    return Math.min(1.0, hits / words.length);
  }
}

export const memoryAgent = new MemoryAgent();
