/**
 * MJ Long-Term Memory Service
 * Local-first persistent memory management (PRD sections 54-61)
 * Stores user preferences, recurring tasks, people, and context.
 */

import { MemoryItem } from '../types';

const STORAGE_KEY = 'mj_assistant_memory_v5';

export const INITIAL_MEMORIES: MemoryItem[] = [
  {
    id: 'mem_1',
    content: 'User prefers concise, natural, conversational responses without robotic greetings.',
    category: 'preference',
    importance: 'HIGH',
    createdAt: Date.now() - 86400000 * 3,
    tags: ['communication', 'style'],
  },
  {
    id: 'mem_2',
    content: 'Primary languages: English, Bengali, and Hindi code-switching.',
    category: 'preference',
    importance: 'CRITICAL',
    createdAt: Date.now() - 86400000 * 2,
    tags: ['language', 'bilingual'],
  },
  {
    id: 'mem_3',
    content: 'Frequently uses YouTube and WhatsApp on Android.',
    category: 'workflow',
    importance: 'MEDIUM',
    createdAt: Date.now() - 86400000,
    tags: ['apps', 'android'],
  },
];

class MemoryService {
  private memories: MemoryItem[] = [];

  constructor() {
    this.load();
  }

  private load() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        this.memories = JSON.parse(raw);
      } else {
        this.memories = [...INITIAL_MEMORIES];
        this.save();
      }
    } catch {
      this.memories = [...INITIAL_MEMORIES];
    }
  }

  private save() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.memories));
    } catch (e) {
      console.warn('Failed saving memories to local storage:', e);
    }
  }

  public getAll(): MemoryItem[] {
    return [...this.memories].sort((a, b) => b.createdAt - a.createdAt);
  }

  public add(
    content: string,
    category: MemoryItem['category'] = 'fact',
    importance: MemoryItem['importance'] = 'MEDIUM',
    tags: string[] = []
  ): MemoryItem {
    const newItem: MemoryItem = {
      id: `mem_${Date.now()}_${Math.random().toString(36).substr(2, 5)}`,
      content: content.trim(),
      category,
      importance,
      createdAt: Date.now(),
      tags,
    };
    this.memories.unshift(newItem);
    this.save();
    return newItem;
  }

  public remove(id: string): boolean {
    const prevLen = this.memories.length;
    this.memories = this.memories.filter((m) => m.id !== id);
    if (this.memories.length !== prevLen) {
      this.save();
      return true;
    }
    return false;
  }

  public search(query: string): MemoryItem[] {
    const q = query.toLowerCase().trim();
    if (!q) return this.getAll();
    return this.memories.filter(
      (m) =>
        m.content.toLowerCase().includes(q) ||
        m.category.toLowerCase().includes(q) ||
        m.tags.some((t) => t.toLowerCase().includes(q))
    );
  }

  public clearAll() {
    this.memories = [];
    this.save();
  }

  public exportJSON(): string {
    return JSON.stringify(this.memories, null, 2);
  }
}

export const memoryService = new MemoryService();
