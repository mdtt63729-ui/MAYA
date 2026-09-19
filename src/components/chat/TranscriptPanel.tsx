/**
 * MJ Assistant Transcript & Response Cards
 * Flagship Material 3 dark surface cards with citations, action status, and audio playback
 */

import React from 'react';
import { Volume2, VolumeX, ExternalLink, MapPin, CheckCircle, Sparkles } from 'lucide-react';
import { ChatMessage, IndianLanguageCode } from '../../types';
import { audioEngine } from '../../services/audioEngine';
import { TaskDetails } from '../../ui/TaskDetails';

interface TranscriptPanelProps {
  messages: ChatMessage[];
  currentLanguage: IndianLanguageCode;
  onSelectPrompt?: (prompt: string) => void;
}

export const TranscriptPanel: React.FC<TranscriptPanelProps> = ({
  messages,
  currentLanguage,
  onSelectPrompt,
}) => {
  const [playingMessageId, setPlayingMessageId] = React.useState<string | null>(null);

  const handleSpeak = async (msg: ChatMessage) => {
    if (playingMessageId === msg.id) {
      audioEngine.interrupt();
      setPlayingMessageId(null);
      return;
    }

    setPlayingMessageId(msg.id);
    if (msg.audioUrl) {
      await audioEngine.playAudio(msg.audioUrl);
    } else {
      await audioEngine.speakLocalTTS(msg.content, msg.language || currentLanguage);
    }
    setPlayingMessageId(null);
  };

  if (messages.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center p-6 text-center max-w-md mx-auto">
        <p className="text-slate-400 text-sm font-medium">
          Say <span className="text-cyan-400 font-semibold">“Hi MJ”</span>, tap the microphone, or ask to launch an app, search, or translate.
        </p>
        <div className="mt-4 flex flex-wrap justify-center gap-2">
          {['Hi MJ', 'Open YouTube', 'WhatsApp kholo', 'Weather in Kolkata', 'Search Android 16 news'].map(
            (sample) => (
              <button
                key={sample}
                onClick={() => onSelectPrompt?.(sample)}
                className="text-xs px-3 py-1.5 rounded-full bg-slate-900/80 text-slate-300 border border-slate-800 hover:border-cyan-500/40 hover:text-cyan-300 transition-colors"
              >
                {sample}
              </button>
            )
          )}
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3 max-w-2xl mx-auto w-full">
      {messages.map((msg) => {
        const isAssistant = msg.role === 'assistant';

        return (
          <div
            key={msg.id}
            className={`flex flex-col ${isAssistant ? 'items-start' : 'items-end'}`}
          >
            {/* Sender Label */}
            <span className="text-[10px] uppercase tracking-wider font-semibold text-slate-500 mb-1 px-1">
              {isAssistant ? 'MJ' : 'You'}
            </span>

            {/* Message Card */}
            <div
              className={`relative max-w-[90%] sm:max-w-[85%] rounded-2xl p-3.5 transition-all ${
                isAssistant
                  ? 'bg-slate-900/80 border border-slate-800/90 text-slate-100 backdrop-blur-sm'
                  : 'bg-cyan-950/40 border border-cyan-800/40 text-cyan-50'
              }`}
            >
              {/* Thinking mode breakdown if present */}
              {msg.thinkingContent && (
                <div className="mb-2 p-2 rounded-lg bg-slate-950/60 border border-indigo-900/30 text-[11px] text-indigo-300 flex items-start space-x-1.5">
                  <Sparkles className="w-3.5 h-3.5 text-indigo-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-semibold block text-indigo-200">High-Thinking Mode</span>
                    <p className="line-clamp-3 text-slate-400 font-mono text-[10px]">
                      {msg.thinkingContent}
                    </p>
                  </div>
                </div>
              )}

              {/* Message Content */}
              <p className="text-sm leading-relaxed whitespace-pre-wrap select-text">
                {msg.content}
              </p>

              {/* Advanced AI Brain Task Graph Details */}
              {msg.brainTasks && msg.brainTasks.length > 0 && (
                <div className="mt-3">
                  <TaskDetails tasks={msg.brainTasks} />
                </div>
              )}

              {/* Action Verification Status */}
              {msg.actionResult && (
                <div className="mt-2 pt-2 border-t border-slate-800/80 flex items-center space-x-1.5 text-xs text-emerald-400">
                  <CheckCircle className="w-3.5 h-3.5" />
                  <span>{msg.actionResult.message}</span>
                </div>
              )}

              {/* Grounding Sources (Search Grounding) */}
              {msg.groundingSources && msg.groundingSources.length > 0 && (
                <div className="mt-2.5 pt-2 border-t border-slate-800/80">
                  <span className="text-[10px] text-slate-400 font-medium block mb-1">
                    Grounded Search Sources:
                  </span>
                  <div className="flex flex-wrap gap-1.5">
                    {msg.groundingSources.map((source, sIdx) => (
                      <a
                        key={sIdx}
                        href={source.url}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center space-x-1 text-[10px] px-2 py-0.5 rounded bg-slate-950/80 border border-slate-700/60 text-cyan-300 hover:border-cyan-400 transition-colors"
                      >
                        <ExternalLink className="w-2.5 h-2.5" />
                        <span className="max-w-[140px] truncate">{source.title || 'Source'}</span>
                      </a>
                    ))}
                  </div>
                </div>
              )}

              {/* Maps Places Grounding */}
              {msg.mapsPlaces && msg.mapsPlaces.length > 0 && (
                <div className="mt-2.5 pt-2 border-t border-slate-800/80">
                  <span className="text-[10px] text-slate-400 font-medium block mb-1">
                    Google Maps Places:
                  </span>
                  <div className="space-y-1">
                    {msg.mapsPlaces.map((place, pIdx) => (
                      <div
                        key={pIdx}
                        className="flex items-center justify-between text-[11px] p-1.5 rounded bg-slate-950/70 border border-slate-800"
                      >
                        <div className="flex items-center space-x-1.5 truncate">
                          <MapPin className="w-3 h-3 text-rose-400 shrink-0" />
                          <span className="text-slate-200 font-medium truncate">{place.name}</span>
                        </div>
                        {place.rating && (
                          <span className="text-[10px] text-amber-400 ml-2 shrink-0">
                            ★ {place.rating}
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Spoken Voice Playback Button */}
              {isAssistant && (
                <div className="mt-2 pt-1 flex items-center justify-end">
                  <button
                    onClick={() => handleSpeak(msg)}
                    className="p-1 rounded-md text-slate-400 hover:text-cyan-300 hover:bg-slate-800/50 transition-colors"
                    title={playingMessageId === msg.id ? 'Stop Speech' : 'Listen with Native Accent'}
                  >
                    {playingMessageId === msg.id ? (
                      <VolumeX className="w-3.5 h-3.5 text-cyan-400 animate-pulse" />
                    ) : (
                      <Volume2 className="w-3.5 h-3.5" />
                    )}
                  </button>
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};
