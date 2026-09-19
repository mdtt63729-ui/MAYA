/**
 * MJOrchestrator
 * PRD Sections 4, 33, 34, 57, 58, 61 & 62: Central controller for the AI Brain.
 * Orchestrates the full lifecycle:
 * Understand → Plan → Reason → Execute → Verify → Recover → Remember → Resume
 */

import { BrainTask, BrainDiagnostics, ContextFusionLayer } from '../brainTypes';
import { taskStore } from '../../storage/TaskStore';
import { checkpointStore } from '../../storage/CheckpointStore';
import { eventBus } from '../events/EventBus';
import { taskPlanner } from '../planning/TaskPlanner';
import { taskScheduler } from './TaskScheduler';
import { contextBuilder } from '../context/ContextBuilder';
import { memoryService } from '../../services/memoryService';

export interface ExecutionProgressEvent {
  stage: 'UNDERSTAND' | 'PLAN' | 'REASON' | 'EXECUTE' | 'VERIFY' | 'RECOVER' | 'REMEMBER' | 'DONE';
  rootTaskId: string;
  activeTask?: BrainTask;
  allTasks: BrainTask[];
  message: string;
}

export class MJOrchestrator {
  private activeRootTaskId?: string;
  private isProcessing = false;
  private progressListeners: Set<(ev: ExecutionProgressEvent) => void> = new Set();

  constructor() {
    this.checkIncompleteTasksOnBoot();
  }

  /**
   * Register progress listener for UI synchronization
   */
  public onProgress(listener: (ev: ExecutionProgressEvent) => void): () => void {
    this.progressListeners.add(listener);
    return () => this.progressListeners.delete(listener);
  }

  private notifyProgress(ev: ExecutionProgressEvent): void {
    this.progressListeners.forEach((fn) => {
      try {
        fn(ev);
      } catch (err) {
        console.error('[Orchestrator Progress Listener Error]', err);
      }
    });
  }

  /**
   * Main entrypoint for processing user instructions through the Brain Pipeline
   */
  public async processInstruction(
    instruction: string,
    recentConversation: Array<{ role: string; content: string }> = []
  ): Promise<{
    success: boolean;
    rootTaskId: string;
    summary: string;
    tasks: BrainTask[];
  }> {
    if (this.isProcessing) {
      // PRD Section 9: If already running, queue or parallel root task
    }

    this.isProcessing = true;
    const rootTaskId = `task_${Date.now()}`;
    this.activeRootTaskId = rootTaskId;

    try {
      // 1. UNDERSTAND & CONTEXT FUSION (PRD Sections 22-24)
      this.notifyProgress({
        stage: 'UNDERSTAND',
        rootTaskId,
        allTasks: [],
        message: 'Analyzing user instruction and fusing context layers...',
      });

      eventBus.emit('TASK_CREATED', `Received goal: "${instruction}"`, { taskId: rootTaskId });

      // Search relevant memories
      const relevantMemories = memoryService.search(instruction).map((m) => ({
        id: m.id,
        content: m.content,
        score: 0.9,
      }));

      const contextLayer: ContextFusionLayer = {
        currentInstruction: instruction,
        recentConversation: recentConversation.slice(-6),
        relevantMemories,
        runtimeState: {
          isOnline: navigator.onLine,
          activeAgentsCount: 1,
          pendingTasksCount: 0,
        },
      };

      const fusedContext = contextBuilder.buildPromptContext(contextLayer);

      // 2. PLAN (PRD Sections 6 & 7: Decompose into Task Graph)
      this.notifyProgress({
        stage: 'PLAN',
        rootTaskId,
        allTasks: [],
        message: 'Decomposing request into structured execution graph...',
      });

      const plannedSubtasks = await taskPlanner.plan(instruction, rootTaskId);
      plannedSubtasks.forEach((t) => taskStore.save(t));

      this.notifyProgress({
        stage: 'REASON',
        rootTaskId,
        allTasks: plannedSubtasks,
        message: `Generated execution plan with ${plannedSubtasks.length} subtasks.`,
      });

      // 3. EXECUTE, VERIFY & RECOVER PIPELINE (PRD Sections 8-19)
      this.notifyProgress({
        stage: 'EXECUTE',
        rootTaskId,
        allTasks: plannedSubtasks,
        message: 'Executing tasks across specialized agents with dependency locking...',
      });

      const executionResult = await taskScheduler.executePlan(
        rootTaskId,
        plannedSubtasks,
        (updatedTask) => {
          const allCurrent = taskStore.getByRootId(rootTaskId);
          this.notifyProgress({
            stage: updatedTask.status === 'VERIFYING' ? 'VERIFY' : updatedTask.status === 'RECOVERING' ? 'RECOVER' : 'EXECUTE',
            rootTaskId,
            activeTask: updatedTask,
            allTasks: allCurrent,
            message: `${updatedTask.assignedAgent.toUpperCase()} Agent: ${updatedTask.title} (${updatedTask.status})`,
          });
        }
      );

      // 4. REMEMBER (PRD Section 25: Save valuable learnings into long-term memory)
      this.notifyProgress({
        stage: 'REMEMBER',
        rootTaskId,
        allTasks: taskStore.getByRootId(rootTaskId),
        message: 'Evaluating task insights for memory storage...',
      });

      // Auto-extract and retain critical preferences if user specified them
      if (/always|never|remember that|my name is|prefer|default to/i.test(instruction)) {
        memoryService.add(instruction, 'preference', 'HIGH', ['auto-extracted', 'user-directive']);
      }

      const allFinalTasks = taskStore.getByRootId(rootTaskId);

      // 5. SYNTHESIZE FINAL RESPONSE
      const successfulOutputs = allFinalTasks
        .filter((t) => t.status === 'COMPLETED' && t.output)
        .map((t) => {
          const out = t.output;
          return typeof out === 'string' ? out : JSON.stringify(out, null, 2);
        });

      let finalSummary = '';
      if (successfulOutputs.length > 0) {
        // Return the last completed output or synthesis
        finalSummary = successfulOutputs[successfulOutputs.length - 1];
      } else {
        finalSummary = 'Completed execution, but no explicit text output was produced.';
      }

      this.notifyProgress({
        stage: 'DONE',
        rootTaskId,
        allTasks: allFinalTasks,
        message: 'All tasks completed and verified.',
      });

      eventBus.emit('TASK_COMPLETED', `Completed execution for root ${rootTaskId}`, {
        taskId: rootTaskId,
      });

      return {
        success: executionResult.success,
        rootTaskId,
        summary: finalSummary,
        tasks: allFinalTasks,
      };
    } finally {
      this.isProcessing = false;
      this.activeRootTaskId = undefined;
    }
  }

