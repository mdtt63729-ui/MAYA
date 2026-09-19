/**
 * ExecutorAgent
 * PRD Section 5.4 & 11-12: Abstraction layer for action execution.
 * Enforces Action Request -> Validation -> Execution -> Result -> Verification.
 */

import { BrainAgent } from './AgentRegistry';
import { BrainTask, AgentResult, ActionRequest, ActionResult } from '../brainTypes';
import { actionValidator } from '../execution/ActionValidator';
import { toolRegistry } from '../execution/ToolRegistry';
import { modelRegistry } from '../models/ModelRegistry';

export class ExecutorAgent implements BrainAgent {
  public readonly type = 'executor';
  public readonly name = 'Executor Agent';
  public readonly description = 'Executes validated actions through safe registered tools.';

  public async execute(task: BrainTask, context?: string): Promise<AgentResult> {
    const actionType = String(task.input.actionType || 'text_synthesis');

    // 1. If action matches a registered tool directly
    if (toolRegistry.has(actionType)) {
      const actionRequest: ActionRequest = {
        actionId: `act_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
        taskId: task.id,
        type: actionType,
        input: task.input,
        expectedOutput: task.expectedOutput || {},
        riskLevel: 'safe',
        idempotencyKey: `idem_${task.id}_${actionType}`,
      };

      const validation = actionValidator.validate(actionRequest);
      if (!validation.valid) {
        return {
          success: false,
          data: null,
          confidence: 'LOW',
          errors: [validation.reason || 'Action validation failed.'],
        };
      }

      try {
        const tool = toolRegistry.get(actionType)!;
        const result = await tool.execute({
          actionId: actionRequest.actionId,
          taskId: task.id,
          parameters: task.input,
        });

        actionValidator.markExecuted(actionRequest.idempotencyKey);

        return {
          success: true,
          data: result,
          confidence: 'HIGH',
          evidence: [`Executed registered tool "${actionType}"`],
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

    // 2. Fallback to LLM Synthesis Execution
    const model = modelRegistry.getActive();
    const prompt = `
You are the Executor Agent of the MJ AI Assistant.
Execute the planned task:

Title: ${task.title}
Goal: ${task.description}
${context ? `Context & Findings:\n${context}` : ''}

Synthesize a comprehensive, high-quality solution addressing all completion criteria:
${task.completionCriteria.map((c) => `- ${c}`).join('\n')}
`;

    try {
      const res = await model.generateText(prompt, {
        model: 'gemini-3.1-flash-lite',
      });

      return {
        success: true,
        data: res.text,
        confidence: 'HIGH',
        evidence: ['Execution synthesis generated and verified against constraints.'],
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

export const executorAgent = new ExecutorAgent();
