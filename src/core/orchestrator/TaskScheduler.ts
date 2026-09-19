/**
 * TaskScheduler
 * PRD Sections 9, 10, 16, 30 & 31: Concurrency-controlled execution scheduler.
 * Enforces dependency completion, parallel wave execution, timeout enforcement,
 * graceful cancellation, and automatic verification/recovery dispatch.
 */

import { BrainTask, BrainTaskStatus, TaskCheckpoint, VerificationResult } from '../brainTypes';
import { taskStore } from '../../storage/TaskStore';
import { checkpointStore } from '../../storage/CheckpointStore';
import { eventBus } from '../events/EventBus';
import { agentRegistry } from '../agents/AgentRegistry';
import { verifierAgent } from '../agents/VerifierAgent';
import { recoveryEngine } from '../execution/RecoveryEngine';

export class TaskScheduler {
  private concurrencyLimit = 3;
  private runningTaskIds: Set<string> = new Set();
  private cancelledRootIds: Set<string> = new Set();
  private taskTimeouts: Map<string, NodeJS.Timeout> = new Map();

  /**
   * Execute a collection of planned tasks for a root plan
   */
  public async executePlan(
    rootTaskId: string,
    tasks: BrainTask[],
    onProgress?: (task: BrainTask) => void
  ): Promise<{ success: boolean; completedTasks: BrainTask[]; failedTasks: BrainTask[] }> {
    // Persist all initial tasks
    tasks.forEach((t) => taskStore.save(t));

    const totalTasksCount = tasks.length;
    let completedCount = 0;

    while (completedCount < totalTasksCount) {
      // Check if root task was cancelled
      if (this.cancelledRootIds.has(rootTaskId)) {
        this.cancelTasksForRoot(rootTaskId);
        return {
          success: false,
          completedTasks: taskStore.getByRootId(rootTaskId).filter((t) => t.status === 'COMPLETED'),
          failedTasks: taskStore.getByRootId(rootTaskId).filter((t) => t.status === 'CANCELLED' || t.status === 'FAILED'),
        };
      }

      // Fetch fresh tasks
      const currentTasks = taskStore.getByRootId(rootTaskId);

      // Find ready tasks
      const completedIds = new Set(
        currentTasks.filter((t) => t.status === 'COMPLETED').map((t) => t.id)
      );

      const readyTasks = currentTasks.filter((t) => {
        if (t.status !== 'QUEUED' && t.status !== 'READY') return false;
        if (this.runningTaskIds.has(t.id)) return false;
        return t.dependencies.every((depId) => completedIds.has(depId));
      });

      // If no tasks ready and none running, check for deadlock or completion
      if (readyTasks.length === 0 && this.runningTaskIds.size === 0) {
        break;
      }

      // Take up to concurrency limit
      const slotsAvailable = this.concurrencyLimit - this.runningTaskIds.size;
      const toRun = readyTasks.slice(0, Math.max(1, slotsAvailable));

      if (toRun.length > 0) {
        // Run wave in parallel (PRD Section 9: Parallel Task Execution)
        await Promise.all(
          toRun.map((task) =>
            this.runSingleTask(task, (updated) => {
              onProgress?.(updated);
            })
          )
        );
      } else {
        // Yield briefly for running tasks to complete
        await new Promise((resolve) => setTimeout(resolve, 100));
      }

      completedCount = taskStore
        .getByRootId(rootTaskId)
        .filter((t) => t.status === 'COMPLETED' || t.status === 'FAILED' || t.status === 'CANCELLED').length;
    }

    const finalTasks = taskStore.getByRootId(rootTaskId);
    const completedTasks = finalTasks.filter((t) => t.status === 'COMPLETED');
    const failedTasks = finalTasks.filter((t) => t.status === 'FAILED');

    return {
      success: failedTasks.length === 0,
      completedTasks,
      failedTasks,
    };
  }

