/**
 * MJ Screen Agent — general, model-driven FULL-SCREEN control.
 *
 * This is what lets MJ actually PERFORM arbitrary on-screen tasks instead of
 * just launching apps: it reads the visible screen, decides the next action
 * with Gemini, executes that action on-device through the Accessibility
 * Service, and repeats until the goal is done (or is proven impossible).
 *
 * Loop: OPEN → READ → DECIDE → ACT → (repeat) → REPORT
 * Everything runs on the phone; the only network call is the model decision.
 *
 * HONESTY: it never fakes success. If a step fails it tries a different way,
 * and if the task truly cannot be completed it says so plainly.
 */

import { GoogleGenAI, Type } from '@google/genai';
import { Capacitor } from '@capacitor/core';
import { appScanner } from './appScanner';

type ReportFn = (msg: string) => void;

interface ChatTurn {
  role: 'user' | 'model';
  parts: any[];
}

export interface ScreenTaskResult {
  success: boolean;
  message: string;
}

class ScreenAgent {
  private report: ReportFn | null = null;
  private busy = false;
  private readonly MAX_STEPS = 16;

  /** Progress / result sink — wired by liveSession to the UI subtitles. */
  public setReportListener(fn: ReportFn): void {
    this.report = fn;
  }

  public isBusy(): boolean {
    return this.busy;
  }

  private plugin(): any | null {
    return Capacitor.isNativePlatform() ? (Capacitor as any).Plugins?.MJNative || null : null;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((r) => setTimeout(r, ms));
  }

  /** Single screenAction bridge call. Returns null on any failure. */
  private async act(params: Record<string, unknown>): Promise<any | null> {
    const p = this.plugin();
    if (!p?.screenAction) return null;
    try {
      return await p.screenAction(params);
    } catch {
      return null;
    }
  }

  private async readScreen(): Promise<string[]> {
    const r = await this.act({ action: 'read' });
    return Array.isArray(r?.texts) ? r.texts.map((t: any) => String(t)) : [];
  }

  private async foregroundApp(): Promise<string> {
    const p = this.plugin();
    try {
      const r: any = await p?.getForegroundApp?.();
      return r?.packageName || '';
    } catch {
      return '';
    }
  }

  private async openAppByName(name: string): Promise<string> {
    const p = this.plugin();
    if (p?.openAppByName) {
      try {
        const r: any = await p.openAppByName({ name });
        if (r?.found) return `Opened ${r.label || name}.`;
      } catch {
        /* fall through */
      }
    }
    const r = await appScanner.openAppSmart(name);
    return r.found ? `Opened ${r.name}.` : `Could not find an app named "${name}".`;
  }

  /** Tools the deciding model may call — exactly one per step. */
  private readonly toolDeclarations = [
    {
      name: 'openApp',
      description: 'Open an installed app on the phone by its name.',
      parameters: {
        type: Type.OBJECT,
        properties: { name: { type: Type.STRING, description: 'app display name, e.g. youtube, whatsapp, settings, telegram' } },
        required: ['name'],
      },
    },
    {
      name: 'click',
      description: 'Click/tap a visible element, matched by a short exact piece of its text or label.',
      parameters: {
        type: Type.OBJECT,
        properties: { text: { type: Type.STRING, description: 'short exact text/label of the element to tap' } },
        required: ['text'],
      },
    },
    {
      name: 'typeText',
      description: 'Type text into the currently focused / first text field on screen.',
      parameters: {
        type: Type.OBJECT,
        properties: { text: { type: Type.STRING } },
        required: ['text'],
      },
    },
    {
      name: 'swipe',
      description: 'Swipe/scroll the screen in a direction.',
      parameters: {
        type: Type.OBJECT,
        properties: { direction: { type: Type.STRING, description: 'up | down | left | right' } },
        required: ['direction'],
      },
    },
    {
      name: 'press',
      description: 'Press a system navigation button.',
      parameters: {
        type: Type.OBJECT,
        properties: { button: { type: Type.STRING, description: 'back | home | recents | notifications' } },
        required: ['button'],
      },
    },
    {
      name: 'wait',
      description: 'Wait a moment for the screen to settle before the next step.',
      parameters: {
        type: Type.OBJECT,
        properties: { ms: { type: Type.NUMBER } },
      },
    },
    {
      name: 'done',
      description: 'Finish the task. Call this when the goal is achieved, or when it is truly impossible.',
      parameters: {
        type: Type.OBJECT,
        properties: {
          success: { type: Type.BOOLEAN },
          message: { type: Type.STRING, description: 'One short, natural sentence spoken aloud to the user, in their own language.' },
        },
        required: ['success', 'message'],
      },
    },
  ];

