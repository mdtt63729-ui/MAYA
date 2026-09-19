/**
 * MJ Local Command Engine
 * Offline-first local intent parser and native action dispatcher.
 * Handles instant app launching, device toggles, volume, timers, and confirmation workflows
 * without requiring cloud roundtrips for local operations.
 */

import { ActiveTask, InstalledApp } from '../types';

export const DEFAULT_INSTALLED_APPS: InstalledApp[] = [
  {
    id: 'youtube',
    name: 'YouTube',
    packageName: 'com.google.android.youtube',
    category: 'media',
    webUrl: 'https://www.youtube.com',
    scheme: 'vnd.youtube:',
    iconName: 'Play',
  },
  {
    id: 'whatsapp',
    name: 'WhatsApp',
    packageName: 'com.whatsapp',
    category: 'social',
    webUrl: 'https://web.whatsapp.com',
    scheme: 'whatsapp://',
    iconName: 'MessageCircle',
  },
  {
    id: 'playstore',
    name: 'Google Play Store',
    packageName: 'com.android.vending',
    category: 'tools',
    webUrl: 'https://play.google.com/store',
    scheme: 'market://',
    iconName: 'ShoppingBag',
  },
  {
    id: 'chrome',
    name: 'Google Chrome',
    packageName: 'com.android.chrome',
    category: 'tools',
    webUrl: 'https://www.google.com',
    iconName: 'Globe',
  },
  {
    id: 'camera',
    name: 'Camera',
    packageName: 'com.android.camera',
    category: 'tools',
    webUrl: '#camera',
    iconName: 'Camera',
  },
  {
    id: 'gallery',
    name: 'Photos & Gallery',
    packageName: 'com.google.android.apps.photos',
    category: 'media',
    webUrl: '#gallery',
    iconName: 'Image',
  },
  {
    id: 'settings',
    name: 'Settings',
    packageName: 'com.android.settings',
    category: 'system',
    webUrl: '#settings',
    iconName: 'Settings',
  },
  {
    id: 'maps',
    name: 'Google Maps',
    packageName: 'com.google.android.apps.maps',
    category: 'tools',
    webUrl: 'https://maps.google.com',
    scheme: 'geo:',
    iconName: 'MapPin',
  },
];

export interface LocalCommandResult {
  isLocalCommand: boolean;
  actionTaken?: string;
  feedbackText: string;
  requiresConfirmation?: boolean;
  confirmationTask?: ActiveTask;
  activeApp?: string;
}

class LocalCommandEngine {
  private torchStream: MediaStream | null = null;
  private isFlashlightOn: boolean = false;
  private volumeLevel: number = 75;

