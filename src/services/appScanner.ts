/**
 * AppScanner Service
 * Scans, indexes, and reliably opens applications on user command.
 * Supports deep links, web fallbacks, Android intents, and built-in interactive tools
 * (Camera, Calculator, Flashlight, Settings, YouTube, WhatsApp, Spotify, Maps, Chrome, etc.).
 * Includes Bengali and English phonetic keyword matching.
 * NATIVE MODE: on Android (Capacitor APK) it indexes EVERY app actually
 * installed on the phone — with real app logos — via the MJNative plugin.
 */

import { Capacitor } from '@capacitor/core';

export interface AppDefinition {
  id: string;
  name: string;
  aliases: string[];
  category: 'system' | 'social' | 'media' | 'utility';
  url: string;
  intentUri?: string;
  isBuiltInTool?: boolean;
  icon: string;
  packageName?: string;
}

export class AppScanner {
  private installedApps: Map<string, AppDefinition> = new Map();
  private isScanned = false;
  private nativeScanPromise: Promise<void> | null = null;
  private lastNativeLaunch: { pkg: string; t: number } | null = null;

  constructor() {
    this.scanInstalledApps();
  }

  /**
   * Scans and indexes all device apps and web integrations
   */
  public scanInstalledApps(): AppDefinition[] {
    const registry = this.getRegistry();

    this.installedApps.clear();
    registry.forEach((app) => {
      this.installedApps.set(app.id, app);
      // Index aliases
      app.aliases.forEach((alias) => {
        this.installedApps.set(alias.toLowerCase(), app);
      });
    });

    this.isScanned = true;
    console.log(`[AppScanner] Indexed ${registry.length} installed apps & system tools`);

    // NATIVE: also index every app actually installed on the phone
    this.refreshNativeApps();
    return registry;
  }

  /**
   * The static fallback registry (used on the web, and as a starting set on
   * Android until the real installed-apps scan completes).
   */
  private getRegistry(): AppDefinition[] {
    const registry: AppDefinition[] = [
      {
        id: 'youtube',
        name: 'YouTube',
        aliases: ['youtube', 'yt', 'ইউটিউব', 'ভিডিও', 'video', 'গান শুনব', 'play video'],
        category: 'media',
        url: 'https://www.youtube.com',
        intentUri: 'vnd.youtube://',
        icon: 'Youtube',
      },
      {
        id: 'whatsapp',
        name: 'WhatsApp',
        aliases: ['whatsapp', 'wp', 'হোয়াটসঅ্যাপ', 'chat', 'মেসেজ', 'message', 'msg'],
        category: 'social',
        url: 'https://web.whatsapp.com',
        intentUri: 'whatsapp://',
        icon: 'MessageCircle',
      },
      {
        id: 'camera',
        name: 'Camera',
        aliases: ['camera', 'cam', 'ক্যামেরা', 'ছবি তোল', 'photo', 'picture', 'selfie'],
        category: 'system',
        url: '#camera',
        isBuiltInTool: true,
        icon: 'Camera',
      },
      {
        id: 'spotify',
        name: 'Spotify',
        aliases: ['spotify', 'গান', 'music', 'songs', 'স্পটিফাই', 'audio'],
        category: 'media',
        url: 'https://open.spotify.com',
        intentUri: 'spotify://',
        icon: 'Music',
      },
      {
        id: 'calculator',
        name: 'Calculator',
        aliases: ['calculator', 'calc', 'ক্যালকুলেটর', 'হিসাব', 'math', 'calculate'],
        category: 'utility',
        url: '#calculator',
        isBuiltInTool: true,
        icon: 'Calculator',
      },
      {
        id: 'settings',
        name: 'Settings',
        aliases: ['settings', 'setting', 'সেটিংস', 'config', 'preference'],
        category: 'system',
        url: '#settings',
        isBuiltInTool: true,
        icon: 'Settings',
      },
      {
        id: 'maps',
        name: 'Google Maps',
        aliases: ['maps', 'map', 'google maps', 'ম্যাপস', 'রাস্তা', 'location', 'navigation', 'দিক'],
        category: 'utility',
        url: 'https://maps.google.com',
        intentUri: 'geo:0,0?q=',
        icon: 'MapPin',
      },
      {
        id: 'chrome',
        name: 'Chrome Browser',
        aliases: ['chrome', 'browser', 'ক্রোম', 'google', 'search', 'ইন্টারনেট', 'web'],
        category: 'utility',
        url: 'https://www.google.com',
        icon: 'Globe',
      },
      {
        id: 'gallery',
        name: 'Gallery',
        aliases: ['gallery', 'photos', 'গ্যালারি', 'ছবি', 'albums', 'pictures'],
        category: 'system',
        url: 'https://photos.google.com',
        icon: 'Image',
      },
      {
        id: 'telegram',
        name: 'Telegram',
        aliases: ['telegram', 'টিলিগ্রাম', 'টেলিগ্রাম', 'tg'],
        category: 'social',
        url: 'https://web.telegram.org',
        intentUri: 'tg://',
        icon: 'Send',
      },
      {
        id: 'instagram',
        name: 'Instagram',
        aliases: ['instagram', 'insta', 'ইন্সটাগ্রাম', 'reels', 'রিলস'],
        category: 'social',
        url: 'https://www.instagram.com',
        intentUri: 'instagram://',
        icon: 'Instagram',
      },
      {
        id: 'twitter',
        name: 'X (Twitter)',
        aliases: ['twitter', 'x', 'টুইটার', 'tweet'],
        category: 'social',
        url: 'https://x.com',
        icon: 'Twitter',
      },
      {
        id: 'gmail',
        name: 'Gmail',
        aliases: ['gmail', 'mail', 'email', 'মেইল', 'ইমেইল'],
        category: 'utility',
        url: 'https://mail.google.com',
        intentUri: 'mailto:',
        icon: 'Mail',
      },
      {
        id: 'flashlight',
        name: 'Flashlight',
        aliases: ['flashlight', 'torch', 'ফ্ল্যাশলাইট', 'টর্চ', 'আলো', 'light'],
        category: 'system',
        url: '#flashlight',
        isBuiltInTool: true,
        icon: 'Zap',
      },
    ];
    return registry;
  }

