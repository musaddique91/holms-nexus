import { auth } from '@/lib/auth'
import { create } from 'zustand'
import type { QueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { streamChat } from '@/lib/api'
import type { AgentRunPart, ConversationDetail, ConversationSummary, Part, ToolPart, WorkflowPart } from '@/lib/types'

export interface StreamState {
  userText: string
  regenerate: boolean
  parts: Part[]
  status: 'streaming' | 'finishing' | 'error'
  error?: string
  model?: string
  provider?: string
  toolCount?: number
  startedAt: number
  controller: AbortController
}

interface Draft {
  serverIds: string[] | null // null = "all connected servers"
  providerId: string | null
  model: string | null
}

interface ChatState {
  streams: Record<string, StreamState>
  draft: Draft
  setDraft: (d: Partial<Draft>) => void
  patchStream: (id: string, fn: (s: StreamState) => Partial<StreamState>) => void
  clearStream: (id: string) => void
}

export const useChat = create<ChatState>((set) => ({
  streams: {},
  draft: { serverIds: null, providerId: null, model: null },
  setDraft: (d) => set((s) => ({ draft: { ...s.draft, ...d } })),
  patchStream: (id, fn) =>
    set((s) => (s.streams[id] ? { streams: { ...s.streams, [id]: { ...s.streams[id], ...fn(s.streams[id]) } } } : s)),
  clearStream: (id) =>
    set((s) => {
      const next = { ...s.streams }
      delete next[id]
      return { streams: next }
    }),
}))

/** Applies `fn` to the (single, leading) workflow part, creating it on first use. */
function withWorkflow(parts: Part[], fn: (w: WorkflowPart) => WorkflowPart): Part[] {
  const idx = parts.findIndex((p) => p.type === 'workflow')
  if (idx < 0) return [fn({ type: 'workflow', agents: [] }), ...parts]
  return parts.map((p, i) => (i === idx ? fn(p as WorkflowPart) : p))
}

const updateAgent = (w: WorkflowPart, id: string, fn: (a: AgentRunPart) => AgentRunPart): WorkflowPart => ({
  ...w,
  agents: w.agents.map((a) => (a.id === id ? fn(a) : a)),
})

function appendText(parts: Part[], text: string): Part[] {
  const last = parts[parts.length - 1]
  if (last && last.type === 'text') {
    return [...parts.slice(0, -1), { ...last, text: last.text + text }]
  }
  return [...parts, { type: 'text', text }]
}

export async function sendMessage(
  qc: QueryClient,
  conversationId: string,
  body: { content: string; workspaceId?: string | null; providerId?: string | null; model?: string | null; serverIds?: string[]; regenerate?: boolean },
) {
  const { patchStream, clearStream } = useChat.getState()
  const controller = new AbortController()
  useChat.setState((s) => ({
    streams: {
      ...s.streams,
      [conversationId]: {
        userText: body.content,
        regenerate: !!body.regenerate,
        parts: [],
        status: 'streaming',
        startedAt: Date.now(),
        controller,
      },
    },
  }))
  // The user message is persisted server-side right away; refresh so it shows in history.
  const settle = async () => {
    await Promise.all([
      qc.refetchQueries({ queryKey: ['conversation', conversationId] }),
      qc.invalidateQueries({ queryKey: ['conversations'] }),
    ])
    clearStream(conversationId)
    // Titles are generated in parallel and can land after the answer; pick them up shortly after.
    for (const delay of [3000, 10000]) {
      setTimeout(() => {
        qc.invalidateQueries({ queryKey: ['conversations'] })
        qc.invalidateQueries({ queryKey: ['conversation', conversationId] })
      }, delay)
    }
  }

  try {
    await streamChat(
      conversationId,
      body,
      {
        onEvent: (event, data) => {
          switch (event) {
            case 'meta':
              patchStream(conversationId, () => ({ model: data.model, provider: data.provider, toolCount: data.toolCount }))
              break
            case 'token':
              patchStream(conversationId, (s) => ({ parts: appendText(s.parts, data.text) }))
              break
            case 'plan':
              patchStream(conversationId, (s) => ({ parts: withWorkflow(s.parts, (w) => ({ ...w, plan: data })) }))
              break
            case 'agent_start':
              patchStream(conversationId, (s) => ({
                parts: withWorkflow(s.parts, (w) => ({ ...w, agents: [...w.agents, { ...data, tools: data.tools ?? [] } as AgentRunPart] })),
              }))
              break
            case 'agent_end':
              patchStream(conversationId, (s) => ({
                parts: withWorkflow(s.parts, (w) =>
                  updateAgent(w, data.id, (a) => ({ ...a, status: data.status, durationMs: data.durationMs, summary: data.summary, output: data.output || a.output })),
                ),
              }))
              break
            case 'tool_start': {
              const { agentId, ...tool } = data
              patchStream(conversationId, (s) => ({
                parts: agentId
                  ? withWorkflow(s.parts, (w) => updateAgent(w, agentId, (a) => ({ ...a, tools: [...a.tools, tool as ToolPart] })))
                  : [...s.parts, tool as ToolPart],
              }))
              break
            }
            case 'tool_end': {
              const done = (p: ToolPart): ToolPart =>
                p.id === data.id ? { ...p, status: data.status, result: data.result, durationMs: data.durationMs } : p
              patchStream(conversationId, (s) => ({
                parts: data.agentId
                  ? withWorkflow(s.parts, (w) => updateAgent(w, data.agentId, (a) => ({ ...a, tools: a.tools.map(done) })))
                  : s.parts.map((p) => (p.type === 'tool' ? done(p) : p)),
              }))
              break
            }
            case 'artifact':
              patchStream(conversationId, (s) => ({ parts: [...s.parts, data] }))
              break
            case 'title':
              qc.setQueriesData<ConversationSummary[]>({ queryKey: ['conversations'] }, (old) =>
                old?.map((c) => (c.id === conversationId ? { ...c, title: data.title } : c)),
              )
              qc.setQueryData<ConversationDetail>(['conversation', conversationId], (old) =>
                old ? { ...old, conversation: { ...old.conversation, title: data.title } } : old,
              )
              break
            case 'error':
              patchStream(conversationId, () => ({ status: 'error', error: data.message }))
              toast.error('The model returned an error', { description: String(data.message).slice(0, 240) })
              break
            case 'done':
              patchStream(conversationId, () => ({ status: 'finishing' }))
              break
          }
        },
      },
      controller.signal,
    )
  } catch (err) {
    if ((err as Error).name !== 'AbortError') {
      toast.error('Could not send message', { description: (err as Error).message })
    }
  } finally {
    await settle()
  }
}

export async function stopStream(conversationId: string) {
  const s = useChat.getState().streams[conversationId]
  if (!s) return
  try {
    await fetch(`/api/conversations/${conversationId}/stop`, { method: 'POST', headers: auth.headers() })
  } finally {
    s.controller.abort()
  }
}