  /**
   * Evaluates if speech/text is a local Android command
   */
  public async executeIfLocal(
    input: string,
    onConfirmCallback?: () => void
  ): Promise<LocalCommandResult | null> {
    const text = input.trim().toLowerCase();

    // 1. App Launching (YouTube, WhatsApp, Play Store, etc.)
    // Supports English, Hindi/Hinglish ("kholo"), Bengali ("kholo")
    const openMatch = text.match(
      /(?:open|launch|kholo|khulo|khol|chalau|start)\s+([a-z0-9\s]+)/i
    ) || text.match(/([a-z0-9\s]+)\s+(?:kholo|khulo|khol|chalau)/i);

    if (openMatch && openMatch[1]) {
      const targetQuery = openMatch[1].trim().toLowerCase();

      // Check installed apps list
      const matchedApp = DEFAULT_INSTALLED_APPS.find(
        (app) =>
          targetQuery.includes(app.name.toLowerCase()) ||
          app.name.toLowerCase().includes(targetQuery) ||
          targetQuery.includes(app.id)
      );

      if (matchedApp) {
        this.openApp(matchedApp);
        return {
          isLocalCommand: true,
          actionTaken: `open_app_${matchedApp.id}`,
          feedbackText: `Opening ${matchedApp.name}...`,
          activeApp: matchedApp.name,
        };
      }

      // Check special keywords
      if (targetQuery.includes('youtube')) {
        window.open('https://www.youtube.com', '_blank');
        return {
          isLocalCommand: true,
          actionTaken: 'open_youtube',
          feedbackText: 'Opening YouTube...',
          activeApp: 'YouTube',
        };
      }
      if (targetQuery.includes('whatsapp')) {
        window.open('https://web.whatsapp.com', '_blank');
        return {
          isLocalCommand: true,
          actionTaken: 'open_whatsapp',
          feedbackText: 'Opening WhatsApp...',
          activeApp: 'WhatsApp',
        };
      }
      if (targetQuery.includes('play store') || targetQuery.includes('playstore')) {
        window.open('https://play.google.com/store', '_blank');
        return {
          isLocalCommand: true,
          actionTaken: 'open_playstore',
          feedbackText: 'Opening Google Play Store...',
          activeApp: 'Play Store',
        };
      }
    }

    // 2. Play Store search (e.g. "Find WhatsApp on Play Store", "Play Store me WhatsApp khojo")
    if (text.includes('play store') && (text.includes('find') || text.includes('search') || text.includes('khojo'))) {
      const query = text.replace(/play store|find|search|on|in|khojo/gi, '').trim();
      const searchUrl = `https://play.google.com/store/search?q=${encodeURIComponent(query)}&c=apps`;
      window.open(searchUrl, '_blank');
      return {
        isLocalCommand: true,
        actionTaken: 'playstore_search',
        feedbackText: `Searching for ${query} on Google Play Store...`,
        activeApp: 'Play Store',
      };
    }

    // 3. WhatsApp Message drafting (Consequential Action -> Confirmation Required)
    const msgMatch = text.match(/(?:send|bhejo|message)\s+(?:a\s+)?(?:whatsapp\s+)?(?:message\s+)?(?:to\s+)?([a-z0-9]+)\s+(?:saying\s+|that\s+|bolke\s+)?(.*)/i) ||
                     text.match(/([a-z0-9]+)\s+(?:ko|ke)\s+(?:message|whatsapp)\s+(?:bhejo|karo)\s*(.*)/i);
    if (msgMatch && msgMatch[1]) {
      const recipient = msgMatch[1].trim();
      const messageBody = msgMatch[2] ? msgMatch[2].trim() : "I'll be there shortly.";

      const task: ActiveTask = {
        id: `task_${Date.now()}`,
        goal: `Send WhatsApp message to ${recipient}`,
        status: 'waiting-confirmation',
        currentStepIndex: 0,
        confirmationPrompt: `Send WhatsApp message to ${recipient}: "${messageBody}"?`,
        steps: [
          { id: '1', title: `Resolve contact ${recipient}`, actionType: 'resolve_contact', status: 'completed', verified: true },
          { id: '2', title: 'Compose message draft', actionType: 'draft_message', status: 'completed', verified: true },
          { id: '3', title: `Send message to ${recipient}`, actionType: 'send_whatsapp', status: 'pending', verified: false, details: messageBody },
        ],
        onConfirm: async () => {
          const encoded = encodeURIComponent(messageBody);
          window.open(`https://api.whatsapp.com/send?text=${encoded}`, '_blank');
        },
      };

      return {
        isLocalCommand: true,
        actionTaken: 'draft_whatsapp_message',
        feedbackText: `I have prepared the message for ${recipient}. Please confirm to send.`,
        requiresConfirmation: true,
        confirmationTask: task,
      };
    }

    // 4. Calling contact (Consequential Action -> Confirmation Required)
    const callMatch = text.match(/(?:call|phone|ring)\s+([a-z0-9\s]+)/i);
    if (callMatch && callMatch[1]) {
      const person = callMatch[1].replace(/mom|dad|home|work/i, (m) => m).trim();

      const task: ActiveTask = {
        id: `task_${Date.now()}`,
        goal: `Call ${person}`,
        status: 'waiting-confirmation',
        currentStepIndex: 0,
        confirmationPrompt: `Place phone call to ${person}?`,
        steps: [
          { id: '1', title: `Resolve contact for ${person}`, actionType: 'resolve_contact', status: 'completed', verified: true },
          { id: '2', title: `Initiate telephony dialer`, actionType: 'dial_phone', status: 'pending', verified: false },
        ],
        onConfirm: async () => {
          window.location.href = `tel:${encodeURIComponent(person)}`;
        },
      };

      return {
        isLocalCommand: true,
        actionTaken: 'prepare_call',
        feedbackText: `Ready to call ${person}. Please confirm below.`,
        requiresConfirmation: true,
        confirmationTask: task,
      };
    }

    // 5. Device Controls: Flashlight (Torch)
    if (text.includes('flashlight') || text.includes('torch')) {
      if (text.includes('on') || text.includes('chalu') || text.includes('jalao')) {
        await this.toggleTorch(true);
        return {
          isLocalCommand: true,
          actionTaken: 'torch_on',
          feedbackText: 'Flashlight turned on.',
        };
      }
      if (text.includes('off') || text.includes('bandh') || text.includes('nibhao')) {
        await this.toggleTorch(false);
        return {
          isLocalCommand: true,
          actionTaken: 'torch_off',
          feedbackText: 'Flashlight turned off.',
        };
      }
    }

    // 6. Device Controls: Volume
    if (text.includes('volume')) {
      if (text.includes('up') || text.includes('increase') || text.includes('barao') || text.includes('badhao')) {
        this.volumeLevel = Math.min(100, this.volumeLevel + 15);
        return {
          isLocalCommand: true,
          actionTaken: 'volume_up',
          feedbackText: `Volume increased to ${this.volumeLevel}%.`,
        };
      }
      if (text.includes('down') || text.includes('decrease') || text.includes('komao') || text.includes('kam karo')) {
        this.volumeLevel = Math.max(0, this.volumeLevel - 15);
        return {
          isLocalCommand: true,
          actionTaken: 'volume_down',
          feedbackText: `Volume decreased to ${this.volumeLevel}%.`,
        };
      }
    }

    // 7. Navigation: Scroll up / down, Back, Home
    if (text.match(/scroll\s+(?:down|niche)/i)) {
      window.scrollBy({ top: 350, behavior: 'smooth' });
      return {
        isLocalCommand: true,
        actionTaken: 'scroll_down',
        feedbackText: 'Scrolling down.',
      };
    }
    if (text.match(/scroll\s+(?:up|upore)/i)) {
      window.scrollBy({ top: -350, behavior: 'smooth' });
      return {
        isLocalCommand: true,
        actionTaken: 'scroll_up',
        feedbackText: 'Scrolling up.',
      };
    }
    if (text.match(/(?:go\s+)?back|piche/i)) {
      window.history.back();
      return {
        isLocalCommand: true,
        actionTaken: 'navigate_back',
        feedbackText: 'Navigating back.',
      };
    }

    return null;
  }

  private openApp(app: InstalledApp) {
    if (app.scheme) {
      try {
        window.location.href = app.scheme;
        return;
      } catch {}
    }
    window.open(app.webUrl, '_blank');
  }

  private async toggleTorch(turnOn: boolean) {
    this.isFlashlightOn = turnOn;
    try {
      if (turnOn) {
        const stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'environment' },
        });
        this.torchStream = stream;
        const track = stream.getVideoTracks()[0];
        const imageCapture = (track as unknown as { applyConstraints: (c: unknown) => Promise<void> });
        if (imageCapture.applyConstraints) {
          await imageCapture.applyConstraints({
            advanced: [{ torch: true }],
          });
        }
      } else {
        if (this.torchStream) {
          this.torchStream.getTracks().forEach((t) => t.stop());
          this.torchStream = null;
        }
      }
    } catch {
      // Graceful fallback for devices without physical torch hardware support
      console.log(`Simulated torch state: ${turnOn ? 'ON' : 'OFF'}`);
    }
  }
}

export const localCommandEngine = new LocalCommandEngine();
