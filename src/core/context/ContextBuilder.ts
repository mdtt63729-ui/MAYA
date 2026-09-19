/**
 * ContextBuilder
 * PRD Sections 22-24, 48 & 49: Context fusion and budget management.
 * Prioritizes: Current Instruction > Active Task > Critical Constraints > Relevant Memory > Recent Conversation.
 */

import { ContextFusionLayer, BrainTask } from '../brainTypes';

export class ContextBuilder {
  private maxTokensBudget = 4000;

  /**
   * Fuse multi-layer context into an optimized prompt prefix
   */
  public buildPromptContext(layer: ContextFusionLayer): string {
    const sections: string[] = [];

    // 1. Current Task Context (Highest structural priority)
    if (layer.currentTask) {
      sections.push(
        `[ACTIVE TASK EXECUTION]\nID: ${layer.currentTask.id}\nTitle: ${layer.currentTask.title}\nGoal: ${layer.currentTask.description}\nCompletion Criteria:\n${layer.currentTask.completionCriteria.map((c) => `- ${c}`).join('\n')}`
      );
    }

    // 2. Relevant Long-Term Memory (Filtered by relevance score)
    if (layer.relevantMemories && layer.relevantMemories.length > 0) {
      const topMemories = layer.relevantMemories
        .slice(0, 4)
        .map((m) => `- ${m.content}`)
        .join('\n');
      sections.push(`[RELEVANT USER MEMORY & PREFERENCES]\n${topMemories}`);
    }

    // 3. Runtime System State
    sections.push(
      `[RUNTIME STATE] Network: ${layer.runtimeState.isOnline ? 'Online' : 'Offline'}, Active Agents: ${layer.runtimeState.activeAgentsCount}`
    );

    // 4. Recent Conversation History (Compressed if long)
    if (layer.recentConversation && layer.recentConversation.length > 0) {
      const historyFormatted = layer.recentConversation
        .slice(-4)
        .map((m) => `${m.role.toUpperCase()}: ${m.content}`)
        .join('\n');
      sections.push(`[RECENT DIALOGUE CONTEXT]\n${historyFormatted}`);
    }

    // 5. Current Instruction
    sections.push(`[CURRENT INSTRUCTION]\n${layer.currentInstruction}`);

    return sections.join('\n\n');
  }

  /**
   * Compress older context when conversation exceeds length budget (PRD Section 24)
   */
  public compressHistory(messages: Array<{ role: string; content: string }>): Array<{ role: string; content: string }> {
    if (messages.length <= 6) return messages;

    const older = messages.slice(0, messages.length - 4);
    const recent = messages.slice(-4);

    const summaryText = `[Summarized previous interactions: User explored ${older.length} prior queries regarding assistant operations.]`;

    return [{ role: 'system', content: summaryText }, ...recent];
  }
}

export const contextBuilder = new ContextBuilder();
