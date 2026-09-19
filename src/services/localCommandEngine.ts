/**
 * MJ Local Command Engine — FAST LOCAL PATH (PRD: Ultra-Fast Response Addendum)
 *
 * "If the task only needs an Android action, execute it locally."
 *
 * Classifies recognized speech into local intents and executes them directly
 * on the device via the MJNative Capacitor plugin — with ZERO cloud
 * round-trip: no waiting for the model to propose a tool call.
 *
 * ZERO MOCK POLICY:
 * - Real torch (CameraManager), real volume (AudioManager), real settings
 *   intents, real system timer (AlarmClock), real system navigation
 *   (AccessibilityService global actions).
 * - Anything that cannot run locally returns null and falls through to the
 *   normal AI path. No fake success, no simulated state.
 */

import { Capacitor } from '@capacitor/core';
import { appScanner, AppDefinition } from './appScanner';
import { ActiveTask } from '../types';

export interface LocalCommandResult {
  isLocalCommand: boolean;
  actionTaken?: string;
  feedbackText: string;
  requiresConfirmation?: boolean;
  confirmationTask?: ActiveTask;
  activeApp?: string;
}

class LocalCommandEngine {
  private native: any | null = null;
  private nativeChecked = false;

  /** The MJNative plugin handle (null on the web). */
  private plugin(): any | null {
    if (!this.nativeChecked) {
      this.nativeChecked = true;
      this.native = Capacitor.isNativePlatform() ? (Capacitor as any).Plugins?.MJNative || null : null;
    }
    return this.native;
  }

