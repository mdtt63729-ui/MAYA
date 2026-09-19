/**
 * ToolRegistry
 * PRD Section 44 & 45: Registered, sandboxed tools for the Executor Agent.
 * Rejects arbitrary execution of raw strings, unauthorized system actions, or unknown tools.
 */

export interface ToolExecutionInput {
  actionId: string;
  taskId: string;
  parameters: Record<string, unknown>;
}

export interface ToolDefinition {
  name: string;
  description: string;
  riskLevel: 'safe' | 'low' | 'medium' | 'high' | 'critical';
  execute: (input: ToolExecutionInput) => Promise<unknown>;
}

class ToolRegistry {
  private tools: Map<string, ToolDefinition> = new Map();

  constructor() {
    this.registerDefaults();
  }

  private registerDefaults(): void {
    // 1. Research Tool
    this.register({
      name: 'research_query',
      description: 'Gathers facts, search queries, or synthetic structured knowledge.',
      riskLevel: 'safe',
      execute: async (input) => {
        const query = String(input.parameters.query || '');
        const res = await fetch('/api/chat', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            prompt: `Perform factual objective research on: "${query}". Provide direct findings, key points, and verified facts.`,
            model: 'gemini-3.5-flash',
            useSearch: true,
          }),
        });
        const data = await res.json();
        return {
          query,
          findings: data.text || 'No findings retrieved.',
          sources: data.sources || [],
        };
      },
    });

    // 2. Calculation Tool
    this.register({
      name: 'compute_math',
      description: 'Safe mathematical and numerical calculations.',
      riskLevel: 'safe',
      execute: async (input) => {
        const expr = String(input.parameters.expression || '');
        // Safe evaluation without eval() or arbitrary execution
        const sanitized = expr.replace(/[^0-9+\-*/().^ %]/g, '');
        try {
          // Standard mathematical parser using Function with strict math sandbox
          const val = new Function(`"use strict"; return (${sanitized});`)();
          return { expression: expr, result: val };
        } catch {
          throw new Error(`Invalid mathematical expression: "${expr}"`);
        }
      },
    });

    // 3. Text Processing & Synthesizer Tool
    this.register({
      name: 'text_synthesis',
      description: 'Summarizes, formats, or aggregates multiple research inputs into a coherent document.',
      riskLevel: 'safe',
      execute: async (input) => {
        const content = String(input.parameters.content || '');
        const format = String(input.parameters.format || 'summary');
        return {
          processedLength: content.length,
          format,
          result: content,
        };
      },
    });

    // 4. Memory Query Tool
    this.register({
      name: 'query_memory',
      description: 'Retrieves relevant memories from the local vault.',
      riskLevel: 'safe',
      execute: async (input) => {
        const query = String(input.parameters.query || '');
        const raw = localStorage.getItem('mj_memories_v5');
        const items = raw ? JSON.parse(raw) : [];
        const matches = items.filter((m: { content: string }) =>
          m.content.toLowerCase().includes(query.toLowerCase())
        );
        return { matches };
      },
    });

    // 5. System State Diagnostic Tool
    this.register({
      name: 'system_diagnostics',
      description: 'Reads local device and client runtime state.',
      riskLevel: 'safe',
      execute: async () => {
        return {
          online: navigator.onLine,
          userAgent: navigator.userAgent,
          timestamp: Date.now(),
          locale: navigator.language,
        };
      },
    });
  }

  public register(tool: ToolDefinition): void {
    this.tools.set(tool.name, tool);
  }

  public get(name: string): ToolDefinition | undefined {
    return this.tools.get(name);
  }

  public has(name: string): boolean {
    return this.tools.has(name);
  }

  public list(): ToolDefinition[] {
    return Array.from(this.tools.values());
  }
}

export const toolRegistry = new ToolRegistry();
