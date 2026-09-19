/**
 * MJ Long-Term Memory Vault Modal
 * PRD Sections 54-61: Persistent user memories, preferences, and workflows.
 */

import React, { useState } from 'react';
import { X, Brain, Trash2, Plus, Search, Download, Tag } from 'lucide-react';
import { MemoryItem } from '../../types';
import { memoryService } from '../../services/memoryService';

interface MemoryModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const MemoryModal: React.FC<MemoryModalProps> = ({ isOpen, onClose }) => {
  const [memories, setMemories] = useState<MemoryItem[]>(() => memoryService.getAll());
  const [searchQuery, setSearchQuery] = useState('');
  const [newContent, setNewContent] = useState('');
  const [newCategory, setNewCategory] = useState<MemoryItem['category']>('preference');
  const [newImportance, setNewImportance] = useState<MemoryItem['importance']>('MEDIUM');
  const [showAddForm, setShowAddForm] = useState(false);

  if (!isOpen) return null;

  const refreshMemories = () => {
    setMemories(memoryService.search(searchQuery));
  };

  const handleSearch = (q: string) => {
    setSearchQuery(q);
    setMemories(memoryService.search(q));
  };

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newContent.trim()) return;

    memoryService.add(newContent.trim(), newCategory, newImportance);
    setNewContent('');
    setShowAddForm(false);
    refreshMemories();
  };

  const handleDelete = (id: string) => {
    memoryService.remove(id);
    refreshMemories();
  };

  const handleExport = () => {
    const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(memoryService.exportJSON());
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute('href', dataStr);
    downloadAnchor.setAttribute('download', 'mj_memories_vault.json');
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  const getImportanceBadge = (imp: MemoryItem['importance']) => {
    const colors = {
      CRITICAL: 'bg-rose-950/70 border-rose-700/60 text-rose-300',
      HIGH: 'bg-amber-950/70 border-amber-700/60 text-amber-300',
      MEDIUM: 'bg-cyan-950/70 border-cyan-700/60 text-cyan-300',
      LOW: 'bg-slate-900 border-slate-700 text-slate-400',
    }[imp];

    return (
      <span className={`text-[9px] font-mono uppercase px-1.5 py-0.5 rounded border ${colors}`}>
        {imp}
      </span>
    );
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md select-none animate-fade-in">
      <div className="w-full max-w-lg rounded-3xl bg-[#0e131d] border border-slate-700/80 p-5 shadow-2xl space-y-4 max-h-[85vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center space-x-2.5">
            <div className="w-9 h-9 rounded-xl bg-cyan-950/70 border border-cyan-700/40 flex items-center justify-center text-cyan-400">
              <Brain className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-slate-100">
                Long-Term Memory Vault
              </h3>
              <p className="text-[11px] text-slate-400">
                Persistent context, preferences, and personal knowledge
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-slate-800"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Search & Actions Bar */}
        <div className="flex items-center space-x-2">
          <div className="relative flex-1">
            <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-3" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => handleSearch(e.target.value)}
              placeholder="Search memories..."
              className="w-full text-xs pl-8 pr-3 py-2 rounded-xl bg-slate-950 border border-slate-800 text-slate-200 focus:outline-none focus:border-cyan-500"
            />
          </div>

          <button
            onClick={() => setShowAddForm(!showAddForm)}
            className="p-2 rounded-xl bg-cyan-950/80 hover:bg-cyan-900 border border-cyan-700/50 text-cyan-300 text-xs flex items-center space-x-1"
            title="Add Memory"
          >
            <Plus className="w-4 h-4" />
          </button>

          <button
            onClick={handleExport}
            className="p-2 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-700 text-slate-300 text-xs flex items-center space-x-1"
            title="Export JSON"
          >
            <Download className="w-4 h-4" />
          </button>
        </div>

        {/* Add Memory Form */}
        {showAddForm && (
          <form
            onSubmit={handleAdd}
            className="p-3 rounded-2xl bg-slate-950/80 border border-slate-800 space-y-2.5 animate-slide-down"
          >
            <textarea
              value={newContent}
              onChange={(e) => setNewContent(e.target.value)}
              placeholder="e.g. Always summarize YouTube videos in bullet points..."
              className="w-full text-xs p-2 rounded-xl bg-slate-900 border border-slate-700 text-slate-200 focus:outline-none focus:border-cyan-500 resize-none h-16"
            />

            <div className="flex items-center justify-between text-xs">
              <div className="flex space-x-2">
                <select
                  value={newCategory}
                  onChange={(e) => setNewCategory(e.target.value as MemoryItem['category'])}
                  className="bg-slate-900 border border-slate-700 rounded-lg px-2 py-1 text-[11px] text-slate-300"
                >
                  <option value="preference">Preference</option>
                  <option value="workflow">Workflow</option>
                  <option value="person">Person</option>
                  <option value="fact">Fact</option>
                </select>

                <select
                  value={newImportance}
                  onChange={(e) => setNewImportance(e.target.value as MemoryItem['importance'])}
                  className="bg-slate-900 border border-slate-700 rounded-lg px-2 py-1 text-[11px] text-slate-300"
                >
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                  <option value="CRITICAL">Critical</option>
                </select>
              </div>

              <button
                type="submit"
                className="px-3 py-1 rounded-lg bg-cyan-400 text-slate-950 font-semibold text-xs active:scale-95 transition-all"
              >
                Save Memory
              </button>
            </div>
          </form>
        )}

        {/* Memories List */}
        <div className="flex-1 overflow-y-auto space-y-2 pr-1">
          {memories.length === 0 ? (
            <div className="py-8 text-center text-xs text-slate-500">
              No memories found. Tell MJ “Remember that...” or tap + to record.
            </div>
          ) : (
            memories.map((m) => (
              <div
                key={m.id}
                className="p-3 rounded-2xl bg-slate-900/60 border border-slate-800/80 space-y-2"
              >
                <div className="flex items-start justify-between">
                  <p className="text-xs text-slate-200 leading-relaxed select-text flex-1 mr-2">
                    {m.content}
                  </p>
                  <button
                    onClick={() => handleDelete(m.id)}
                    className="p-1 text-slate-500 hover:text-rose-400 transition-colors"
                    title="Delete Memory"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>

                <div className="flex items-center justify-between text-[10px] text-slate-500 pt-1 border-t border-slate-800/50">
                  <div className="flex items-center space-x-1.5">
                    <span className="capitalize text-slate-400 flex items-center">
                      <Tag className="w-2.5 h-2.5 mr-1" />
                      {m.category}
                    </span>
                    {getImportanceBadge(m.importance)}
                  </div>
                  <span>{new Date(m.createdAt).toLocaleDateString()}</span>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
