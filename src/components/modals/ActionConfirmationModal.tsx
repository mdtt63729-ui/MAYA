/**
 * Consequential Action Confirmation Modal
 * PRD Sections 39, 40, 65: Ensures critical device actions (WhatsApp send, phone dial)
 * receive explicit confirmation before execution.
 */

import React from 'react';
import { ShieldAlert, Check, X, Phone, MessageCircle } from 'lucide-react';
import { ActiveTask } from '../../types';

interface ActionConfirmationModalProps {
  task: ActiveTask | null;
  onConfirm: () => void;
  onCancel: () => void;
}

export const ActionConfirmationModal: React.FC<ActionConfirmationModalProps> = ({
  task,
  onConfirm,
  onCancel,
}) => {
  if (!task) return null;

  const isCall = task.goal.toLowerCase().includes('call');
  const isMessage = task.goal.toLowerCase().includes('message') || task.goal.toLowerCase().includes('whatsapp');

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in select-none">
      <div className="w-full max-w-sm rounded-3xl bg-[#0e131d] border border-slate-700/80 p-5 shadow-2xl space-y-4">
        {/* Header */}
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-2xl bg-cyan-950/80 border border-cyan-700/50 flex items-center justify-center text-cyan-400">
            {isCall ? (
              <Phone className="w-5 h-5" />
            ) : isMessage ? (
              <MessageCircle className="w-5 h-5" />
            ) : (
              <ShieldAlert className="w-5 h-5 text-amber-400" />
            )}
          </div>
          <div>
            <h3 className="text-sm font-semibold text-slate-100">
              Action Confirmation
            </h3>
            <span className="text-[11px] text-slate-400">
              Consequential device action
            </span>
          </div>
        </div>

        {/* Prompt */}
        <div className="p-3.5 rounded-2xl bg-slate-900/90 border border-slate-800 text-sm text-slate-200">
          <p className="font-medium">{task.confirmationPrompt || task.goal}</p>
        </div>

        {/* Step Breakdown */}
        <div className="space-y-1.5">
          <span className="text-[10px] uppercase tracking-wider font-semibold text-slate-500">
            Planned Steps
          </span>
          <div className="space-y-1">
            {task.steps.map((step) => (
              <div
                key={step.id}
                className="flex items-center justify-between text-xs py-1 px-2 rounded bg-slate-950/50 text-slate-300"
              >
                <span>{step.title}</span>
                <span className="text-[10px] font-mono text-cyan-400 uppercase">
                  {step.status}
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Tactile Action Buttons */}
        <div className="flex items-center space-x-2 pt-1">
          <button
            onClick={onCancel}
            className="flex-1 py-2.5 px-4 rounded-xl text-xs font-semibold text-slate-300 bg-slate-800/80 hover:bg-slate-800 active:scale-95 transition-all flex items-center justify-center space-x-1.5"
          >
            <X className="w-3.5 h-3.5" />
            <span>Cancel</span>
          </button>

          <button
            onClick={onConfirm}
            className="flex-1 py-2.5 px-4 rounded-xl text-xs font-semibold text-slate-950 bg-cyan-400 hover:bg-cyan-300 active:scale-95 transition-all shadow-lg shadow-cyan-950 flex items-center justify-center space-x-1.5"
          >
            <Check className="w-3.5 h-3.5 stroke-[3]" />
            <span>Confirm & Execute</span>
          </button>
        </div>
      </div>
    </div>
  );
};