  /**
   * NATIVE ANDROID: index every app actually installed on the phone via
   * the MJNative plugin (real names + real app icons). Called automatically
   * on scan; safe to call again any time (e.g. when Settings opens).
   */
  public async refreshNativeApps(): Promise<void> {
    if (!Capacitor.isNativePlatform()) return;
    if (this.nativeScanPromise) return;
    this.nativeScanPromise = (async () => {
      try {
        const MJNative = (Capacitor as any).Plugins?.MJNative;
        if (!MJNative) return;

        const res: any = await MJNative.getInstalledApps();
        const nativeList: any[] = Array.isArray(res?.apps) ? res.apps : [];
        if (nativeList.length === 0) return;

        // Rebuild the index: built-in tools + EVERY real installed app
        const registry = this.getRegistry();
        this.installedApps.clear();

        // Keep the built-in interactive tools (flashlight, camera, calculator, settings)
        registry
          .filter((app) => app.isBuiltInTool)
          .forEach((app) => {
            this.installedApps.set(app.id, app);
            app.aliases.forEach((alias) => this.installedApps.set(alias.toLowerCase(), app));
          });

        const STOP_WORDS = ['com', 'android', 'google', 'inc', 'app', 'apps', 'mobile', 'lite'];
        for (const n of nativeList) {
          const name: string = (n.name || n.packageName || '').toString();
          const pkg: string = (n.packageName || '').toString();
          if (!name || !pkg) continue;

          const aliases = new Set<string>([name.toLowerCase()]);
          // Useful segments of the package name (e.g. 'whatsapp' from com.whatsapp)
          pkg
            .split('.')
            .filter((seg: string) => seg.length > 3 && !STOP_WORDS.includes(seg))
            .forEach((seg: string) => aliases.add(seg.toLowerCase()));
          // Individual words of the display name
          name
            .toLowerCase()
            .split(/\s+/)
            .filter((w: string) => w.length > 2)
            .forEach((w: string) => aliases.add(w));

          const app: AppDefinition = {
            id: pkg,
            name,
            aliases: Array.from(aliases),
            category: 'utility',
            url: `https://play.google.com/store/apps/details?id=${pkg}`,
            icon: n.icon || 'AppWindow',
            packageName: pkg,
          };
          this.installedApps.set(pkg, app);
          app.aliases.forEach((alias) => {
            if (!this.installedApps.has(alias)) {
              this.installedApps.set(alias, app);
            }
          });
        }

        this.isScanned = true;
        console.log(`[AppScanner] Native scan complete — ${nativeList.length} installed apps indexed with icons`);
      } catch (e) {
        console.warn('[AppScanner] Native app scan failed:', e);
      } finally {
        this.nativeScanPromise = null;
      }
    })();
    return this.nativeScanPromise;
  }

  /**
   * Find app by name or phonetic keyword
   */
  public findApp(query: string): AppDefinition | null {
    if (!query) return null;
    let clean = query.trim().toLowerCase();

    // 0. Bengali app names → canonical id (user speaks Bengali — must match!)
    clean = AppScanner.BENGALI_ALIASES[clean] || clean;

    // 1. Direct match
    if (this.installedApps.has(clean)) {
      return this.installedApps.get(clean)!;
    }

    // 2. Partial substring search
    for (const [key, app] of this.installedApps.entries()) {
      if (clean.includes(key) || key.includes(clean)) {
        return app;
      }
    }

    // 2b. Bengali substring (e.g. "ইউটিউবটা" contains "ইউটিউব")
    for (const [bKey, id] of Object.entries(AppScanner.BENGALI_ALIASES)) {
      if (clean.includes(bKey)) {
        const app = this.installedApps.get(id);
        if (app) return app;
      }
    }

    // 3. Fuzzy word-overlap match (e.g. "you tube", "the youtube app", "whats app")
    const qWords = clean
      .replace(/[^a-z0-9\u0980-\u09FF]+/g, ' ')
      .split(' ')
      .filter((w) => w.length > 2);
    let best: { app: AppDefinition; score: number } | null = null;
    for (const [key, app] of this.installedApps.entries()) {
      const kWords = key
        .replace(/[^a-z0-9\u0980-\u09FF]+/g, ' ')
        .split(' ')
        .filter((w) => w.length > 2);
      let score = 0;
      for (const qw of qWords) {
        for (const kw of kWords) {
          if (qw === kw) score += 2;
          else if (qw.length > 3 && kw.length > 2 && (qw.startsWith(kw) || kw.startsWith(qw))) score += 1;
        }
      }
      if (score > 0 && (!best || score > best.score)) best = { app, score };
    }
    if (best && best.score >= 2) return best.app;

    return null;
  }

