import React, { useEffect } from 'react';
import { ExternalLink, Sparkles, X } from 'lucide-react';
import { ToolCallPayload } from '../../services/liveSession';

interface ActionNotificationProps {
  toolCall: ToolCallPayload | null;
  onDismiss: () => void;
}

export const ActionNotification: React.FC<ActionNotificationProps> = ({ toolCall, onDismiss }) => {
  useEffect(() => {
    if (!toolCall) return;
    const timer = setTimeout(() => {
      onDismiss();
    }, 6000);
    return () => clearTimeout(timer);
  }, [toolCall, onDismiss]);

  if (!toolCall) return null;

  const url = (toolCall.args.url as string) || '';
  const query = (toolCall.args.query as string) || '';

  return (
    <div className="fixed top-20 left-1/2 transform -translate-x-1/2 z-50 w-11/12 max-w-md animate-in fade-in slide-in-from-top-4 duration-300">
      <div className="rounded-2xl p-3.5 bg-slate-900/95 border border-pink-500/40 shadow-[0_0_30px_rgba(244,63,94,0.25)] backdrop-blur-xl flex items-center justify-between text-slate-100">
        <div className="flex items-center space-x-3 overflow-hidden">
          <div className="w-8 h-8 rounded-full bg-gradient-to-tr from-pink-500 to-purple-600 flex items-center justify-center shrink-0 shadow-md">
            <Sparkles className="w-4 h-4 text-white" />
          </div>
          <div className="truncate text-left">
            <span className="text-[10px] font-mono uppercase tracking-widest text-pink-400 font-semibold block">
              MJ Browser Action
            </span>
            <p className="text-xs font-medium text-slate-200 truncate">
              {toolCall.name === 'openWebsite'
                ? `Opening: ${url}`
                : toolCall.name === 'searchWeb'
                ? `Searching: "${query}"`
                : `Executed: ${toolCall.name}`}
            </p>
          </div>
        </div>

        <div className="flex items-center space-x-2 shrink-0 ml-2">
          {url && (
            <a
              href={url.startsWith('http') ? url : `https://${url}`}
              target="_blank"
              rel="noreferrer"
              className="p-1.5 rounded-lg bg-pink-500/20 text-pink-300 hover:bg-pink-500/30 border border-pink-500/30 transition-colors"
              title="Open Website"
            >
              <ExternalLink className="w-3.5 h-3.5" />
            </a>
          )}
          <button
            onClick={onDismiss}
            className="p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800/60 transition-colors"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
};
