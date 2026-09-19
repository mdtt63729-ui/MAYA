/**
 * TaskDetails
 * PRD Sections 39-41: Transparent inspection panel for Multi-Agent Task DAG,
 * criteria checklists, verification scoring, and failure recovery traces.
 */

import React, { useState } from 'react';
import {
  CheckCircle2,
  Clock,
  AlertCircle,
  ShieldCheck,
  ChevronDown,
  ChevronRight,
  RotateCcw,
  Bot,
  Activity,
} from 'lucide-react';
import { BrainTask, BrainTaskStatus, AgentType } from '../core/brainTypes';

interface TaskDetailsProps {
  tasks: BrainTask[];
  onCancel?: () => void;
}

export const TaskDetails: React.FC<TaskDetailsProps> = ({ tasks, onCancel }) => {
  const [isExpanded, setIsExpanded] = useState(true);

  if (!tasks || tasks.length === 0) return null;

  const agentColors: Record<AgentType, string> = {
    planner: 'bg-purple-500/20 text-purple-300 border-purple-500/30',
    researcher: 'bg-blue-500/20 text-blue-300 border-blue-500/30',
    coder: 'bg-cyan-500/20 text-cyan-300 border-cyan-500/30',
    executor: 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30',
    memory: 'bg-amber-500/20 text-amber-300 border-amber-500/30',
    verifier: 'bg-indigo-500/20 text-indigo-300 border-indigo-500/30',
  };

  const statusIcons: Record<BrainTaskStatus, React.ReactNode> = {
    QUEUED: <Clock className="w-3.5 h-3.5 text-zinc-500" />,
    PLANNING: <Activity className="w-3.5 h-3.5 text-cyan-400 animate-spin" />,
    READY: <Clock className="w-3.5 h-3.5 text-cyan-300" />,
    RUNNING: <Activity className="w-3.5 h-3.5 text-emerald-400 animate-spin" />,
    WAITING: <Clock className="w-3.5 h-3.5 text-amber-400" />,
    VERIFYING: <ShieldCheck className="w-3.5 h-3.5 text-indigo-400 animate-pulse" />,
    PAUSED: <Clock className="w-3.5 h-3.5 text-zinc-400" />,
    FAILED: <AlertCircle className="w-3.5 h-3.5 text-rose-400" />,
    RECOVERING: <RotateCcw className="w-3.5 h-3.5 text-rose-300 animate-spin" />,
    COMPLETED: <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />,
    CANCELLED: <AlertCircle className="w-3.5 h-3.5 text-zinc-500" />,
  };

  return (
    <div id="brain-task-details-card" className="w-full bg-zinc-900/80 border border-zinc-800 rounded-xl overflow-hidden shadow-lg backdrop-blur-md">
      {/* Header Bar */}
      <div
        onClick={() => setIsExpanded(!isExpanded)}
        className="flex items-center justify-between px-3.5 py-2.5 bg-zinc-850 hover:bg-zinc-800/80 cursor-pointer transition-colors"
      >
        <div className="flex items-center gap-2">
          <Bot className="w-4 h-4 text-cyan-400" />
          <span className="text-xs font-semibold text-zinc-200">Execution DAG & Agent Pipeline</span>
          <span className="text-[10px] px-2 py-0.5 rounded-full bg-zinc-800 text-zinc-400 font-mono">
            {tasks.length} Subtasks
          </span>
        </div>

        <div className="flex items-center gap-2">
          {onCancel && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onCancel();
              }}
              className="px-2 py-0.5 text-[11px] bg-rose-500/20 hover:bg-rose-500/30 text-rose-300 rounded transition-colors"
            >
              Cancel Plan
            </button>
          )}
          {isExpanded ? (
            <ChevronDown className="w-4 h-4 text-zinc-400" />
          ) : (
            <ChevronRight className="w-4 h-4 text-zinc-400" />
          )}
        </div>
      </div>

      {/* Expanded Subtask List */}
      {isExpanded && (
        <div className="p-3 space-y-2 max-h-72 overflow-y-auto">
          {tasks.map((task) => {
            return (
              <div
                key={task.id}
                className={`p-2.5 rounded-lg border transition-all ${
                  task.status === 'RUNNING'
                    ? 'border-emerald-500/40 bg-emerald-950/20'
                    : task.status === 'VERIFYING'
                    ? 'border-indigo-500/40 bg-indigo-950/20'
                    : task.status === 'RECOVERING'
                    ? 'border-rose-500/40 bg-rose-950/20'
                    : task.status === 'COMPLETED'
                    ? 'border-zinc-800 bg-zinc-900/40'
                    : 'border-zinc-800/60 bg-zinc-900/20 opacity-70'
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 overflow-hidden">
                    {statusIcons[task.status]}
                    <span className="text-xs font-medium text-zinc-200 truncate">{task.title}</span>
                  </div>

                  <div className="flex items-center gap-1.5 shrink-0">
                    {/* Agent Badge */}
                    <span
                      className={`text-[10px] px-1.5 py-0.5 rounded border uppercase font-mono font-medium ${
                        agentColors[task.assignedAgent]
                      }`}
                    >
                      {task.assignedAgent}
                    </span>

                    {/* Status badge */}
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-300 font-mono">
                      {task.status}
                    </span>
                  </div>
                </div>

                {/* Criteria Checklist */}
                {task.completionCriteria && task.completionCriteria.length > 0 && (
                  <div className="mt-2 pl-5 space-y-1">
                    {task.completionCriteria.map((crit, cIdx) => (
                      <div key={cIdx} className="text-[11px] text-zinc-400 flex items-center gap-1.5">
                        <span className="w-1 h-1 rounded-full bg-zinc-600" />
                        <span>{crit}</span>
                      </div>
                    ))}
                  </div>
                )}

                {/* Verification Outcome */}
                {task.verificationResult && (
                  <div className="mt-2 pt-1.5 border-t border-zinc-800/60 flex items-center justify-between text-[11px]">
                    <span className="text-zinc-400 flex items-center gap-1">
                      <ShieldCheck className="w-3 h-3 text-indigo-400" />
                      Verification: {task.verificationResult.status} (L{task.verificationResult.level})
                    </span>
                    <span className="font-mono text-indigo-300 font-semibold">
                      {Math.round(task.verificationResult.score * 100)}% score
                    </span>
                  </div>
                )}

                {/* Error / Recovery indicator */}
                {task.error && (
                  <div className="mt-1.5 text-[11px] text-rose-300 bg-rose-950/40 px-2 py-1 rounded border border-rose-900/50">
                    <span className="font-semibold">[{task.error.type}]:</span> {task.error.message}
                    {task.retryCount > 0 && ` (Retry #${task.retryCount})`}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
