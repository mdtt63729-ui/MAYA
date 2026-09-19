/**
 * AgentRegistry
 * PRD Section 43: Centralized registry for all specialized Brain agents.
 */

import { AgentType, AgentResult, BrainTask } from '../brainTypes';
import { plannerAgent } from './PlannerAgent';
import { researcherAgent } from './ResearcherAgent';
import { coderAgent } from './CoderAgent';
import { executorAgent } from './ExecutorAgent';
import { memoryAgent } from './MemoryAgent';
import { verifierAgent } from './VerifierAgent';

export interface BrainAgent {
  readonly type: AgentType;
  readonly name: string;
  readonly description: string;
  execute(task: BrainTask, context?: string): Promise<AgentResult>;
}

class AgentRegistry {
  private agents: Map<AgentType, BrainAgent> = new Map();

  constructor() {
    this.register(plannerAgent);
    this.register(researcherAgent);
    this.register(coderAgent);
    this.register(executorAgent);
    this.register(memoryAgent);
    this.register(verifierAgent);
  }

  public register(agent: BrainAgent): void {
    this.agents.set(agent.type, agent);
  }

  public get(type: AgentType): BrainAgent | undefined {
    return this.agents.get(type);
  }

  public has(type: AgentType): boolean {
    return this.agents.has(type);
  }

  public list(): BrainAgent[] {
    return Array.from(this.agents.values());
  }
}

export const agentRegistry = new AgentRegistry();
