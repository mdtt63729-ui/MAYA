/**
 * TaskProgress
 * PRD Section 38: "No Fake Progress" rule.
 * Visualizes authentic mathematical progress based on completed subtasks in the DAG.
 */

import React from 'react';
import { BrainTask } from '../core/brainTypes';

interface TaskProgressProps {
  tasks: BrainTask[];
}

export const TaskProgress: React.FC<TaskProgressProps> = ({ tasks }) => {
  if (!tasks || tasks.length === 0) return null;

  const total = tasks.length;
  const completed = tasks.filter((t) => t.status === 'COMPLETED').length;
  const failed = tasks.filter((t) => t.status === 'FAILED').length;
  const inProgress = tasks.filter(
    (t) => t.status === 'RUNNING' || t.status === 'VERIFYING' || t.status === 'RECOVERING'
  ).length;

  const percentage = Math.round((completed / total) * 100);

  return (
    <div id="brain-task-progress" className="w-full space-y-1.5 px-3 py-2 bg-zinc-900/60 rounded-lg border border-zinc-800">
      <div className="flex items-center justify-between text-[11px] text-zinc-400">
        <span className="font-medium">
          Task Graph: {completed}/{total} Subtasks Completed
        </span>
        <span className="font-mono text-cyan-400 font-semibold">{percentage}%</span>
      </div>

      {/* Synchronized Bar */}
      <div className="w-full h-1.5 bg-zinc-800 rounded-full overflow-hidden flex">
        <div
          className="h-full bg-emerald-500 transition-all duration-300"
          style={{ width: `${(completed / total) * 100}%` }}
        />
        {inProgress > 0 && (
          <div
            className="h-full bg-cyan-400 animate-pulse transition-all duration-300"
            style={{ width: `${(inProgress / total) * 100}%` }}
          />
        )}
        {failed > 0 && (
          <div
            className="h-full bg-rose-500 transition-all duration-300"
            style={{ width: `${(failed / total) * 100}%` }}
          />
        )}
      </div>
    </div>
  );
};