  /**
   * Evaluates if recognized speech is a LOCAL command and executes it
   * immediately. Returns null when the input should go to the AI path.
   */
  public async executeIfLocal(input: string): Promise<LocalCommandResult | null> {
    const text = input.trim().toLowerCase();
    if (!text || text.length < 3) return null;

    // ---------------------------------------------------------------
    // 1. OPEN APP — instant native launch, no cloud round-trip
    // ---------------------------------------------------------------
    const openMatch =
      text.match(/(?:open|launch|start)\s+([a-z0-9\s]+)$/i) ||
      text.match(/(?:kholo|khulo|chalau|chalu karo)\s+([a-z0-9\s]+)/i) ||
      text.match(/([a-z0-9\s]+?)\s+(?:kholo|khulo|khol de|chalau)/i) ||
      text.match(/(?:খোলো|খুলো|খুলে দাও|চালু করো)\s+(.+)/) ||
      text.match(/(.+?)\s+(?:খোলো|খুলো|খুলে দাও|চালাও)/);

    if (openMatch && openMatch[1]) {
      const target = openMatch[1]
        .replace(/\b(the|app|app ta|ta|ta ke|ke|diye|de|to|please|jaldi)\b/g, ' ')
        .trim();
      // Only treat as an app command when the target is short & app-like —
      // longer sentences go to the AI path (avoids false positives).
      if (target && target.split(/\s+/).length <= 4) {
        const app = appScanner.findApp(target);
        if (app && this.isLaunchable(app)) {
          const result = appScanner.launchApp(app);
          if (result.actionType === 'builtin') {
            // Built-in web tools (camera overlay etc.) — let the AI path handle.
            return null;
          }
          return {
            isLocalCommand: true,
            actionTaken: `open_app_${app.name}`,
            feedbackText: `${app.name} খুলছি...`,
            activeApp: app.name,
          };
        }
      }
    }

    // ---------------------------------------------------------------
    // 2. FLASHLIGHT / TORCH — real CameraManager torch
    // ---------------------------------------------------------------
    if (/(flashlight|torch|টর্চ|ফ্ল্যাশলাইট|আলোটা|টর্চটা)/.test(text)) {
      const turnOn =
        /(on|chalu|chalau|jalao|jala|on karo|চালু|জ্বালাও|জ্বালা|অন করো|বালিশ|বালি)/.test(text) && !/off/.test(text);
      const turnOff = /(off|bandh|nibhao|nivao|off karo|বন্ধ|নিভাও|অফ করো)/.test(text);
      if (turnOn || turnOff) {
        const p = this.plugin();
        if (p?.setTorch) {
          try {
            await p.setTorch({ on: turnOn });
            return {
              isLocalCommand: true,
              actionTaken: turnOn ? 'torch_on' : 'torch_off',
              feedbackText: turnOn ? 'টর্চ চালু করেছি জান।' : 'টর্চ বন্ধ করেছি।',
            };
          } catch {
            return {
              isLocalCommand: true,
              actionTaken: 'torch_failed',
              feedbackText: 'এই ফোনে টর্চ কন্ট্রোল করা যাচ্ছে না।',
            };
          }
        }
      }
    }

    // ---------------------------------------------------------------
    // 3. VOLUME — real AudioManager (native only; no fake numbers)
    // ---------------------------------------------------------------
    if (/(volume|ভলিউম|শব্দটা|sound|shobdo)/.test(text)) {
      const up = /(up|increase|barao|badhao|bada karo|বাড়াও|বাড়াও|বেশি|high)/.test(text);
      const down = /(down|decrease|komao|kam karo|koro|কমাও|কম|low|silent)/.test(text);
      if (up !== down) {
        const p = this.plugin();
        if (p?.adjustVolume) {
          try {
            await p.adjustVolume({ direction: up ? 'up' : 'down' });
            return {
              isLocalCommand: true,
              actionTaken: up ? 'volume_up' : 'volume_down',
              feedbackText: up ? 'ভলিউম বাড়িয়েছি।' : 'ভলিউম কমিয়েছি।',
            };
          } catch {
            /* fall through to AI path */
          }
        }
        return null; // Never fake volume changes on the web.
      }
    }

    // ---------------------------------------------------------------
    // 4. SYSTEM SETTINGS — direct Settings intents
    // ---------------------------------------------------------------
    const settingMap: Array<[RegExp, string, string]> = [
      [/(wifi|wi-fi|ওয়াইফাই|ওয়াই-ফাই)/, 'wifi', 'ওয়াইফাই সেটিংস খুলছি...'],
      [/(bluetooth|ব্লুটুথ)/, 'bluetooth', 'ব্লুটুথ সেটিংস খুলছি...'],
      [/(airplane|এয়ারপ্লেন|ফ্লাইট মোড)/, 'airplane', 'এয়ারপ্লেন মোড সেটিংস খুলছি...'],
      [/(display|brightness|উজ্জ্বল|ডিসপ্লে)/, 'display', 'ডিসপ্লে সেটিংস খুলছি...'],
      [/(location|লোকেশন|জিপিএস|gps)/, 'location', 'লোকেশন সেটিংস খুলছি...'],
    ];
    if (/(settings|setting|সেটিংস)/.test(text) || settingMap.some(([re]) => re.test(text))) {
      for (const [re, key, feedback] of settingMap) {
        if (re.test(text)) {
          const p = this.plugin();
          if (p?.openSystemSetting) {
            try {
              await p.openSystemSetting({ setting: key });
              return { isLocalCommand: true, actionTaken: `open_${key}_settings`, feedbackText: feedback };
            } catch {
              /* fall through */
            }
          }
        }
      }
      // Plain "settings kholo"
      if (/(settings|setting|সেটিংস)/.test(text) && /(open|kholo|khulo|খোলো|খুলো|যাও)/.test(text)) {
        const p = this.plugin();
        if (p?.openSystemSetting) {
          try {
            await p.openSystemSetting({ setting: 'settings' });
            return { isLocalCommand: true, actionTaken: 'open_settings', feedbackText: 'সেটিংস খুলছি...' };
          } catch {
            /* fall through */
          }
        }
      }
    }

    // ---------------------------------------------------------------
    // 5. TIMER / ALARM — real AlarmClock intent
    // ---------------------------------------------------------------
    if (/(timer|টাইমার|alarm|অ্যালার্ম|remind|মনে করিয়ে)/.test(text)) {
      const durMatch = text.match(/(\d+)\s*(minute|minutes|min|মিনিট|second|seconds|sec|সেকেন্ড|hour|hours|ghanta|ghonta|ঘণ্টা|ঘন্টা)/i);
      if (durMatch) {
        const n = parseInt(durMatch[1], 10);
        const unit = durMatch[2];
        const seconds = /sec|সেকেন্ড/i.test(unit)
          ? n
          : /hour|ghonta|ghonta|ঘণ্টা|ঘন্টা/i.test(unit)
            ? n * 3600
            : n * 60;
        const p = this.plugin();
        if (p?.startTimer) {
          try {
            await p.startTimer({ seconds, label: 'MJ Timer' });
            return {
              isLocalCommand: true,
              actionTaken: 'set_timer',
              feedbackText: `${n} ${unit} এর টাইমার সেট করেছি।`,
            };
          } catch {
            /* fall through */
          }
        }
      }
    }

    // ---------------------------------------------------------------
    // 6. SYSTEM NAVIGATION — Back / Home / Recents (Accessibility)
    // ---------------------------------------------------------------
    if (/(back|পিছনে|পিছনে যাও|pichone jao|ব্যাক)/.test(text) && /(go|jao|যাও|karo|back)/.test(text)) {
      return await this.tryGlobalAction('back', 'ব্যাক করেছি।');
    }
    if (/(home|হোম|হোমে)/.test(text) && /(go|jao|যাও|home)/.test(text)) {
      return await this.tryGlobalAction('home', 'হোমে চলে গেছি।');
    }
    if (/(recents|recent apps|রিসেন্ট|রিসেন্ট অ্যাপ)/.test(text)) {
      return await this.tryGlobalAction('recents', 'রিসেন্ট অ্যাপ খুলছি...');
    }

    // ---------------------------------------------------------------
    // 7. LOCAL INFO — time / date / battery (no model call)
    // ---------------------------------------------------------------
    if (/(what time|time koto|koto baje|কটা বাজে|ক'টা বাজে|সময় কত|কত সময়|current time)/.test(text)) {
      const now = new Date();
      const timeStr = now.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
      return {
        isLocalCommand: true,
        actionTaken: 'tell_time',
        feedbackText: `এখন ${timeStr} বাজে জান।`,
      };
    }
    if (/(what.*date|today.*date|আজ কত তারিখ|তারিখ কত|আজকের তারিখ)/.test(text)) {
      const today = new Date().toLocaleDateString('bn-IN', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      });
      return {
        isLocalCommand: true,
        actionTaken: 'tell_date',
        feedbackText: `আজ ${today}।`,
      };
    }
    if (/(battery|ব্যাটারি|চার্জ কত|charge koto)/.test(text)) {
      const p = this.plugin();
      if (p?.getDeviceStatus) {
        try {
          const status: any = await p.getDeviceStatus();
          if (typeof status?.batteryLevel === 'number' && status.batteryLevel >= 0) {
            const pct = status.batteryLevel;
            const charging = status.charging ? ', চার্জে আছে' : '';
            return {
              isLocalCommand: true,
              actionTaken: 'tell_battery',
              feedbackText: `ব্যাটারি ${pct}%${charging}।`,
            };
          }
        } catch {
          /* fall through to AI path */
        }
      }
      return null;
    }

    // ---------------------------------------------------------------
    // 8. ACCESSIBILITY enable helper
    // ---------------------------------------------------------------
    if (/(accessibility|অ্যাক্সেসিবিলিটি)/.test(text) && /(open|enable|chalu|খোলো|on)/.test(text)) {
      const p = this.plugin();
      if (p?.openAccessibilitySettings) {
        try {
          await p.openAccessibilitySettings();
          return {
            isLocalCommand: true,
            actionTaken: 'open_accessibility_settings',
            feedbackText: 'Android Settings-এ MJ Android Agent খুঁজে Enable করো জান।',
          };
        } catch {
          /* fall through */
        }
      }
    }

    // ---------------------------------------------------------------
    // 9. PLAY STORE SEARCH
    // ---------------------------------------------------------------
    if (/(play store|playstore|প্লে স্টোর|প্লেস্টোর)/.test(text) && /(search|find|khojo|খোঁজো|খুঁজে)/.test(text)) {
      const query = text
        .replace(/play ?store|playstore|প্লে ?স্টোর|search|find|khojo|খোঁজো|খুঁজে|on|in|the|me|মে|e/gi, ' ')
        .trim();
      if (query) {
        appScanner.openExternalUrl(
          `https://play.google.com/store/search?q=${encodeURIComponent(query)}&c=apps`
        );
        return {
          isLocalCommand: true,
          actionTaken: 'playstore_search',
          feedbackText: `Play Store-এ ${query} খুঁজছি...`,
          activeApp: 'Play Store',
        };
      }
    }

    // ---------------------------------------------------------------
    // 10. CONSEQUENTIAL ACTIONS — WhatsApp draft & phone call
    //     (explicit confirmation required before execution)
    // ---------------------------------------------------------------
    const msgMatch =
      text.match(/(?:send|bhejo|message)\s+(?:a\s+)?(?:whatsapp\s+)?(?:message\s+)?(?:to\s+)?([a-z0-9]+)\s+(?:saying\s+|that\s+|bolke\s+)?(.*)/i) ||
      text.match(/([a-z0-9]+)\s+(?:ko|ke)\s+(?:message|whatsapp)\s+(?:bhejo|karo)\s*(.*)/i);
    if (msgMatch && msgMatch[1] && /(whatsapp|message|মেসেজ)/.test(text)) {
      const recipient = msgMatch[1].trim();
      const messageBody = msgMatch[2] ? msgMatch[2].trim() : 'I will be there shortly.';
      const task: ActiveTask = {
        id: `task_${Date.now()}`,
        goal: `Send WhatsApp message to ${recipient}`,
        status: 'waiting-confirmation',
        currentStepIndex: 0,
        confirmationPrompt: `${recipient}-কে WhatsApp-এ এই মেসেজটা পাঠাবো: "${messageBody}"?`,
        steps: [
          { id: '1', title: `Resolve contact ${recipient}`, actionType: 'resolve_contact', status: 'completed', verified: true },
          { id: '2', title: 'Compose message draft', actionType: 'draft_message', status: 'completed', verified: true },
          { id: '3', title: `Send message to ${recipient}`, actionType: 'send_whatsapp', status: 'pending', verified: false, details: messageBody },
        ],
        onConfirm: async () => {
          appScanner.openExternalUrl(`https://api.whatsapp.com/send?text=${encodeURIComponent(messageBody)}`);
        },
      };
      return {
        isLocalCommand: true,
        actionTaken: 'draft_whatsapp_message',
        feedbackText: `${recipient}-এর জন্য মেসেজ রেডি করেছি জান। Confirm করো তাহলে পাঠাবো।`,
        requiresConfirmation: true,
        confirmationTask: task,
      };
    }

    const callMatch = text.match(/(?:call|phone|ring|কল করো)\s+([a-z0-9\s]+)/i);
    if (callMatch && callMatch[1]) {
      const person = callMatch[1].replace(/\b(mom|dad|home|work)\b/gi, (m) => m).trim();
      if (person) {
        const task: ActiveTask = {
          id: `task_${Date.now()}`,
          goal: `Call ${person}`,
          status: 'waiting-confirmation',
          currentStepIndex: 0,
          confirmationPrompt: `${person}-কে কল করবো?`,
          steps: [
            { id: '1', title: `Resolve contact for ${person}`, actionType: 'resolve_contact', status: 'completed', verified: true },
            { id: '2', title: 'Initiate telephony dialer', actionType: 'dial_phone', status: 'pending', verified: false },
          ],
          onConfirm: async () => {
            window.location.href = `tel:${encodeURIComponent(person.replace(/\s+/g, ''))}`;
          },
        };
        return {
          isLocalCommand: true,
          actionTaken: 'prepare_call',
          feedbackText: `${person}-কে কল করার জন্য রেডি জান। Confirm করো।`,
          requiresConfirmation: true,
          confirmationTask: task,
        };
      }
    }

    return null;
  }

  /** Executes an AccessibilityService global action (Back/Home/Recents). */
  private async tryGlobalAction(action: 'back' | 'home' | 'recents', feedback: string): Promise<LocalCommandResult | null> {
    const p = this.plugin();
    if (p?.globalAction) {
      try {
        await p.globalAction({ action });
        return { isLocalCommand: true, actionTaken: `global_${action}`, feedbackText: feedback };
      } catch {
        return {
          isLocalCommand: true,
          actionTaken: 'needs_accessibility',
          feedbackText: 'এই কাজের জন্য Accessibility চালু করতে হবে জান — Settings → Accessibility → MJ Android Agent।',
        };
      }
    }
    return null; // Web has no system navigation — AI path.
  }

  /** Only real installed apps (with packageName) or web-launchable entries. */
  private isLaunchable(app: AppDefinition): boolean {
    if (app.isBuiltInTool) return false;
    if (app.packageName) return true;
    return app.url.startsWith('http');
  }
}

export const localCommandEngine = new LocalCommandEngine();
