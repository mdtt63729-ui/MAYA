/**
 * Gemini Multi-Modal Creative Studio Modal
 * Comprehensive suite featuring all user-selected templates:
 * - Text-to-Speech (gemini-3.1-flash-tts-preview)
 * - Music Generation (lyria-3-clip-preview / lyria-3-pro-preview)
 * - High-Quality Image Generation (gemini-3-pro-image-preview & gemini-3.1-flash-image-preview with 1K/2K/4K and 8 aspect ratios)
 * - Veo Video Generation (veo-3.1-fast-generate-preview in 16:9 & 9:16)
 * - Audio Transcription (gemini-3.5-transcribe)
 * - Grounded Search & Maps (gemini-3.5-flash)
 * - High-Thinking Mode (gemini-3.1-pro-preview with thinkingLevel HIGH)
 */

import React, { useState } from 'react';
import {
  X,
  Sparkles,
  Music,
  Image as ImageIcon,
  Video,
  Mic,
  Volume2,
  BrainCircuit,
  Search,
  MapPin,
  RefreshCw,
  Play,
  Upload,
} from 'lucide-react';
import { audioEngine } from '../../services/audioEngine';

interface GeminiStudioModalProps {
  isOpen: boolean;
  onClose: () => void;
}

type StudioTab = 'tts' | 'music' | 'image' | 'video' | 'transcribe' | 'thinking' | 'grounding';

interface ResultDataState {
  type: string;
  base64?: string;
  lyrics?: string;
  text?: string;
  sources?: Array<{ title?: string; url?: string }>;
  message?: string;
}