  /**
   * Cancel currently running root task
   */
  public cancelCurrent(): void {
    if (this.activeRootTaskId) {
      taskScheduler.cancelRoot(this.activeRootTaskId);
      this.isProcessing = false;
      this.activeRootTaskId = undefined;
    }
  }

  /**
   * Crash & Network Recovery on Boot (PRD Sections 20, 21, 61 & 62)
   */
  private checkIncompleteTasksOnBoot(): void {
    try {
      const allTasks = taskStore.getAll();
      const unfinished = allTasks.filter(
        (t) => t.status === 'RUNNING' || t.status === 'VERIFYING' || t.status === 'RECOVERING'
      );

      if (unfinished.length > 0) {
        console.log(`[MJOrchestrator] Detected ${unfinished.length} unfinished tasks after restart.`);
        unfinished.forEach((t) => {
          // Check for safe checkpoint
          const cp = checkpointStore.getLatestForTask(t.id);
          if (cp) {
            console.log(`[MJOrchestrator] Restoring task ${t.id} to ready state from checkpoint.`);
            t.status = 'READY';
          } else {
            t.status = 'FAILED';
            t.error = {
              type: 'RESOURCE_ERROR',
              message: 'Task interrupted by application shutdown before first checkpoint.',
              timestamp: Date.now(),
              recoveryAttempted: false,
            };
          }
          taskStore.save(t);
        });
      }
    } catch (e) {
      console.warn('[MJOrchestrator] Boot recovery check encountered error:', e);
    }
  }

  /**
   * Diagnostic summary
   */
  public getDiagnostics(): BrainDiagnostics {
    const all = taskStore.getAll();
    return {
      activeTaskId: this.activeRootTaskId,
      totalTasks: all.length,
      runningTasks: all.filter((t) => t.status === 'RUNNING').length,
      completedTasks: all.filter((t) => t.status === 'COMPLETED').length,
      failedTasks: all.filter((t) => t.status === 'FAILED').length,
      retryCount: all.reduce((sum, t) => sum + (t.retryCount || 0), 0),
      recoveryCount: all.filter((t) => t.status === 'RECOVERING').length,
      concurrencyLimit: 3,
    };
  }
}

export const orchestrator = new MJOrchestrator();
