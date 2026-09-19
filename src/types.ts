/**
 * MJ AI Assistant - Core Types & Definitions
 * Master PRD v5.0 & v5.1 compliant
 */

import { BrainTask } from './core/brainTypes';

export type MJState =
  | 'BOOTING'
  | 'READY'
  | 'LISTENING'
  | 'THINKING'
  | 'PROCESSING'
  | 'SPEAKING'
  | 'EXECUTING'
  | 'VERIFYING'
  | 'INTERRUPTED'
  | 'OFFLINE'
  | 'ERROR';

export type IndianLanguageCode =
  | 'hi'     // Hindi
  | 'bn'     // Bengali
  | 'en-IN'  // English (India)
  | 'ta'     // Tamil
  | 'te'     // Telugu
  | 'mr'     // Marathi
  | 'gu'     // Gujarati
  | 'kn'     // Kannada
  | 'ml'     // Malayalam
  | 'pa'     // Punjabi
  | 'or'     // Odia
  | 'as'     // Assamese
  | 'ur';    // Urdu

export interface LanguageInfo {
  code: IndianLanguageCode;
  name: string;
  nativeName: string;
  bilingualExample: string;
  sampleGreeting: string;
  ttsVoiceName?: string;
  speechLocale: string;
}

export type GeminiModelId =
  | 'gemini-3.1-flash-lite'
  | 'gemini-3.5-flash'
  | 'gemini-3.1-pro-preview';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: number;
  language?: IndianLanguageCode;
  groundingSources?: Array<{
    title?: string;
    url?: string;
    source?: string;
  }>;
  mapsPlaces?: Array<{
    name: string;
    address?: string;
    rating?: number;
    url?: string;
  }>;
  thinkingContent?: string;
  audioUrl?: string;
  actionResult?: {
    tool: string;
    success: boolean;
    message: string;
    verified: boolean;
  };
  brainTasks?: BrainTask[];
  taskRootId?: string;
}

export interface MemoryItem {
  id: string;
  content: string;
  category: 'preference' | 'workflow' | 'person' | 'fact' | 'task';
  importance: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  createdAt: number;
  tags: string[];
}

export interface TaskStep {
  id: string;
  title: string;
  actionType: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  verified: boolean;
  details?: string;
}

export interface ActiveTask {
  id: string;
  goal: string;
  steps: TaskStep[];
  currentStepIndex: number;
  status: 'in-progress' | 'waiting-confirmation' | 'completed' | 'cancelled';
  activeApp?: string;
  confirmationPrompt?: string;
  onConfirm?: () => Promise<void>;
  onCancel?: () => void;
}

export interface InstalledApp {
  id: string;
  name: string;
  packageName: string;
  category: 'system' | 'social' | 'media' | 'tools';
  webUrl: string;
  scheme?: string;
  iconName: string;
}

export interface DeviceStatus {
  batteryLevel: number;
  isCharging: boolean;
  networkStatus: 'ONLINE' | 'LIMITED' | 'OFFLINE';
  volumeLevel: number; // 0 - 100
  flashlightOn: boolean;
  screenLocked: boolean;
  activeApp: string;
  isOverlayActive: boolean;
  isAccessibilityGranted: boolean;
  isMicrophoneGranted: boolean;
  isScreenCaptureGranted: boolean;
}

export interface ImageGenConfig {
  prompt: string;
  aspectRatio: '1:1' | '2:3' | '3:2' | '3:4' | '4:3' | '9:16' | '16:9' | '21:9';
  resolution: '512px' | '1K' | '2K' | '4K';
  model: 'gemini-3-pro-image' | 'gemini-3.1-flash-image' | 'gemini-3.1-flash-lite-image';
}

export interface VideoGenConfig {
  prompt: string;
  aspectRatio: '16:9' | '9:16';
  resolution: '720p' | '1080p';
  sourceImageBase64?: string;
}

export interface MusicGenConfig {
  prompt: string;
  duration: 'clip' | 'full'; // clip <= 30s (lyria-3-clip-preview), full (lyria-3-pro-preview)
  genre?: string;
}