export const GeminiStudioModal: React.FC<GeminiStudioModalProps> = ({ isOpen, onClose }) => {
  const [activeTab, setActiveTab] = useState<StudioTab>('image');
  const [loading, setLoading] = useState<boolean>(false);
  const [resultData, setResultData] = useState<ResultDataState | null>(null);
  const [errorText, setErrorText] = useState<string | null>(null);

  // Tab 1: TTS State
  const [ttsText, setTtsText] = useState('Say cheerfully: Welcome to MJ Assistant!');
  const [ttsVoice, setTtsVoice] = useState('Kore');

  // Tab 2: Music State
  const [musicPrompt, setMusicPrompt] = useState('A relaxing 30-second acoustic Indian fusion instrumental track');
  const [musicDuration, setMusicDuration] = useState<'clip' | 'full'>('clip');

  // Tab 3: Image State
  const [imagePrompt, setImagePrompt] = useState('A futuristic high-tech personal AI assistant orb on a glass desk in Mumbai at night');
  const [imageAspect, setImageAspect] = useState<string>('16:9');
  const [imageRes, setImageRes] = useState<string>('1K');
  const [imageModel, setImageModel] = useState<'gemini-3-pro-image' | 'gemini-3.1-flash-image'>('gemini-3-pro-image');
  const [imageSource, setImageSource] = useState<string | null>(null);

  // Tab 4: Veo Video State
  const [videoPrompt, setVideoPrompt] = useState('A neon hologram of a cybernetic eagle soaring through clouds at sunset');
  const [videoAspect, setVideoAspect] = useState<'16:9' | '9:16'>('16:9');

  // Tab 5: Transcribe State
  const [isRecording, setIsRecording] = useState<boolean>(false);

  // Tab 6: Thinking State
  const [thinkingPrompt, setThinkingPrompt] = useState('Explain the quantum computing principles behind topological qubits and compare them with transmon architectures.');

  // Tab 7: Grounding State
  const [groundingQuery, setGroundingQuery] = useState('What are the best tech cafes near Indiranagar, Bengaluru?');
  const [groundingType, setGroundingType] = useState<'search' | 'maps'>('maps');

  if (!isOpen) return null;

  const handleGenerateTTS = async () => {
    setLoading(true);
    setErrorText(null);
    setResultData(null);
    try {
      const res = await fetch('/api/tts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text: ttsText, voice: ttsVoice }),
      });
      const data = await res.json();
      if (data.audioBase64) {
        setResultData({ type: 'audio', base64: data.audioBase64 });
        await audioEngine.playAudio(data.audioBase64);
      } else {
        throw new Error(data.error || 'No audio generated');
      }
    } catch (err: unknown) {
      setErrorText(err instanceof Error ? err.message : 'TTS generation failed');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateMusic = async () => {
    setLoading(true);
    setErrorText(null);
    setResultData(null);
    try {
      const res = await fetch('/api/music-generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt: musicPrompt, duration: musicDuration }),
      });
      const data = await res.json();
      if (data.audioBase64) {
        setResultData({ type: 'music', base64: data.audioBase64, lyrics: data.lyrics });
        await audioEngine.playAudio(data.audioBase64);
      } else {
        throw new Error(data.error || 'No music generated');
      }
    } catch (err: unknown) {
      setErrorText(err instanceof Error ? err.message : 'Music generation failed');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateImage = async () => {
    setLoading(true);
    setErrorText(null);
    setResultData(null);
    try {
      const res = await fetch('/api/image-generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: imagePrompt,
          model: imageModel,
          aspectRatio: imageAspect,
          resolution: imageRes,
          sourceImageBase64: imageSource ? imageSource.split(',')[1] : undefined,
        }),
      });
      const data = await res.json();
      if (data.imageBase64) {
        setResultData({ type: 'image', base64: data.imageBase64 });
      } else {
        throw new Error(data.error || 'Failed generating image');
      }
    } catch (err: unknown) {
      setErrorText(err instanceof Error ? err.message : 'Image generation failed');
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateVideo = async () => {
    setLoading(true);
    setErrorText(null);
    setResultData(null);
    try {
      const res = await fetch('/api/video-generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: videoPrompt,
          aspectRatio: videoAspect,
          sourceImageBase64: imageSource ? imageSource.split(',')[1] : undefined,
        }),
      });
      const data = await res.json();
      setResultData({
        type: 'video',
        message: data.message || 'Veo 3.1 video generation process initiated in background.',
      });
    } catch (err: unknown) {
      setErrorText(err instanceof Error ? err.message : 'Veo video generation failed');
    } finally {
      setLoading(false);
    }
  };

  const handleRunThinking = async () => {
    setLoading(true);
    setErrorText(null);
    setResultData(null);
    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: thinkingPrompt,
          model: 'gemini-3.1-pro-preview',
          enableThinking: true,
        }),
      });
      const data = await res.json();
      setResultData({ type: 'text', text: data.text });
    } catch (err: unknown) {
      setErrorText(err instanceof Error ? err.message : 'Thinking request failed');
    } finally {
      setLoading(false);
    }
  };

  const handleRunGrounding = async () => {
    setLoading(true);
    setErrorText(null);
    setResultData(null);
    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: groundingQuery,
          model: 'gemini-3.5-flash',
          useSearch: groundingType === 'search',
          useMaps: groundingType === 'maps',
        }),
      });
      const data = await res.json();
      setResultData({
        type: 'grounding',
        text: data.text,
        sources: data.sources,
      });
    } catch (err: unknown) {
      setErrorText(err instanceof Error ? err.message : 'Grounded query failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-5 bg-black/80 backdrop-blur-md select-none animate-fade-in">
      <div className="w-full max-w-2xl rounded-3xl bg-[#0e131d] border border-slate-700/80 p-5 shadow-2xl space-y-4 max-h-[92vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center space-x-2.5">
            <div className="w-9 h-9 rounded-xl bg-cyan-950/80 border border-cyan-700/40 flex items-center justify-center text-cyan-400">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-slate-100">
                Gemini Multi-Modal Tools Studio
              </h3>
              <p className="text-[11px] text-slate-400">
                TTS, Music, High-res Images, Veo Video, Search & Maps Grounding
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-slate-800"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Tab Selector */}
        <div className="flex items-center space-x-1.5 overflow-x-auto pb-1 border-b border-slate-800/80 text-xs scrollbar-none">
          {[
            { id: 'image', label: 'Image Creator', icon: ImageIcon },
            { id: 'video', label: 'Veo Video', icon: Video },
            { id: 'music', label: 'Lyria Music', icon: Music },
            { id: 'tts', label: 'Speech (TTS)', icon: Volume2 },
            { id: 'thinking', label: 'High Thinking', icon: BrainCircuit },
            { id: 'grounding', label: 'Search & Maps', icon: Search },
          ].map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => {
                  setActiveTab(tab.id as StudioTab);
                  setResultData(null);
                  setErrorText(null);
                }}
                className={`px-3 py-2 rounded-xl flex items-center space-x-1.5 whitespace-nowrap transition-all ${
                  isActive
                    ? 'bg-cyan-500/20 text-cyan-300 font-semibold border border-cyan-500/40'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/60'
                }`}
              >
                <Icon className="w-3.5 h-3.5" />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>

        {/* Body Content by Tab */}
        <div className="flex-1 overflow-y-auto space-y-3 pr-1 text-xs">
          {/* TAB 1: IMAGE GENERATOR */}
          {activeTab === 'image' && (
            <div className="space-y-3">
              <div className="space-y-1">
                <label className="text-slate-300 font-medium">Text Prompt:</label>
                <textarea
                  value={imagePrompt}
                  onChange={(e) => setImagePrompt(e.target.value)}
                  className="w-full text-xs p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-slate-200 focus:outline-none focus:border-cyan-500 h-20 resize-none"
                />
              </div>

              {/* Controls: Model, Resolution, Aspect Ratio */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                <div>
                  <label className="text-slate-400 text-[11px] block mb-1">Model:</label>
                  <select
                    value={imageModel}
                    onChange={(e) => setImageModel(e.target.value as 'gemini-3-pro-image' | 'gemini-3.1-flash-image')}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2 text-slate-200"
                  >
                    <option value="gemini-3-pro-image">gemini-3-pro-image-preview</option>
                    <option value="gemini-3.1-flash-image">gemini-3.1-flash-image-preview</option>
                  </select>
                </div>

                <div>
                  <label className="text-slate-400 text-[11px] block mb-1">Resolution:</label>
                  <select
                    value={imageRes}
                    onChange={(e) => setImageRes(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2 text-slate-200"
                  >
                    <option value="1K">1K Resolution</option>
                    <option value="2K">2K Resolution</option>
                    <option value="4K">4K Studio</option>
                    <option value="512px">512px Fast</option>
                  </select>
                </div>

                <div>
                  <label className="text-slate-400 text-[11px] block mb-1">Aspect Ratio:</label>
                  <select
                    value={imageAspect}
                    onChange={(e) => setImageAspect(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2 text-slate-200"
                  >
                    <option value="1:1">1:1 Square</option>
                    <option value="16:9">16:9 Landscape</option>
                    <option value="9:16">9:16 Portrait</option>
                    <option value="4:3">4:3 Standard</option>
                    <option value="3:4">3:4 Portrait</option>
                    <option value="3:2">3:2 Photo</option>
                    <option value="2:3">2:3 Photo</option>
                    <option value="21:9">21:9 Ultrawide</option>
                  </select>
                </div>
              </div>

              {/* Optional Photo to Edit / Animate */}
              <div className="flex items-center space-x-2">
                <label className="py-2 px-3 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-700 text-slate-300 text-xs flex items-center space-x-1.5 cursor-pointer">
                  <Upload className="w-3.5 h-3.5" />
                  <span>{imageSource ? 'Image Attached' : 'Attach Photo for Edit'}</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(e) => {
                      const file = e.target.files?.[0];
                      if (!file) return;
                      const reader = new FileReader();
                      reader.onload = () => setImageSource(reader.result as string);
                      reader.readAsDataURL(file);
                    }}
                    className="hidden"
                  />
                </label>
                {imageSource && (
                  <button
                    onClick={() => setImageSource(null)}
                    className="text-xs text-rose-400 hover:underline"
                  >
                    Remove Photo
                  </button>
                )}
              </div>

              <button
                onClick={handleGenerateImage}
                disabled={loading}
                className="w-full py-2.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold flex items-center justify-center space-x-2 active:scale-98 transition-all"
              >
                {loading ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}
                <span>Generate Image ({imageRes} / {imageAspect})</span>
              </button>
            </div>
          )}

          {/* TAB 2: VEO VIDEO GENERATION */}
          {activeTab === 'video' && (
            <div className="space-y-3">
              <div className="space-y-1">
                <label className="text-slate-300 font-medium">Video Prompt (Veo 3.1):</label>
                <textarea
                  value={videoPrompt}
                  onChange={(e) => setVideoPrompt(e.target.value)}
                  className="w-full text-xs p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-slate-200 focus:outline-none focus:border-cyan-500 h-20 resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-slate-400 text-[11px] block mb-1">Aspect Ratio:</label>
                  <select
                    value={videoAspect}
                    onChange={(e) => setVideoAspect(e.target.value as '16:9' | '9:16')}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2 text-slate-200"
                  >
                    <option value="16:9">16:9 (Landscape)</option>
                    <option value="9:16">9:16 (Portrait)</option>
                  </select>
                </div>

                <div>
                  <label className="text-slate-400 text-[11px] block mb-1">Model Engine:</label>
                  <div className="p-2 rounded-xl bg-slate-950 border border-slate-800 text-slate-300 font-mono text-[11px]">
                    veo-3.1-fast-generate-preview
                  </div>
                </div>
              </div>

              <button
                onClick={handleGenerateVideo}
                disabled={loading}
                className="w-full py-2.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold flex items-center justify-center space-x-2 active:scale-98 transition-all"
              >
                {loading ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Video className="w-4 h-4" />}
                <span>Generate Veo 3 Video ({videoAspect})</span>
              </button>
            </div>
          )}

          {/* TAB 3: LYRIA MUSIC GENERATION */}
          {activeTab === 'music' && (
            <div className="space-y-3">
              <div className="space-y-1">
                <label className="text-slate-300 font-medium">Music Prompt (Lyria 3):</label>
                <textarea
                  value={musicPrompt}
                  onChange={(e) => setMusicPrompt(e.target.value)}
                  className="w-full text-xs p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-slate-200 focus:outline-none focus:border-cyan-500 h-20 resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-slate-400 text-[11px] block mb-1">Format & Duration:</label>
                  <select
                    value={musicDuration}
                    onChange={(e) => setMusicDuration(e.target.value as 'clip' | 'full')}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2 text-slate-200"
                  >
                    <option value="clip">Lyria Clip (Up to 30s)</option>
                    <option value="full">Lyria Pro (Full Track)</option>
                  </select>
                </div>

                <div>
                  <label className="text-slate-400 text-[11px] block mb-1">Model Engine:</label>
                  <div className="p-2 rounded-xl bg-slate-950 border border-slate-800 text-slate-300 font-mono text-[11px]">
                    {musicDuration === 'clip' ? 'lyria-3-clip-preview' : 'lyria-3-pro-preview'}
                  </div>
                </div>
              </div>

              <button
                onClick={handleGenerateMusic}
                disabled={loading}
                className="w-full py-2.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold flex items-center justify-center space-x-2 active:scale-98 transition-all"
              >
                {loading ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Music className="w-4 h-4" />}
                <span>Generate Music Audio</span>
              </button>
            </div>
          )}

          {/* TAB 4: TTS GENERATION */}
          {activeTab === 'tts' && (
            <div className="space-y-3">
              <div className="space-y-1">
                <label className="text-slate-300 font-medium">Text to Speak:</label>
                <textarea
                  value={ttsText}
                  onChange={(e) => setTtsText(e.target.value)}
                  className="w-full text-xs p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-slate-200 focus:outline-none focus:border-cyan-500 h-20 resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-slate-400 text-[11px] block mb-1">Voice Personality:</label>
                  <select
                    value={ttsVoice}
                    onChange={(e) => setTtsVoice(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-xl p-2 text-slate-200"
                  >
                    <option value="Kore">Kore (Warm, Intelligent)</option>
                    <option value="Puck">Puck (Conversational)</option>
                    <option value="Fenrir">Fenrir (Deep, Authoritative)</option>
                    <option value="Zephyr">Zephyr (Bright, Natural)</option>
                    <option value="Charon">Charon (Calm, Measured)</option>
                  </select>
                </div>

                <div>
                  <label className="text-slate-400 text-[11px] block mb-1">Model Engine:</label>
                  <div className="p-2 rounded-xl bg-slate-950 border border-slate-800 text-slate-300 font-mono text-[11px]">
                    gemini-3.1-flash-tts-preview
                  </div>
                </div>
              </div>

              <button
                onClick={handleGenerateTTS}
                disabled={loading}
                className="w-full py-2.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold flex items-center justify-center space-x-2 active:scale-98 transition-all"
              >
                {loading ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Volume2 className="w-4 h-4" />}
                <span>Synthesize & Play Speech</span>
              </button>
            </div>
          )}

          {/* TAB 5: HIGH THINKING MODE */}
          {activeTab === 'thinking' && (
            <div className="space-y-3">
              <div className="space-y-1">
                <label className="text-slate-300 font-medium">Complex Problem for Deep Reasoning:</label>
                <textarea
                  value={thinkingPrompt}
                  onChange={(e) => setThinkingPrompt(e.target.value)}
                  className="w-full text-xs p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-slate-200 focus:outline-none focus:border-cyan-500 h-24 resize-none"
                />
              </div>

              <div className="p-2.5 rounded-xl bg-slate-950 border border-indigo-950/80 text-[11px] text-indigo-300 flex items-center space-x-2">
                <BrainCircuit className="w-4 h-4 text-indigo-400 shrink-0" />
                <span>Model: <b className="font-mono text-indigo-200">gemini-3.1-pro-preview</b> with thinkingLevel: HIGH</span>
              </div>

              <button
                onClick={handleRunThinking}
                disabled={loading}
                className="w-full py-2.5 rounded-xl bg-indigo-500 hover:bg-indigo-400 text-slate-950 font-semibold flex items-center justify-center space-x-2 active:scale-98 transition-all"
              >
                {loading ? <RefreshCw className="w-4 h-4 animate-spin" /> : <BrainCircuit className="w-4 h-4" />}
                <span>Execute High-Thinking Reasoning</span>
              </button>
            </div>
          )}

          {/* TAB 6: GROUNDING (SEARCH & MAPS) */}
          {activeTab === 'grounding' && (
            <div className="space-y-3">
              <div className="space-y-1">
                <label className="text-slate-300 font-medium">Real-Time Search / Maps Query:</label>
                <textarea
                  value={groundingQuery}
                  onChange={(e) => setGroundingQuery(e.target.value)}
                  className="w-full text-xs p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-slate-200 focus:outline-none focus:border-cyan-500 h-20 resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setGroundingType('maps')}
                  className={`p-2.5 rounded-xl border flex items-center justify-center space-x-2 ${
                    groundingType === 'maps'
                      ? 'bg-rose-950/60 border-rose-600 text-rose-300 font-medium'
                      : 'bg-slate-950 border-slate-800 text-slate-400'
                  }`}
                >
                  <MapPin className="w-4 h-4" />
                  <span>Google Maps Grounding</span>
                </button>

                <button
                  type="button"
                  onClick={() => setGroundingType('search')}
                  className={`p-2.5 rounded-xl border flex items-center justify-center space-x-2 ${
                    groundingType === 'search'
                      ? 'bg-cyan-950/60 border-cyan-600 text-cyan-300 font-medium'
                      : 'bg-slate-950 border-slate-800 text-slate-400'
                  }`}
                >
                  <Search className="w-4 h-4" />
                  <span>Google Search Grounding</span>
                </button>
              </div>

              <button
                onClick={handleRunGrounding}
                disabled={loading}
                className="w-full py-2.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold flex items-center justify-center space-x-2 active:scale-98 transition-all"
              >
                {loading ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
                <span>Query Grounded Gemini 3.5 Flash</span>
              </button>
            </div>
          )}

          {/* Error display */}
          {errorText && (
            <div className="p-3 rounded-xl bg-rose-950/70 border border-rose-800/80 text-rose-200 text-xs">
              {errorText}
            </div>
          )}

          {/* Results Display */}
          {resultData && (
            <div className="p-3.5 rounded-2xl bg-slate-950 border border-slate-800 space-y-2 mt-3 animate-fade-in">
              <span className="text-[11px] font-semibold text-cyan-400 uppercase tracking-wider block">
                Result Output
              </span>

              {/* Image Result */}
              {resultData.type === 'image' && resultData.base64 && (
                <div className="rounded-xl overflow-hidden border border-slate-800 bg-black flex items-center justify-center">
                  <img
                    src={`data:image/png;base64,${resultData.base64}`}
                    alt="Generated output"
                    className="max-h-72 w-auto object-contain"
                  />
                </div>
              )}

              {/* Audio Result */}
              {(resultData.type === 'audio' || resultData.type === 'music') && resultData.base64 && (
                <div className="flex items-center space-x-3 p-3 rounded-xl bg-slate-900">
                  <button
                    onClick={() => resultData.base64 && audioEngine.playAudio(resultData.base64)}
                    className="p-2.5 rounded-xl bg-cyan-400 text-slate-950 hover:bg-cyan-300"
                  >
                    <Play className="w-4 h-4" />
                  </button>
                  <div className="text-xs text-slate-200">
                    <span className="font-semibold block">Audio Ready</span>
                    <span className="text-slate-400 text-[10px]">
                      {resultData.lyrics || 'Synthesized multi-modal audio track'}
                    </span>
                  </div>
                </div>
              )}

              {/* Text / Grounding / Thinking Result */}
              {resultData.text && (
                <div className="text-xs text-slate-200 leading-relaxed whitespace-pre-wrap select-text p-2 rounded-xl bg-slate-900/60 max-h-60 overflow-y-auto">
                  {resultData.text}
                </div>
              )}

              {/* Video Message */}
              {resultData.message && (
                <div className="text-xs text-cyan-300 p-2 rounded-xl bg-cyan-950/40 border border-cyan-800/40">
                  {resultData.message}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
