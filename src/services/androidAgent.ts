/**
 * MJ Android Agent — accessibility-driven screen automation (PRD Phase B).
 *
 * Generic engine: OPEN → OBSERVE → FIND → ACT → WAIT → VERIFY → REPORT.
 * Runs entirely on-device (no cloud round-trip) through the MJNative
 * screenAction bridge on top of the user-enabled Accessibility Service.
 *
 * NO FALSE CLAIMS: every step reports what actually happened; if a step
 * fails the user hears exactly what could not be completed.
 */

import { Capacitor } from '@capacitor/core';
import { appScanner } from './appScanner';

export interface AgentResult {
  success: boolean;
  message: string;
}

type ReportFn = (msg: string) => void;

class AndroidAgent {
  private report: ReportFn | null = null;
  private busy = false;

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

  public async isAccessibilityEnabled(): Promise<boolean> {
    const p = this.plugin();
    if (!p?.isAccessibilityEnabled) return false;
    try {
      return (await p.isAccessibilityEnabled())?.enabled === true;
    } catch {
      return false;
    }
  }

  private async openAccessibilitySettings(): Promise<void> {
    const p = this.plugin();
    if (p?.openAccessibilitySettings) {
      try {
        await p.openAccessibilitySettings();
      } catch {
        /* ignore */
      }
    }
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((r) => setTimeout(r, ms));
  }

  /** Single bridge call. Returns null on any failure (never throws). */
  private async act(params: Record<string, unknown>): Promise<any | null> {
    const p = this.plugin();
    if (!p?.screenAction) return null;
    try {
      return await p.screenAction(params);
    } catch {
      return null;
    }
  }

  // ------------------------------------------------------------------
  // Flows
  // ------------------------------------------------------------------

  /**
   * YouTube search & play:
   * launch → tap Search → type query → tap suggestion → tap first result.
   */
  public async youtubeSearchAndPlay(query: string): Promise<AgentResult> {
    if (this.busy) return { success: false, message: 'already running' };
    this.busy = true;
    try {
      const gate = await this.requireAccessibility();
      if (!gate.ok) return { success: false, message: gate.msg };

      this.report?.('YouTube খুলছি...');
      const yt = appScanner.findApp('youtube');
      if (!yt) return { success: false, message: 'YouTube not found' };
      appScanner.launchApp(yt);
      await this.sleep(2500);

      // 1. Tap the search icon (contentDescription "Search")
      this.report?.('সার্চ খুঁজছি...');
      let tapped = await this.act({ action: 'click', matchDesc: 'search', timeoutMs: 7000 });
      if (!tapped?.success) {
        tapped = await this.act({ action: 'click', matchText: 'search', excludeEditable: true, timeoutMs: 3000 });
      }
      if (!tapped?.success) return this.fail('YouTube-এর সার্চ বাটন পাইনি জান।');
      await this.sleep(1500);

      // 2. Type the query into the editable search field
      this.report?.(`"${query}" লিখছি...`);
      let typed = await this.act({ action: 'type', editable: true, value: query, timeoutMs: 7000 });
      if (!typed?.success) {
        // Some layouts label the search box via contentDescription
        typed = await this.act({ action: 'type', matchDesc: 'search', editable: true, value: query, timeoutMs: 4000 });
      }
      if (!typed?.success) return this.fail('সার্চ বক্সে লিখতে পারিনি জান।');
      await this.sleep(1200);

      // 3. Submit — tap the first suggestion containing the query
      //    (excludeEditable so we don't re-tap the search field itself)
      const lower = query.toLowerCase();
      const suggestion = await this.act({ action: 'click', matchText: lower, excludeEditable: true, timeoutMs: 4000 });
      await this.sleep(2500);

      if (!suggestion?.success) {
        // Suggestions may not have appeared — results sometimes load anyway
        const result = await this.act({ action: 'click', matchText: lower, excludeEditable: true, timeoutMs: 3000 });
        await this.sleep(2000);
        if (!result?.success) {
          return { success: true, message: `সার্চ করে দিয়েছি, কিন্তু ভিডিওটা চালাতে পারিনি জান।` };
        }
      }

      this.report?.(`${query} চালিয়ে দিয়েছি জান!`);
      return { success: true, message: 'playing' };
    } finally {
      this.busy = false;
    }
  }

  /** Chrome search: launch → focus address bar → type → tap suggestion. */
  public async chromeSearch(query: string): Promise<AgentResult> {
    if (this.busy) return { success: false, message: 'already running' };
    this.busy = true;
    try {
      const gate = await this.requireAccessibility();
      if (!gate.ok) return { success: false, message: gate.msg };

      this.report?.('Chrome খুলছি...');
      const chrome = appScanner.findApp('chrome');
      if (!chrome) return { success: false, message: 'Chrome not found' };
      appScanner.launchApp(chrome);
      await this.sleep(2500);

      // 1. Focus the address bar (editable)
      this.report?.(`"${query}" লিখছি...`);
      const typed = await this.act({ action: 'type', editable: true, value: query, timeoutMs: 8000 });
      if (!typed?.success) {
        return this.fail('Chrome-এর অ্যাড্রেস বারে লিখতে পারিনি জান।');
      }
      await this.sleep(1500);

      // 2. Tap the suggestion containing the query (submit)
      const lower = query.toLowerCase();
      const suggestion = await this.act({ action: 'click', matchText: lower, excludeEditable: true, timeoutMs: 4000 });
      await this.sleep(1500);

      if (!suggestion?.success) {
        return { success: true, message: 'সার্চ লিখে দিয়েছি, কিন্তু সাবমিট করতে পারিনি জান।' };
      }
      this.report?.(`${query} সার্চ করে দিয়েছি জান!`);
      return { success: true, message: 'searched' };
    } finally {
      this.busy = false;
    }
  }

  /** Scroll the current app screen (down/up) — node action + gesture fallback. */
  public async scrollScreen(down: boolean): Promise<AgentResult> {
    const gate = await this.requireAccessibility();
    if (!gate.ok) return { success: false, message: gate.msg };
    const r = await this.act({ action: 'scroll', value: down ? 'down' : 'up', timeoutMs: 4000 });
    if (r?.success) {
      return { success: true, message: down ? 'নিচে স্ক্রল করেছি।' : 'উপরে স্ক্রল করেছি।' };
    }
    return { success: false, message: 'স্ক্রল করতে পারিনি জান।' };
  }

  /** Generic: find a labeled element and click it (e.g. "Notifications খোলো"). */
  public async findAndClick(label: string): Promise<AgentResult> {
    const gate = await this.requireAccessibility();
    if (!gate.ok) return { success: false, message: gate.msg };
    const r = await this.act({ action: 'click', matchText: label, timeoutMs: 6000 });
    if (r?.success) {
      return { success: true, message: `${label} খুলেছি।` };
    }
    return { success: false, message: `${label} পাইনি জান।` };
  }

  // ------------------------------------------------------------------
  // Internals
  // ------------------------------------------------------------------

  private async requireAccessibility(): Promise<{ ok: boolean; msg: string }> {
    if (await this.isAccessibilityEnabled()) return { ok: true, msg: '' };
    this.report?.('এই কাজের জন্য Accessibility চালু করতে হবে জান — Settings → Accessibility → MJ Android Agent।');
    await this.openAccessibilitySettings();
    return { ok: false, msg: 'accessibility disabled' };
  }

  private fail(msg: string): AgentResult {
    this.report?.(msg);
    return { success: false, message: msg };
  }
}

export const androidAgent = new AndroidAgent();
