/**
 * AI Model Provider Abstraction
 * PRD Section 47: Decoupled model interface ensuring the Brain logic
 * is independent of specific underlying LLM providers.
 */

export interface ModelRequestOptions {
  model?: string;
  temperature?: number;
  maxTokens?: number;
  enableThinking?: boolean;
  systemPrompt?: string;
  timeoutMs?: number;
}

export interface ModelResponse {
  text: string;
  modelUsed: string;
  latencyMs: number;
  groundingSources?: Array<{ title?: string; url?: string }>;
}

export interface AIModelProvider {
  name: string;
  generateText(prompt: string, options?: ModelRequestOptions): Promise<ModelResponse>;
  generateJSON<T>(prompt: string, options?: ModelRequestOptions): Promise<T>;
}

/**
 * Gemini Provider implementation communicating with server-side endpoint
 */
export class GeminiModelProvider implements AIModelProvider {
  public name = 'Gemini';

  public async generateText(prompt: string, options?: ModelRequestOptions): Promise<ModelResponse> {
    const start = Date.now();
    const timeout = options?.timeoutMs || 30000;

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);

    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        signal: controller.signal,
        body: JSON.stringify({
          prompt,
          model: options?.model || 'gemini-3.1-flash-lite',
          enableThinking: options?.enableThinking || false,
          systemInstruction: options?.systemPrompt,
        }),
      });

      if (!res.ok) {
        const errJson = await res.json().catch(() => ({}));
        throw new Error(errJson.error || `HTTP ${res.status}`);
      }

      const data = await res.json();
      const latencyMs = Date.now() - start;

      return {
        text: data.text || '',
        modelUsed: options?.model || 'gemini-3.1-flash-lite',
        latencyMs,
        groundingSources: data.sources,
      };
    } finally {
      clearTimeout(timer);
    }
  }

  public async generateJSON<T>(prompt: string, options?: ModelRequestOptions): Promise<T> {
    const jsonPrompt = `${prompt}\n\nIMPORTANT: Respond ONLY with valid JSON inside a single codeblock \`\`\`json ... \`\`\` or raw JSON without commentary.`;
    const response = await this.generateText(jsonPrompt, options);

    let raw = response.text.trim();
    // Strip markdown code block if present
    const match = raw.match(/```(?:json)?\s*([\s\S]*?)\s*```/);
    if (match && match[1]) {
      raw = match[1].trim();
    }

    try {
      return JSON.parse(raw) as T;
    } catch (err) {
      throw new Error(`Failed to parse JSON model output: ${(err as Error).message}\nRaw output: ${raw.slice(0, 200)}`);
    }
  }
}