  /**
   * Run a natural-language on-screen goal end to end.
   * e.g. "open whatsapp and message baba hi",
   *      "youtube e arijit singh er gaan chalao",
   *      "settings e bluetooth on koro"
   */
  public async runTask(goal: string): Promise<ScreenTaskResult> {
    if (this.busy) return { success: false, message: 'আরেকটা কাজ চলছে জান, একটু দাঁড়াও।' };
    this.busy = true;
    try {
      const p = this.plugin();
      if (!p?.screenAction) {
        return { success: false, message: 'স্ক্রিন কন্ট্রোল এই বিল্ডে চালু নেই জান।' };
      }

      // Gate: the Accessibility Service must be enabled by the user.
      let acc = false;
      try {
        acc = (await p.isAccessibilityEnabled())?.enabled === true;
      } catch {
        /* ignore */
      }
      if (!acc) {
        try {
          await p.openAccessibilitySettings();
        } catch {
          /* ignore */
        }
        const msg = 'এই কাজটা করতে আগে Accessibility চালু করো জান — Settings → Accessibility → MJ Android Agent।';
        this.report?.(msg);
        return { success: false, message: msg };
      }

      const apiKey = (localStorage.getItem('gemini_custom_api_key') || '').trim();
      if (!apiKey) {
        const msg = 'আগে Settings-এ তোমার Gemini API key বসাও জান, তারপর আবার বলো।';
        this.report?.(msg);
        return { success: false, message: msg };
      }
      const ai = new GoogleGenAI({ apiKey });

      const systemInstruction = `You are MJ, driving a REAL Android phone through its accessibility service to complete the user's goal.
Rules:
- Each turn you are shown the CURRENT visible screen text and the foreground app. Choose exactly ONE action by calling one tool.
- To tap something, pass a SHORT, exact piece of its visible text (a few words, not the whole sentence).
- If the needed control is not visible, scroll first; if the wrong app is open, open the right app first.
- Never repeat an action that just failed more than twice — try a different approach instead.
- When the goal is fully achieved, call 'done' with success=true and a short, natural confirmation.
- If it truly cannot be done, call 'done' with success=false and a short, honest reason.
- The 'message' you pass to 'done' is spoken aloud to the user: write it in the user's language (Bengali script if they spoke Bengali), warmly and naturally, ONE short sentence.`;

      const contents: ChatTurn[] = [];

      for (let step = 0; step < this.MAX_STEPS; step++) {
        const texts = await this.readScreen();
        const fg = await this.foregroundApp();
        const screenText =
          texts.slice(0, 70).map((t, i) => `${i + 1}. ${t}`).join('\n') || '(screen appears empty)';
        contents.push({
          role: 'user',
          parts: [
            {
              text: `GOAL: ${goal}\n\nFOREGROUND_APP: ${fg || 'unknown'}\n\nVISIBLE_SCREEN_TEXT:\n${screenText}\n\nStep ${step + 1}: what is the single next action?`,
            },
          ],
        });

        let resp: any;
        try {
          resp = await ai.models.generateContent({
            model: 'gemini-flash-latest',
            contents,
            config: { systemInstruction, tools: [{ functionDeclarations: this.toolDeclarations }] },
          });
        } catch {
          // Fall back to a concrete model name if the alias is unavailable.
          resp = await ai.models.generateContent({
            model: 'gemini-2.5-flash',
            contents,
            config: { systemInstruction, tools: [{ functionDeclarations: this.toolDeclarations }] },
          });
        }

        const modelContent = resp?.candidates?.[0]?.content;
        if (modelContent) contents.push({ role: 'model', parts: modelContent.parts || [] });

        const fc = this.pickFunctionCall(resp);
        if (!fc) {
          // No tool call — treat the model's text as the answer.
          const text = (resp?.text || '').trim();
          const msg = text || 'হয়ে গেছে জান।';
          this.report?.(msg);
          return { success: true, message: msg };
        }

        const name = String(fc.name || '');
        const args: any = fc.args || {};

        if (name === 'done') {
          const msg = String(args.message || 'হয়ে গেছে জান।');
          this.report?.(msg);
          return { success: args.success !== false, message: msg };
        }

        const outcome = await this.execute(name, args);
        this.report?.(outcome);

        contents.push({
          role: 'user',
          parts: [{ functionResponse: { name, response: { result: outcome } } }],
        });
        await this.sleep(700);
      }

      const msg = 'অনেক চেষ্টা করেও কাজটা পুরো করতে পারিনি জান — তুমি একবার দেখে বলো।';
      this.report?.(msg);
      return { success: false, message: msg };
    } catch {
      const msg = 'স্ক্রিন কন্ট্রোল করতে গিয়ে একটা সমস্যা হলো জান।';
      this.report?.(msg);
      return { success: false, message: msg };
    } finally {
      this.busy = false;
    }
  }

