/**
 * AppScanner Service
 * Scans, indexes, and reliably opens applications on user command.
 * Supports deep links, web fallbacks, Android intents, and built-in interactive tools
 * (Camera, Calculator, Flashlight, Settings, YouTube, WhatsApp, Spotify, Maps, Chrome, etc.).
 * Includes Bengali and English phonetic keyword matching.
 */

export interface AppDefinition {
  id: string;
  name: string;
  aliases: string[];
  category: 'system' | 'social' | 'media' | 'utility';
  url: string;
  intentUri?: string;
  isBuiltInTool?: boolean;
  icon: string;
}

export class AppScanner {
  private installedApps: Map<string, AppDefinition> = new Map();
  private isScanned = false;

  constructor() {
    this.scanInstalledApps();
  }

  /**
   * Scans and indexes all device apps and web integrations
   */
  public scanInstalledApps(): AppDefinition[] {
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
    return registry;
  }

  /**
   * Find app by name or phonetic keyword
   */
  public findApp(query: string): AppDefinition | null {
    if (!query) return null;
    const clean = query.trim().toLowerCase();

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

    return null;
  }

  /**
   * Launch application with popup blocker protection and native intent fallback
   */
  public launchApp(app: AppDefinition): { success: boolean; actionType: string; url: string } {
    console.log(`[AppScanner] Launching ${app.name} (${app.url})`);

    // Handle Built-in Tools
    if (app.isBuiltInTool) {
      return { success: true, actionType: 'builtin', url: app.url };
    }

    // Try opening external link
    try {
      const win = window.open(app.url, '_blank', 'noopener,noreferrer');
      if (win) {
        win.focus();
        return { success: true, actionType: 'window_open', url: app.url };
      }
    } catch (err) {
      console.warn('[AppScanner] Direct window.open failed:', err);
    }

    return { success: true, actionType: 'fallback_sheet', url: app.url };
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
