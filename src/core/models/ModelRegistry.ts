/**
 * ModelRegistry
 * Central registry for AI models with fallback and active provider switching.
 */

import { AIModelProvider, GeminiModelProvider } from './AIModelProvider';

class ModelRegistry {
  private providers: Map<string, AIModelProvider> = new Map();
  private activeProviderName = 'Gemini';

  constructor() {
    const defaultGemini = new GeminiModelProvider();
    this.providers.set(defaultGemini.name, defaultGemini);
  }

  public register(provider: AIModelProvider): void {
    this.providers.set(provider.name, provider);
  }

  public setActiveProvider(name: string): void {
    if (!this.providers.has(name)) {
      throw new Error(`Model provider "${name}" is not registered.`);
    }
    this.activeProviderName = name;
  }

  public getActive(): AIModelProvider {
    const p = this.providers.get(this.activeProviderName);
    if (!p) {
      const fallback = new GeminiModelProvider();
      this.providers.set(fallback.name, fallback);
      return fallback;
    }
    return p;
  }
}

export const modelRegistry = new ModelRegistry();
