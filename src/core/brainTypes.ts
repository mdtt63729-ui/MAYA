/**
 * MJ AI Assistant - Phase 1 Advanced AI Brain Types
 * PRD Sections 1-68 compliant: Strict TypeScript definitions for
 * Multi-Agent System, Task Planning Engine, Self-Verification, Context Fusion,
 * Checkpoints, Recovery, and Scheduling.
 */

export type BrainTaskStatus =
  | 'QUEUED'
  | 'PLANNING'
  | 'READY'
  | 'RUNNING'
  | 'WAITING'
  | 'VERIFYING'
  | 'PAUSED'
  | 'FAILED'
  | 'RECOVERING'
  | 'COMPLETED'
  | 'CANCELLED';

export type BrainTaskPriority =
  | 'CRITICAL'
  | 'HIGH'
  | 'NORMAL'
  | 'LOW'
  | 'BACKGROUND';

export type AgentType =
  | 'planner'
  | 'researcher'
  | 'coder'
  | 'executor'
  | 'memory'
  | 'verifier';

export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';

export type VerificationLevel = 0 | 1 | 2 | 3 | 4;

export type VerificationStatus =
  | 'VERIFIED'
  | 'FAILED'
  | 'PARTIALLY_VERIFIED'
  | 'UNCERTAIN';

export interface VerificationResult {
  status: VerificationStatus;
  level: VerificationLevel;
  score: number; // 0 to 1
  checksPassed: string[];
  checksFailed: string[];
  feedback?: string;
  reasoning?: string;
  timestamp: number;
}

export type FailureType =
  | 'NETWORK_ERROR'
  | 'TIMEOUT'
  | 'INVALID_INPUT'
  | 'TOOL_ERROR'
  | 'MODEL_ERROR'
  | 'RESOURCE_ERROR'
  | 'DEPENDENCY_ERROR'
  | 'VERIFICATION_ERROR'
  | 'PERMISSION_ERROR'
  | 'UNKNOWN_ERROR';

export type RecoveryStrategy =
  | 'RETRY_IMMEDIATE'
  | 'RETRY_BACKOFF'
  | 'REPLAN'
  | 'ALTERNATIVE_ROUTE'
  | 'REQUEST_CLARIFICATION'
  | 'ABORT';

export interface TaskError {
  type: FailureType;
  message: string;
  rawError?: unknown;
  timestamp: number;
  recoveryAttempted: boolean;
  strategy?: RecoveryStrategy;
}

export interface TaskCheckpoint {
  checkpointId: string;
  taskId: string;
  timestamp: number;
  completedSubtaskIds: string[];
  pendingSubtaskIds: string[];
  intermediateOutputs: Record<string, unknown>;
  dependenciesState: Record<string, BrainTaskStatus>;
  retryState: {
    retryCount: number;
    lastError?: string;
  };
  verificationState?: VerificationResult;
  memoryReferences: string[];
}

export interface BrainTask {
  id: string;
  rootTaskId: string;
  parentTaskId?: string;
  title: string;
  description: string;
  status: BrainTaskStatus;
  priority: BrainTaskPriority;
  dependencies: string[]; // task IDs that must complete first
  assignedAgent: AgentType;
  input: Record<string, unknown>;
  expectedOutput?: Record<string, unknown> | string;
  output?: unknown;
  completionCriteria: string[];
  retryCount: number;
  maxRetries: number;
  createdAt: number;
  updatedAt: number;
  checkpoint?: TaskCheckpoint;
  error?: TaskError;
  verificationResult?: VerificationResult;
  metadata?: Record<string, unknown>;
}

export interface AgentResult<T = unknown> {
  success: boolean;
  data: T;
  confidence: ConfidenceLevel;
  evidence?: string[];
  warnings?: string[];
  errors?: string[];
  nextSuggestedAction?: string;
}

export interface ActionRequest {
  actionId: string;
  taskId: string;
  type: string;
  input: Record<string, unknown>;
  expectedOutput: Record<string, unknown> | string;
  riskLevel: 'safe' | 'low' | 'medium' | 'high' | 'critical';
  idempotencyKey?: string;
}

export interface ActionResult {
  actionId: string;
  taskId: string;
  success: boolean;
  output?: unknown;
  error?: string;
  timestamp: number;
}

export type BrainEventType =
  | 'AGENT_STARTED'
  | 'AGENT_COMPLETED'
  | 'TASK_CREATED'
  | 'TASK_UPDATED'
  | 'TASK_STATUS_CHANGED'
  | 'ACTION_REQUESTED'
  | 'ACTION_COMPLETED'
  | 'VERIFICATION_STARTED'
  | 'VERIFICATION_COMPLETED'
  | 'TASK_FAILED'
  | 'RECOVERY_STARTED'
  | 'TASK_RESUMED'
  | 'TASK_COMPLETED'
  | 'TASK_CANCELLED'
  | 'TASK_PAUSED';

export interface BrainEvent {
  id: string;
  type: BrainEventType;
  taskId?: string;
  agentType?: AgentType;
  timestamp: number;
  message: string;
  payload?: Record<string, unknown>;
}

export interface ContextFusionLayer {
  currentInstruction: string;
  currentTask?: BrainTask;
  recentConversation: Array<{ role: string; content: string }>;
  relevantMemories: Array<{ id: string; content: string; score: number }>;
  runtimeState: {
    isOnline: boolean;
    activeAgentsCount: number;
    pendingTasksCount: number;
  };
}

export interface BrainDiagnostics {
  activeTaskId?: string;
  activeAgent?: AgentType;
  totalTasks: number;
  runningTasks: number;
  completedTasks: number;
  failedTasks: number;
  retryCount: number;
  recoveryCount: number;
  lastVerification?: VerificationResult;
  concurrencyLimit: number;
}