  /** Bengali-spoken app names → canonical registry ids. */
  private static readonly BENGALI_ALIASES: Record<string, string> = {
    'ইউটিউব': 'youtube',
    'ইউটিউব মিউজিক': 'youtube',
    'হোয়াটসঅ্যাপ': 'whatsapp',
    'হোয়াটসএপ': 'whatsapp',
    'হোয়াটস্যাপ': 'whatsapp',
    'ফেসবুক': 'facebook',
    'মেসেঞ্জার': 'messenger',
    'ইনস্টাগ্রাম': 'instagram',
    'ক্যামেরা': 'camera',
    'ক্রোম': 'chrome',
    'গুগল': 'google',
    'জিমেইল': 'gmail',
    'ম্যাপ': 'maps',
    'গুগল ম্যাপ': 'maps',
    'টেলিগ্রাম': 'telegram',
    'স্ন্যাপচ্যাট': 'snapchat',
    'স্পটিফাই': 'spotify',
    'গানা': 'gaana',
    'গ্যালারি': 'gallery',
    'ফটো': 'gallery',
    'ছবি': 'gallery',
    'ঘড়ি': 'clock',
    'ক্যালকুলেটর': 'calculator',
    'ক্যালেন্ডার': 'calendar',
    'প্লে স্টোর': 'playstore',
    'প্লেস্টোর': 'playstore',
    'সেটিংস': 'settings',
    'ফোন': 'phone',
    'ডায়ালার': 'phone',
    'মেসেজ': 'messages',
    'এসএমএস': 'messages',
  };

  /**
   * Launch application — on Android it opens the REAL installed app,
   * with popup blocker protection and native intent fallback on the web.
   */
  public launchApp(app: AppDefinition): { success: boolean; actionType: string; url: string } {
    console.log(`[AppScanner] Launching ${app.name}${app.packageName ? ` (${app.packageName})` : ` (${app.url})`}`);

    // Handle Built-in Tools
    if (app.isBuiltInTool) {
      return { success: true, actionType: 'builtin', url: app.url };
    }

    // NATIVE ANDROID: launch the actual installed app via MJNative
    if (app.packageName) {
      // Dedupe guard — if this app was launched moments ago (e.g. the local
      // fast path already fired and the model then proposed the same tool
      // call), don't launch it a second time.
      if (
        this.lastNativeLaunch &&
        this.lastNativeLaunch.pkg === app.packageName &&
        Date.now() - this.lastNativeLaunch.t < 4000
      ) {
        return { success: true, actionType: 'native_app_launch_dedupe', url: app.url };
      }
      try {
        const MJNative = (Capacitor as any).Plugins?.MJNative;
        if (Capacitor.isNativePlatform() && MJNative) {
          MJNative.launchApp({ packageName: app.packageName });
          this.lastNativeLaunch = { pkg: app.packageName, t: Date.now() };
          return { success: true, actionType: 'native_app_launch', url: app.url };
        }
      } catch (err) {
        console.warn('[AppScanner] Native launch failed:', err);
      }
    }

    // Try opening external link
    this.openExternalUrl(app.url);
    return { success: true, actionType: 'window_open', url: app.url };
  }

  /**
   * Open a URL externally — natively on Android (system browser / app),
   * or a new tab on the web.
   */
  public openExternalUrl(url: string): boolean {
    if (Capacitor.isNativePlatform()) {
      try {
        const MJNative = (Capacitor as any).Plugins?.MJNative;
        if (MJNative) {
          MJNative.openUrl({ url });
          return true;
        }
      } catch (err) {
        console.warn('[AppScanner] Native openUrl failed:', err);
      }
    }
    try {
      const win = window.open(url, '_blank', 'noopener,noreferrer');
      if (win) {
        win.focus();
        return true;
      }
    } catch (err) {
      console.warn('[AppScanner] window.open failed:', err);
    }
    return false;
  }

  public getAllApps(): AppDefinition[] {
    const seen = new Set<string>();
    const list: AppDefinition[] = [];
    for (const app of this.installedApps.values()) {
      if (!seen.has(app.id)) {
        seen.add(app.id);
        list.push(app);
      }
    }
    return list;
  }
}

export const appScanner = new AppScanner();