  /**
   * Run a single task through Execute -> Verify -> Recovery pipeline
   */
  private async runSingleTask(
    task: BrainTask,
    onUpdate?: (task: BrainTask) => void
  ): Promise<void> {
    this.runningTaskIds.add(task.id);
    task.status = 'RUNNING';
    taskStore.save(task);
    onUpdate?.(task);

    eventBus.emit('TASK_STATUS_CHANGED', `Task ${task.id} started`, {
      taskId: task.id,
      agentType: task.assignedAgent,
    });

    // Enforce configurable timeout (PRD Section 31: Task Timeout)
    const timeoutMs = 45000;
    const timeoutPromise = new Promise<never>((_, reject) => {
      const timer = setTimeout(() => {
        reject(new Error(`Task timeout: Exceeded ${timeoutMs}ms limit.`));
      }, timeoutMs);
      this.taskTimeouts.set(task.id, timer);
    });

    try {
      // 1. EXECUTE (Reasoning / Execution separation)
      const agent = agentRegistry.get(task.assignedAgent) || agentRegistry.get('executor')!;
      eventBus.emit('AGENT_STARTED', `Agent ${agent.name} executing task ${task.id}`, {
        taskId: task.id,
        agentType: task.assignedAgent,
      });

      const executionPromise = agent.execute(task);
      const result = await Promise.race([executionPromise, timeoutPromise]);

      if (!result.success) {
        throw new Error(result.errors?.[0] || 'Execution failed without explicit error.');
      }

      task.output = result.data;

      // 2. VERIFY (PRD Section 13-15: Self-Verification Engine)
      task.status = 'VERIFYING';
      taskStore.save(task);
      onUpdate?.(task);

      eventBus.emit('VERIFICATION_STARTED', `Verifying task ${task.id}`, {
        taskId: task.id,
        agentType: 'verifier',
      });

      const verifyResult = await verifierAgent.execute(task);
      task.verificationResult = verifyResult.data as VerificationResult;

      if (!verifyResult.success) {
        throw new Error(`Self-verification failed: ${verifyResult.warnings?.join('; ') || 'Criteria unsatisfied'}`);
      }

      // 3. SUCCESS & CHECKPOINT (PRD Section 20-21: Checkpoint system)
      task.status = 'COMPLETED';
      task.updatedAt = Date.now();

      const checkpoint: TaskCheckpoint = {
        checkpointId: `cp_${task.id}_${Date.now()}`,
        taskId: task.id,
        timestamp: Date.now(),
        completedSubtaskIds: [task.id],
        pendingSubtaskIds: [],
        intermediateOutputs: { [task.id]: task.output },
        dependenciesState: { [task.id]: 'COMPLETED' },
        retryState: { retryCount: task.retryCount },
        verificationState: task.verificationResult,
        memoryReferences: [],
      };
      checkpointStore.save(checkpoint);
      task.checkpoint = checkpoint;

      taskStore.save(task);
      onUpdate?.(task);

      eventBus.emit('TASK_COMPLETED', `Task ${task.id} completed & verified`, {
        taskId: task.id,
        payload: { output: task.output },
      });
    } catch (err) {
      // 4. FAILURE & RECOVERY (PRD Sections 16-19: Recovery Engine)
      await this.handleTaskFailure(task, err, onUpdate);
    } finally {
      const timer = this.taskTimeouts.get(task.id);
      if (timer) {
        clearTimeout(timer);
        this.taskTimeouts.delete(task.id);
      }
      this.runningTaskIds.delete(task.id);
    }
  }

  /**
   * Handle failure with exponential backoff retry or re-plan
   */
  private async handleTaskFailure(
    task: BrainTask,
    err: unknown,
    onUpdate?: (task: BrainTask) => void
  ): Promise<void> {
    const errorType = recoveryEngine.classifyError(err);
    const strategy = recoveryEngine.determineStrategy(task, errorType);
    task.error = recoveryEngine.createError(err, strategy);

    eventBus.emit('TASK_FAILED', `Task ${task.id} failed: ${task.error.message}`, {
      taskId: task.id,
      payload: { errorType, strategy },
    });

    if (strategy === 'RETRY_BACKOFF' || strategy === 'RETRY_IMMEDIATE') {
      task.status = 'RECOVERING';
      task.retryCount++;
      taskStore.save(task);
      onUpdate?.(task);

      const delay = recoveryEngine.calculateBackoff(task.retryCount);
      eventBus.emit('RECOVERY_STARTED', `Retrying task ${task.id} with backoff (${Math.round(delay)}ms)`, {
        taskId: task.id,
      });

      await new Promise((resolve) => setTimeout(resolve, delay));
      // Retry
      return this.runSingleTask(task, onUpdate);
    }

    if (strategy === 'ALTERNATIVE_ROUTE') {
      task.status = 'RECOVERING';
      task.retryCount++;
      // Switch agent or simplify prompt
      task.assignedAgent = task.assignedAgent === 'coder' ? 'executor' : 'researcher';
      taskStore.save(task);
      onUpdate?.(task);

      eventBus.emit('RECOVERY_STARTED', `Executing alternative route for ${task.id} with ${task.assignedAgent}`, {
        taskId: task.id,
      });

      return this.runSingleTask(task, onUpdate);
    }

    // Unrecoverable or max retries exceeded
    task.status = 'FAILED';
    taskStore.save(task);
    onUpdate?.(task);
  }

  /**
   * Cancel all tasks for a root goal (PRD Section 30: Task Cancellation)
   */
  public cancelRoot(rootTaskId: string): void {
    this.cancelledRootIds.add(rootTaskId);
    this.cancelTasksForRoot(rootTaskId);
  }

  private cancelTasksForRoot(rootTaskId: string): void {
    const tasks = taskStore.getByRootId(rootTaskId);
    tasks.forEach((t) => {
      if (t.status !== 'COMPLETED' && t.status !== 'FAILED') {
        t.status = 'CANCELLED';
        taskStore.save(t);
        const timer = this.taskTimeouts.get(t.id);
        if (timer) {
          clearTimeout(timer);
          this.taskTimeouts.delete(t.id);
        }
      }
    });

    eventBus.emit('TASK_CANCELLED', `Root task ${rootTaskId} cancelled by user.`, {
      taskId: rootTaskId,
    });
  }
}

export const taskScheduler = new TaskScheduler();