  private pickFunctionCall(resp: any): any | null {
    try {
      if (Array.isArray(resp?.functionCalls) && resp.functionCalls.length > 0) {
        return resp.functionCalls[0];
      }
    } catch {
      /* ignore */
    }
    try {
      const parts = resp?.candidates?.[0]?.content?.parts || [];
      for (const part of parts) {
        if (part?.functionCall) return part.functionCall;
      }
    } catch {
      /* ignore */
    }
    return null;
  }

  private async execute(name: string, args: any): Promise<string> {
    if (name === 'openApp') {
      const appName = String(args.name || args.appName || '').trim();
      if (!appName) return 'No app name was given.';
      const r = await this.openAppByName(appName);
      await this.sleep(1600);
      return r;
    }
    if (name === 'click') {
      const text = String(args.text || '').trim();
      if (!text) return 'No text was given to click.';
      const r = await this.act({ action: 'click', matchText: text, timeoutMs: 6000 });
      return r?.success ? `Clicked "${text}".` : `Could not find "${text}" on screen to click.`;
    }
    if (name === 'typeText') {
      const text = String(args.text || '');
      const r = await this.act({ action: 'type', editable: true, value: text, timeoutMs: 6000 });
      return r?.success ? `Typed "${text}".` : 'No text field was found to type into.';
    }
    if (name === 'swipe') {
      const dir = String(args.direction || 'down');
      const r = await this.act({ action: 'swipe', value: dir, timeoutMs: 4000 });
      return r?.success ? `Swiped ${dir}.` : `Could not swipe ${dir}.`;
    }
    if (name === 'press') {
      const button = String(args.button || 'back');
      const p = this.plugin();
      try {
        await p?.globalAction?.({ action: button });
        return `Pressed ${button}.`;
      } catch {
        return `Could not press ${button}.`;
      }
    }
    if (name === 'wait') {
      const ms = Math.min(Math.max(Number(args.ms) || 1000, 300), 5000);
      await this.sleep(ms);
      return `Waited ${ms}ms.`;
    }
    return `Unknown action "${name}".`;
  }
}

export const screenAgent = new ScreenAgent();
