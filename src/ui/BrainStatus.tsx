/**
 * BrainStatus
 * PRD Section 37 & 41: Real-time visual status indicator of the AI Brain Pipeline:
 * Understand → Plan → Reason → Execute → Verify → Recover → Remember.
 */

import React from 'react';
import { Brain, CheckCircle2, AlertTriangle, ShieldCheck, Cpu, RefreshCw } from 'lucide-react';
import { ExecutionProgressEvent } from '../core/orchestrator/MJOrchestrator';

interface BrainStatusProps {
  progress?: ExecutionProgressEvent | null;
  onCancel?: () => void;
}

export const BrainStatus: React.FC<BrainStatusProps> = ({ progress, onCancel }) => {
  if (!progress || progress.stage === 'DONE') return null;

  const stageIcons = {
    UNDERSTAND: <Brain className="w-3.5 h-3.5 animate-pulse text-indigo-400" />,
    PLAN: <Cpu className="w-3.5 h-3.5 animate-pulse text-cyan-400" />,
    REASON: <Cpu className="w-3.5 h-3.5 text-blue-400" />,
    EXECUTE: <RefreshCw className="w-3.5 h-3.5 animate-spin text-emerald-400" />,
    VERIFY: <ShieldCheck className="w-3.5 h-3.5 animate-pulse text-amber-400" />,
    RECOVER: <AlertTriangle className="w-3.5 h-3.5 animate-bounce text-rose-400" />,
    REMEMBER: <Brain className="w-3.5 h-3.5 text-purple-400" />,
    DONE: <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />,
  };

  const stageColors = {
    UNDERSTAND: 'border-indigo-500/30 bg-indigo-500/10 text-indigo-300',
    PLAN: 'border-cyan-500/30 bg-cyan-500/10 text-cyan-300',
    REASON: 'border-blue-500/30 bg-blue-500/10 text-blue-300',
    EXECUTE: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300',
    VERIFY: 'border-amber-500/30 bg-amber-500/10 text-amber-300',
    RECOVER: 'border-rose-500/30 bg-rose-500/10 text-rose-300',
    REMEMBER: 'border-purple-500/30 bg-purple-500/10 text-purple-300',
    DONE: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300',
  };

  return (
    <div
      id="brain-status-banner"
      className={`flex items-center justify-between px-3 py-1.5 rounded-lg border text-xs font-mono transition-all backdrop-blur-md shadow-sm ${
        stageColors[progress.stage]
      }`}
    >
      <div className="flex items-center gap-2 overflow-hidden">
        {stageIcons[progress.stage]}
        <span className="font-semibold uppercase tracking-wider">{progress.stage}</span>
        <span className="text-zinc-400 truncate max-w-[280px] sm:max-w-md">{progress.message}</span>
      </div>

      {onCancel && (
        <button
          id="cancel-brain-task-btn"
          onClick={onCancel}
          className="ml-2 px-2 py-0.5 rounded bg-rose-500/20 hover:bg-rose-500/30 text-rose-300 text-[11px] font-sans transition-colors cursor-pointer"
        >
          Cancel
        </button>
      )}
    </div>
  );
};
