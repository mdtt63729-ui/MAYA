/**
 * Floating MJ Orb Android Overlay
 * PRD Sections 50-53 & v5.1 Sections 30-32: Draggable mini-orb that stays on top
 * across applications and responds to voice with quick expand gestures.
 */

import React, { useState, useRef } from 'react';
import { MJState } from '../../types';
import { MJOrb } from './MJOrb';
import { Maximize2, Mic, X } from 'lucide-react';

interface FloatingOverlayProps {
  state: MJState;
  onExpand: () => void;
  onToggleMic: () => void;
  onClose: () => void;
}

export const FloatingOverlay: React.FC<FloatingOverlayProps> = ({
  state,
  onExpand,
  onToggleMic,
  onClose,
}) => {
  const [pos, setPos] = useState<{ x: number; y: number }>({ x: 20, y: 120 });
  const isDraggingRef = useRef<boolean>(false);
  const startPosRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });

  const handlePointerDown = (e: React.PointerEvent) => {
    isDraggingRef.current = true;
    startPosRef.current = {
      x: e.clientX - pos.x,
      y: e.clientY - pos.y,
    };
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!isDraggingRef.current) return;
    setPos({
      x: Math.max(10, Math.min(window.innerWidth - 90, e.clientX - startPosRef.current.x)),
      y: Math.max(10, Math.min(window.innerHeight - 90, e.clientY - startPosRef.current.y)),
    });
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    isDraggingRef.current = false;
    try {
      (e.target as HTMLElement).releasePointerCapture(e.pointerId);
    } catch {}
  };

  return (
    <div
      style={{ left: `${pos.x}px`, top: `${pos.y}px` }}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      className="fixed z-50 flex items-center justify-center select-none touch-none cursor-grab active:cursor-grabbing transition-transform active:scale-95"
    >
      <div className="relative group p-1 rounded-full bg-[#080a0f]/90 backdrop-blur-md border border-cyan-500/50 shadow-2xl shadow-cyan-950/60">
        <MJOrb state={state} size="mini" interactive={false} />

        {/* Hover Quick Actions */}
        <div className="absolute -top-7 right-0 hidden group-hover:flex items-center space-x-1 p-1 rounded-full bg-slate-900 border border-slate-700 shadow-lg">
          <button
            onClick={(e) => {
              e.stopPropagation();
              onToggleMic();
            }}
            className="p-1 rounded-full text-cyan-400 hover:bg-slate-800"
            title="Toggle Mic"
          >
            <Mic className="w-3 h-3" />
          </button>
          <button
            onClick={(e) => {
              e.stopPropagation();
              onExpand();
            }}
            className="p-1 rounded-full text-slate-300 hover:bg-slate-800"
            title="Expand Fullscreen"
          >
            <Maximize2 className="w-3 h-3" />
          </button>
          <button
            onClick={(e) => {
              e.stopPropagation();
              onClose();
            }}
            className="p-1 rounded-full text-rose-400 hover:bg-slate-800"
            title="Close Overlay"
          >
            <X className="w-3 h-3" />
          </button>
        </div>
      </div>
    </div>
  );
};
